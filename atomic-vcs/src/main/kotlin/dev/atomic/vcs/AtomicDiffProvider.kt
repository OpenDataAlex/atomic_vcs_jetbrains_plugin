package dev.atomic.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import dev.atomic.core.cli.AtomicCli
import java.nio.file.Path

class AtomicDiffProvider(
    private val project: Project,
    private val cli: AtomicCli,
) : DiffProvider {
    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? = getLastRevision(file)?.number

    override fun getLatestCommittedRevision(file: VirtualFile): VcsRevisionNumber? = getLastRevision(file)?.number

    override fun createFileContent(revision: VcsRevisionNumber, file: VirtualFile): ContentRevision? {
        val filePath = VcsUtil.getFilePath(file)
        val root = project.basePath?.let { Path.of(it) } ?: return null
        val targetPath = file.toNioPathOrNull() ?: return null

        return AtomicContentRevision(filePath, revision) {
            blockingCliOrNull { cli.recordedContent(root, targetPath) }
        }
    }

    override fun getLastRevision(file: VirtualFile): ItemLatestState? = latestState(file.toNioPathOrNull())

    override fun getLastRevision(filePath: FilePath): ItemLatestState? = latestState(filePath.path.toNioPathOrNull())

    private fun latestState(targetPath: Path?): ItemLatestState? {
        val root = project.basePath?.let { Path.of(it) } ?: return null
        val latest = blockingCliOrNull { cli.log(root, targetPath, maxCount = 1) }?.firstOrNull()
        val rev = if (latest != null) {
            AtomicRevisionNumber(latest.hash, latest.timestamp)
        } else {
            AtomicRevisionNumber.HEAD
        }
        return ItemLatestState(rev, true, false)
    }
}
