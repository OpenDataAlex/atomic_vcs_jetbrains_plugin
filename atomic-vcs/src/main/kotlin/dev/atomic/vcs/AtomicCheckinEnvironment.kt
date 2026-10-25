package dev.atomic.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.atomic.core.cli.AtomicCli
import kotlinx.coroutines.runBlocking
import java.nio.file.InvalidPathException
import java.nio.file.Path

class AtomicCheckinEnvironment(
    private val project: Project,
    private val cli: AtomicCli,
) : CheckinEnvironment {
    private companion object {
        val LOG = com.intellij.openapi.diagnostic.Logger
            .getInstance(AtomicCheckinEnvironment::class.java)
    }

    override fun getCheckinOperationName(): String = "Record"

    override fun getHelpId(): String? = null

    override fun createCommitOptions(
        panel: CheckinProjectPanel,
        commitContext: CommitContext,
    ): RefreshableOnComponent? = null

    @Suppress("OVERRIDE_DEPRECATION") // still called by older platform code paths
    override fun getDefaultMessageFor(filesToCheckin: Array<out FilePath>): String? = null

    // The platform's commit workflow calls this overload; the default implementation of it ends up as a silent no-op.
    override fun commit(
        changes: List<Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>,
    ): List<VcsException>? = doRecord(changes, commitMessage)

    override fun commit(
        changes: List<Change>,
        preparedComment: String,
    ): List<VcsException>? = doRecord(changes, preparedComment)

    private fun doRecord(
        changes: List<Change>,
        preparedComment: String,
    ): List<VcsException>? {
        val exceptions = mutableListOf<VcsException>()
        val root = project.basePath?.let { Path.of(it) } ?: return null
        LOG.warn("atomic record invoked: ${changes.size} change(s), root=$root, message='$preparedComment'")

        val pathsToRecord = changes.mapNotNull { change -> pathOf(change)?.let { Path.of(it) } }

        try {
            runBlocking { recordAndVerify(root, changes, pathsToRecord, preparedComment) }

            // Refresh VFS and re-query statuses
            LocalFileSystem.getInstance().refresh(true)
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
            dev.atomic.vcs.log.AtomicLogProvider
                .refresh(project)
        } catch (e: Exception) {
            LOG.warn("Atomic record failed", e)
            val detail = (e as? dev.atomic.core.cli.AtomicCommandException)
                ?.let { (it.stderr.ifBlank { it.stdout }).trim() }
                ?.takeIf { it.isNotEmpty() }
            exceptions.add(VcsException("Failed to record Atomic change: ${detail ?: e.message}", e))
        }

        return if (exceptions.isEmpty()) null else exceptions
    }

    private fun pathOf(change: Change): String? = (change.afterRevision ?: change.beforeRevision)?.file?.path

    private suspend fun recordAndVerify(
        root: Path,
        changes: List<Change>,
        pathsToRecord: List<Path>,
        message: String,
    ) {
        // Add any newly created / untracked files
        val addedPaths = changes
            .filter { it.beforeRevision == null && it.afterRevision != null }
            .mapNotNull { pathOf(it)?.let { p -> Path.of(p) } }
        if (addedPaths.isNotEmpty()) {
            cli.add(root, addedPaths)
        }

        // Record changes
        val recorded = cli.record(root, message, pathsToRecord)
        LOG.info("atomic record: ${recorded.command}\n${recorded.rawOutput}")

        // Verify: anything we asked to record that is still pending means the CLI did not record it
        val remaining = cli.status(root)
        val pending = (remaining.modified + remaining.added + remaining.deleted + remaining.conflicted)
            .map { it.toAbsolutePath().normalize() }
            .toSet()
        val notRecorded = pathsToRecord.map { it.toAbsolutePath().normalize() }.filter { it in pending }
        check(notRecorded.isEmpty()) {
            "atomic record did not record: " + notRecorded.joinToString { root.relativize(it).toString() } +
                "\nCommand: ${recorded.command}\nOutput: ${recorded.rawOutput}"
        }
    }

    override fun scheduleMissingFileForDeletion(files: List<FilePath>): List<VcsException>? = null

    override fun scheduleUnversionedFilesForAddition(files: List<VirtualFile>): List<VcsException>? {
        val root = project.basePath?.let { Path.of(it) } ?: return null
        val paths = files.mapNotNull {
            try {
                Path.of(it.path)
            } catch (_: InvalidPathException) {
                null
            }
        }
        if (paths.isNotEmpty()) {
            try {
                runBlocking { cli.add(root, paths) }
            } catch (e: Exception) {
                return listOf(VcsException("Failed to add files: ${e.message}", e))
            }
        }
        return null
    }

    override fun isRefreshAfterCommitNeeded(): Boolean = true
}
