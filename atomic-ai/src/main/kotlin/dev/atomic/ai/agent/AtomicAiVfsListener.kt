package dev.atomic.ai.agent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.InvalidPathException
import java.nio.file.Path

class AtomicAiVfsListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            val pathStr = event.path
            try {
                val path = Path.of(pathStr)
                AtomicAgentSessionTracker.instance.recordTouchedPath(path)
            } catch (e: InvalidPathException) {
                LOG.debug("Ignoring event with an invalid path: $pathStr", e)
            }
        }
    }

    private companion object {
        val LOG = Logger.getInstance(AtomicAiVfsListener::class.java)
    }
}
