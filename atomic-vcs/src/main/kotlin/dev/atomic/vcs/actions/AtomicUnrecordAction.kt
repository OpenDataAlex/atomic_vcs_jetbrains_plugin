package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import dev.atomic.vcs.AtomicNotificationManager
import dev.atomic.vcs.AtomicVcs
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val UNRECORD_TITLE = "Atomic Unrecord"

class AtomicUnrecordAction : AnAction(
    UNRECORD_TITLE,
    "Unrecord the latest patch from the current view into working copy changes",
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val vcs = project?.let { AtomicVcs.getInstance(it) }
        e.presentation.isEnabledAndVisible = vcs != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = AtomicVcs.getInstance(project) ?: return
        val root = project.basePath?.let { Path.of(it) } ?: return

        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to unrecord the latest change from the current view?\n\n" +
                "The patch will be removed from the view's history and its modifications " +
                "will return to your working tree.",
            UNRECORD_TITLE,
            "Unrecord",
            "Cancel",
            Messages.getWarningIcon(),
        )

        if (confirm != Messages.YES) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Unrecording Atomic Change...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runBlocking { vcs.cli.unrecord(root) }
                    LocalFileSystem.getInstance().refresh(true)
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    dev.atomic.vcs.log.AtomicLogProvider
                        .refresh(project)
                    AtomicNotificationManager.notifyInfo(project, UNRECORD_TITLE, "Successfully unrecorded the latest change.")
                } catch (ex: Exception) {
                    AtomicNotificationManager.notifyError(project, "$UNRECORD_TITLE Failed", ex.message ?: "Unknown error")
                }
            }
        })
    }
}
