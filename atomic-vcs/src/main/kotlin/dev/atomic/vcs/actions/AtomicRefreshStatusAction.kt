package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import dev.atomic.vcs.AtomicVcs

class AtomicRefreshStatusAction : AnAction(
    "Refresh Atomic Status",
    "Re-query the Atomic working copy status",
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(AtomicVcs.NAME)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LocalFileSystem.getInstance().refresh(true)
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }
}
