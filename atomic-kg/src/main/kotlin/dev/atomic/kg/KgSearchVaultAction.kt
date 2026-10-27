package dev.atomic.kg

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Files
import java.nio.file.Path

class KgSearchVaultAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val isAtomicRepo = project?.basePath?.let { Files.isDirectory(Path.of(it, ".atomic")) } == true
        e.presentation.isEnabledAndVisible = project != null && !project.isDefault && editor != null && isAtomicRepo
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText
        val filePath = virtualFile?.path?.let(Path::of)
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            val panel = toolWindow.contentManager.contents
                .mapNotNull { it.component as? KgToolWindowPanel }
                .firstOrNull()
                ?: return@activate
            panel.focusFromEditor(KgEditorFocusRequest(selection, filePath))
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Atomic KG"
    }
}
