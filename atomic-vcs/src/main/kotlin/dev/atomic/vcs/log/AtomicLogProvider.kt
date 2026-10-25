package dev.atomic.vcs.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.TimedVcsCommitImpl
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.impl.VcsRefImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcsUtil.VcsUtil
import dev.atomic.core.cli.AtomicCliException
import dev.atomic.core.format.ProvenanceFormatter
import dev.atomic.core.model.AtomicChangeDetail
import dev.atomic.core.model.AtomicChangeSummary
import dev.atomic.core.model.AtomicChangeType
import dev.atomic.vcs.AtomicContentRevision
import dev.atomic.vcs.AtomicRevisionNumber
import dev.atomic.vcs.AtomicVcs
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feeds Atomic's change graph (changes and their dependencies) and views (shown as branch labels)
 * into the IDE's Version Control | Log tab.
 */
class AtomicLogProvider(private val project: Project) : VcsLogProvider {
    private class Snapshot(
        val changes: List<AtomicChangeSummary>,
        val heads: Map<String, String>,
        val currentView: String?,
        val fetchedAt: Long,
    ) {
        val byHash: Map<String, AtomicChangeSummary> = changes.associateBy { it.hash }
    }

    private class Registration(val roots: Collection<VirtualFile>, val refresher: VcsLogRefresher)

    private val cache = ConcurrentHashMap<String, Snapshot>()
    private val registrations = CopyOnWriteArrayList<Registration>()
    private val refManager = AtomicLogRefManager()

    /** Drops cached data and asks the IDE to reload the log, e.g. after a record or unrecord. */
    fun refresh() {
        cache.clear()
        for (registration in registrations) {
            for (root in registration.roots) registration.refresher.refresh(root)
        }
    }

    override fun readFirstBlock(root: VirtualFile, requirements: VcsLogProvider.Requirements): VcsLogProvider.DetailedLogData {
        val snapshot = snapshot(root)
        val commits = snapshot.changes.take(requirements.commitCount).map { metadata(root, it) }
        val refs = refs(root, snapshot)
        return object : VcsLogProvider.DetailedLogData {
            override fun getCommits(): List<VcsCommitMetadata> = commits

            override fun getRefs(): Set<VcsRef> = refs
        }
    }

    override fun readAllHashes(root: VirtualFile, commitConsumer: Consumer<in TimedVcsCommit>): VcsLogProvider.LogData {
        val snapshot = snapshot(root)
        snapshot.changes.forEach { commitConsumer.consume(TimedVcsCommitImpl(hash(it.hash), parents(it), millis(it.timestamp))) }
        val refs = refs(root, snapshot)
        val users = snapshot.changes.map { user(it) }.toSet()
        return object : VcsLogProvider.LogData {
            override fun getRefs(): Set<VcsRef> = refs

            override fun getUsers(): Set<VcsUser> = users
        }
    }

