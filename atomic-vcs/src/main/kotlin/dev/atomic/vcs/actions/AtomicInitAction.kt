package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import dev.atomic.vcs.AtomicVcs

class AtomicInitAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val isEnabled = project != null && !project.isDefault
        e.presentation.isEnabledAndVisible = isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Create Atomic Repository"
            description = "Select directory to initialize an Atomic repository"
            isHideIgnored = false
        }
        val defaultRoot: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: project.guessProjectDir() ?: project.projectFile?.parent
        FileChooser.chooseFile(descriptor, project, defaultRoot) { selectedDir ->
            val vcs = AtomicVcs.getInstance(project) ?: AtomicVcs(project)
            vcs.enableIntegration(selectedDir)
        }
    }
}
