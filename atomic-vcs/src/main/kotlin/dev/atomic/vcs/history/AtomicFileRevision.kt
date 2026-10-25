package dev.atomic.vcs.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.model.AtomicChangeSummary
import dev.atomic.vcs.AtomicRevisionNumber
import dev.atomic.vcs.blockingCliOrNull
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AtomicFileRevision(
    private val myFilePath: FilePath,
    private val mySummary: AtomicChangeSummary,
    private val rootPath: Path,
    private val cli: AtomicCli,
) : VcsFileRevision {
    private val myRevisionNumber = AtomicRevisionNumber(mySummary.hash, mySummary.timestamp)

    override fun getRevisionNumber(): VcsRevisionNumber = myRevisionNumber

    override fun getRevisionDate(): Date = try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        format.parse(mySummary.timestamp.substringBefore("+").substringBefore("Z")) ?: Date()
    } catch (_: ParseException) {
        Date()
    }

    override fun getAuthor(): String = mySummary.author

    override fun getCommitMessage(): String = mySummary.message

    override fun getBranchName(): String? = if (mySummary.isDraft) "draft" else null

    override fun loadContent(): ByteArray =
        blockingCliOrNull { cli.change(rootPath, mySummary.hash) }
            ?.rawPatch
            ?.toByteArray(StandardCharsets.UTF_8)
            ?: ByteArray(0)

    @Suppress("OVERRIDE_DEPRECATION") // still called by older platform code paths
    override fun getContent(): ByteArray? = loadContent()

    override fun getChangedRepositoryPath(): RepositoryLocation? = null
}
