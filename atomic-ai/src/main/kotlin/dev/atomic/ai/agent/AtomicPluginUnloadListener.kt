package dev.atomic.ai.agent

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val ATOMIC_PLUGIN_ID = "com.bluefiredatasolutions.com.atomic-vcs-integration"
private const val SESSION_CLEANUP_TIMEOUT_MILLIS = 60_000L

class AtomicPluginUnloadListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != ATOMIC_PLUGIN_ID) return

        val tracker = AtomicAgentSessionTracker.instance
        if (tracker.getActiveSessions().isEmpty()) return

        val cleanup = Runnable {
            runBlocking {
                withTimeout(SESSION_CLEANUP_TIMEOUT_MILLIS) {
                    tracker.endAllSessions()
                }
            }
        }

        try {
            if (ApplicationManager.getApplication().isDispatchThread) {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    cleanup,
                    "Ending active Atomic agent sessions",
                    false,
                    null,
                )
            } else {
                cleanup.run()
            }
        } catch (exception: Exception) {
            LOG.warn("Could not end all Atomic agent sessions before plugin unload", exception)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AtomicPluginUnloadListener::class.java)
    }
}