    override fun readMetadata(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsCommitMetadata>) {
        val snapshot = snapshot(root)
        for (h in hashes) {
            snapshot.byHash[AtomicHashes.fromHex(h)]?.let { consumer.consume(metadata(root, it)) }
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsFullCommitDetails>) {
        val snapshot = snapshot(root)
        val repoPath = Path.of(root.path)
        val cli = cli()
        for (h in hashes) {
            val summary = snapshot.byHash[AtomicHashes.fromHex(h)] ?: continue
            val detail = try {
                runBlocking { cli.change(repoPath, summary.hash) }
            } catch (e: AtomicCliException) {
                throw VcsException("Failed to read Atomic change ${summary.hash}: ${e.message}", e)
            }
            val aiSection = ProvenanceFormatter.format(detail)
            val fullMessage = if (aiSection == null) summary.message else summary.message.trimEnd() + "\n\n" + aiSection
            val author = summary.author.ifBlank { detail.author }
            val fullSummary = summary.copy(message = fullMessage, author = author)
            consumer.consume(FullDetails(root, fullSummary, changesOf(summary, detail)))
        }
    }

    private fun changesOf(summary: AtomicChangeSummary, detail: AtomicChangeDetail): List<Change> {
        val previous = AtomicRevisionNumber(summary.dependencies.firstOrNull() ?: summary.hash)
        val current = AtomicRevisionNumber(summary.hash, summary.timestamp)
        return detail.changedFiles.map { file ->
            val filePath = VcsUtil.getFilePath(file.path.toString(), false)
            val before = AtomicContentRevision(filePath, previous)
            val after = AtomicContentRevision(filePath, current)
            when (file.changeType) {
                AtomicChangeType.ADDED -> Change(null, after)
                AtomicChangeType.DELETED -> Change(before, null)
                else -> Change(before, after)
            }
        }
    }

    override fun getSupportedVcs(): VcsKey = AtomicVcs.getKey()

    override fun getReferenceManager(): VcsLogRefManager = refManager

    override fun subscribeToRootRefreshEvents(roots: Collection<VirtualFile>, refresher: VcsLogRefresher): Disposable {
        val registration = Registration(roots, refresher)
        registrations.add(registration)
        return Disposable { registrations.remove(registration) }
    }

    override fun getCommitsMatchingFilter(
        root: VirtualFile,
        filterCollection: VcsLogFilterCollection,
        options: PermanentGraph.Options,
        maxCount: Int,
    ): List<TimedVcsCommit> {
        val textFilter = filterCollection.get(VcsLogFilterCollection.TEXT_FILTER)
        val matching = snapshot(root).changes.filter { textFilter == null || textFilter.matches(it.message) }
        val limited = if (maxCount > 0) matching.take(maxCount) else matching
        return limited.map { TimedVcsCommitImpl(hash(it.hash), parents(it), millis(it.timestamp)) }
    }

    override fun getCurrentUser(root: VirtualFile): VcsUser? = null

    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> {
        val snapshot = snapshot(root)
        val target = AtomicHashes.toAtomic(commitHash)
        return snapshot.heads
            .filter { (_, head) -> reaches(snapshot, head, target) }
            .keys
            .toList()
    }

    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? = null

    override fun getCurrentBranch(root: VirtualFile): String? = snapshot(root).currentView

    // ---- helpers ----

    private fun cli() = AtomicVcs.getInstance(project)?.cli
        ?: throw VcsException("Atomic is not enabled for project ${project.name}")

    private fun snapshot(root: VirtualFile): Snapshot {
        val now = System.currentTimeMillis()
        cache[root.path]?.takeIf { now - it.fetchedAt < CACHE_MILLIS }?.let { return it }
        val repoPath = Path.of(root.path)
        val cli = cli()
        val fresh = try {
            runBlocking {
                val changes = cli.changeGraph(repoPath, MAX_CHANGES)
                // Branch labels are optional: the graph is still useful without them
                val heads = runCatching { cli.viewHeads(repoPath) }.getOrDefault(emptyMap())
                val current = runCatching { cli.listViews(repoPath).firstOrNull { it.isCurrent }?.name }.getOrNull()
                Snapshot(changes, heads, current, System.currentTimeMillis())
            }
        } catch (e: AtomicCliException) {
            throw VcsException("Failed to read the Atomic change graph: ${e.message}", e)
        }
        cache[root.path] = fresh
        return fresh
    }

    private fun reaches(snapshot: Snapshot, from: String, target: String): Boolean {
        val seen = HashSet<String>()
        val stack = ArrayDeque<String>().apply { addLast(from) }
        while (stack.isNotEmpty()) {
            val h = stack.removeLast()
            if (h == target) return true
            if (!seen.add(h)) continue
            snapshot.byHash[h]?.dependencies?.forEach { stack.addLast(it) }
        }
        return false
    }

    private fun refs(root: VirtualFile, snapshot: Snapshot): Set<VcsRef> =
        snapshot.heads.map { (name, head) -> VcsRefImpl(hash(head), name, AtomicViewRefType, root) as VcsRef }.toSet()

    private fun hash(atomicHash: String): Hash = AtomicHashes.toIdeHash(atomicHash)

    private fun parents(change: AtomicChangeSummary): List<Hash> = change.dependencies.map { hash(it) }

    private fun millis(timestamp: String): Long =
        try {
            OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }

    private fun user(change: AtomicChangeSummary): VcsUser = VcsUserImpl(change.author.ifBlank { "Atomic" }, "")

    private fun metadata(root: VirtualFile, change: AtomicChangeSummary): VcsCommitMetadata {
        val time = millis(change.timestamp)
        val user = user(change)
        return VcsCommitMetadataImpl(
            hash(change.hash),
            parents(change),
            time,
            root,
            change.message
                .lineSequence()
                .firstOrNull()
                .orEmpty(),
            user,
            change.message,
            user,
            time,
        )
    }

    private inner class FullDetails(
        root: VirtualFile,
        change: AtomicChangeSummary,
        private val changes: List<Change>,
    ) : VcsCommitMetadataImpl(
            hash(change.hash),
            parents(change),
            millis(change.timestamp),
            root,
            change.message
                .lineSequence()
                .firstOrNull()
                .orEmpty(),
            user(change),
            change.message,
            user(change),
            millis(change.timestamp),
        ),
        VcsFullCommitDetails {
        override fun getChanges(): Collection<Change> = changes

        override fun getChanges(parent: Int): Collection<Change> = changes
    }

    companion object {
        private const val CACHE_MILLIS = 5_000L
        private const val MAX_CHANGES = 2_000

        /** Asks every Atomic log provider of the project to reload (called after record/unrecord). */
        fun refresh(project: Project) {
            VcsLogProvider.LOG_PROVIDER_EP
                .getExtensions(project)
                .filterIsInstance<AtomicLogProvider>()
                .forEach { it.refresh() }
        }
    }
}
