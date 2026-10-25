package dev.atomic.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.vcs.settings.AtomicSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

class AtomicVcsStartupActivity(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ProjectActivity {
    private fun isProjectRootMapping(mapping: VcsDirectoryMapping, basePath: String): Boolean {
        val dir = mapping.directory
        return dir.isBlank() ||
            dir == VcsDirectoryMapping.PROJECT_CONSTANT.get() ||
            dir.trimEnd('/', '\\') == basePath.trimEnd('/', '\\')
    }

    private fun applyAtomicRootMapping(vcsManager: ProjectLevelVcsManager, basePath: String) {
        val others = vcsManager.directoryMappings.filterNot { isProjectRootMapping(it, basePath) }
        vcsManager.directoryMappings = others + VcsDirectoryMapping("", AtomicVcs.NAME)
    }

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return
        val rootPath = Path.of(basePath)
        val dotAtomic = rootPath.resolve(AtomicVcs.DOT_DIR)

        val isAtomicRepo = withContext(ioDispatcher) {
            Files.isDirectory(dotAtomic)
        }
        if (!isAtomicRepo) return

        ensureAtomicMapping(project, basePath)

        // Check if atomic CLI executable exists
        val settings = AtomicSettings.getInstance(project)
        val cli = AtomicCliImpl(executablePathProvider = { settings.executablePath })
        val exe = cli.findExecutable()

        if (exe == null) {
            withContext(mainDispatcher) {
                if (!project.isDisposed) {
                    AtomicNotificationManager.notifyMissingBinary(project)
                }
            }
        } else {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    FileStatusManager.getInstance(project).fileStatusesChanged()
                }
            }
        }
    }

    private fun ensureAtomicMapping(project: Project, basePath: String) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)

        ApplicationManager.getApplication().invokeAndWait {
            if (project.isDisposed) return@invokeAndWait
            val currentMappings = vcsManager.directoryMappings.toList()
            if (currentMappings.any { it.vcs == AtomicVcs.NAME }) return@invokeAndWait

            val rootVcs = currentMappings.firstOrNull { isProjectRootMapping(it, basePath) }?.vcs.orEmpty()
            if (rootVcs.isBlank()) {
                applyAtomicRootMapping(vcsManager, basePath)
            } else {
                AtomicNotificationManager.notifyUseAtomicMapping(project, rootVcs) {
                    applyAtomicRootMapping(vcsManager, basePath)
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                }
            }
        }
    }
}
