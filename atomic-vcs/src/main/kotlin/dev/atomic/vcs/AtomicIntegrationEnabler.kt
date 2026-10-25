package dev.atomic.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class AtomicIntegrationEnabler(
    private val vcs: AtomicVcs,
    targetDirectory: VirtualFile? = null,
) : VcsIntegrationEnabler(vcs, targetDirectory) {
    private val logger = Logger.getInstance(AtomicIntegrationEnabler::class.java)

    override fun initOrNotifyError(targetDirectory: VirtualFile): Boolean = try {
        val rootPath = Path.of(targetDirectory.path)
        val success = runBlocking {
            vcs.cli.init(rootPath)
        }
        if (success) {
            refreshVcsDir(targetDirectory, AtomicVcs.DOT_DIR)
            VcsNotifier.getInstance(myProject).notifySuccess(
                "atomic.repository.created",
                "Atomic Repository Created",
                "Created Atomic repository in ${targetDirectory.presentableUrl}",
            )
            true
        } else {
            VcsNotifier.getInstance(myProject).notifyError(
                "atomic.init.error",
                "Atomic Init Error",
                "Could not initialize Atomic repository in ${targetDirectory.presentableUrl}",
            )
            false
        }
    } catch (e: Exception) {
        logger.warn("Failed to initialize Atomic repository", e)
        VcsNotifier.getInstance(myProject).notifyError(
            "atomic.init.error",
            "Atomic Init Error",
            e.message ?: "Failed to initialize Atomic repository",
        )
        false
    }
}
