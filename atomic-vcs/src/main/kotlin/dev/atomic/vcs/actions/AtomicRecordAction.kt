package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil

/**
 * "Record Changes..." – Atomic's term for committing. Opens the platform's Commit tool window / dialog.
 */
class AtomicRecordAction : AnAction() {
    private val delegate: AnAction?
        get() = ActionManager.getInstance().getAction("CheckinProject")

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val target = delegate
        if (target == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        target.update(e)
        // The delegate's update may have rewritten the text; keep ours.
        e.presentation.text = "Record Changes..."
        e.presentation.description = "Record the selected changes as an Atomic change"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val target = delegate ?: return
        ActionUtil.performActionDumbAwareWithCallbacks(target, e)
    }
}
