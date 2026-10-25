package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import dev.atomic.vcs.AtomicVcs

/**
 * The "Atomic" submenu of the VCS menu. Only visible when the project has an Atomic mapping.
 */
class AtomicMenuGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(AtomicVcs.NAME)
    }
}
