package dev.atomic.vcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.nio.charset.StandardCharsets

class AtomicContentRevision(
    private val myFile: FilePath,
    private val myRevisionNumber: VcsRevisionNumber,
    private val contentProvider: () -> String? = { null },
) : ContentRevision, ByteBackedContentRevision {
    private var cachedContent: String? = null

    override fun getContent(): String? {
        if (cachedContent == null) {
            cachedContent = contentProvider()
        }
        return cachedContent
    }

    override fun getContentAsBytes(): ByteArray? {
        val str = content
        return str?.toByteArray(StandardCharsets.UTF_8)
    }

    override fun getFile(): FilePath = myFile

    override fun getRevisionNumber(): VcsRevisionNumber = myRevisionNumber
}
