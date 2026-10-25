package dev.atomic.vcs

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.*
import com.intellij.vcsUtil.VcsUtil
import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.model.AtomicStatusResult
import java.nio.file.Path

@Suppress("DEPRECATION")
class AtomicChangeProvider(
    private val project: Project,
    private val cli: AtomicCli,
) : ChangeProvider {
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        for (root in resolveRoots(dirtyScope)) {
            progress.checkCanceled()
            progress.text = "Querying Atomic repository status..."

            // If the CLI fails, this root simply reports no changes
            val statusResult = blockingCliOrNull { cli.status(root) } ?: continue
            reportChanges(root, statusResult, builder)
        }
    }

    private fun resolveRoots(dirtyScope: VcsDirtyScope): List<Path> {
        val vcs = AtomicVcs.getInstance(project)
        val vcsRoots = if (vcs != null) {
            ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs).mapNotNull { it.toNioPathOrNull() }
        } else {
            emptyList()
        }
        return vcsRoots.ifEmpty {
            dirtyScope.affectedContentRoots
                .mapNotNull { it.toNioPathOrNull() }
                .distinct()
                .ifEmpty { listOfNotNull(project.basePath?.let { Path.of(it) }) }
        }
    }

    private fun reportChanges(root: Path, status: AtomicStatusResult, builder: ChangelistBuilder) {
        val key = AtomicVcs.getKey()

        for (p in status.modified) {
            val filePath = VcsUtil.getFilePath(p.toFile(), false)
            builder.processChange(Change(recordedRevision(root, p), CurrentContentRevision(filePath), FileStatus.MODIFIED), key)
        }
        for (p in status.added) {
            val filePath = VcsUtil.getFilePath(p.toFile(), false)
            builder.processChange(Change(null, CurrentContentRevision(filePath), FileStatus.ADDED), key)
        }
        for (p in status.deleted) {
            builder.processChange(Change(recordedRevision(root, p), null, FileStatus.DELETED), key)
        }
        for (p in status.conflicted) {
            val filePath = VcsUtil.getFilePath(p.toFile(), false)
            val change = Change(recordedRevision(root, p), CurrentContentRevision(filePath), FileStatus.MERGED_WITH_CONFLICTS)
            builder.processChange(change, key)
        }
        for (p in status.untracked) {
            builder.processUnversionedFile(VcsUtil.getFilePath(p.toFile(), false))
        }
    }

    private fun recordedRevision(root: Path, file: Path): ContentRevision {
        val filePath = VcsUtil.getFilePath(file.toFile(), false)
        return AtomicContentRevision(filePath, AtomicRevisionNumber.HEAD) { fetchRecordedContent(root, file) }
    }

    private fun fetchRecordedContent(root: Path, file: Path): String? = blockingCliOrNull { cli.recordedContent(root, file) }

    override fun isModifiedDocumentTrackingRequired(): Boolean = false
}
