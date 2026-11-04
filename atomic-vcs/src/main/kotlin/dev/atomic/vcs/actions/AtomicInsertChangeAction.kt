package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.log.VcsLogDataKeys
import dev.atomic.vcs.AtomicNotificationManager
import dev.atomic.vcs.AtomicVcs
import dev.atomic.vcs.log.AtomicHashes
import dev.atomic.vcs.log.AtomicLogProvider
import dev.atomic.vcs.settings.AtomicSettings
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val INSERT_TITLE = "Atomic Insert Change"

/**
 * Promotes one reviewed change (typically from an `agent-*` draft view) into another view with
 * `atomic insert --view <target> <hash>`. This is the "promote only the reviewed change" step of the
 * recommended agent workflow.
 */
class AtomicInsertChangeAction : AnAction(
    "Insert Change into View...",
    "Promote the selected change into another view after review",
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && AtomicVcs.getInstance(project) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = AtomicVcs.getInstance(project)
        val root = project.basePath?.let { Path.of(it) }
        if (vcs == null || root == null) return

        val selected = e
            .getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
            ?.commits
            ?.singleOrNull()
            ?.hash
            ?.let(AtomicHashes::toAtomic)
        val changeHash = selected ?: askForHash(project) ?: return

        val targets = InsertTargets.candidates(blockingViews(vcs, root), AtomicSettings.getInstance(project).defaultParentView)
        if (targets.isEmpty()) {
            Messages.showInfoMessage(project, "There is no non-draft view to insert into.", INSERT_TITLE)
            return
        }

        JBPopupFactory
            .getInstance()
            .createPopupChooserBuilder(targets)
            .setTitle("Insert ${changeHash.take(12)} into View")
            .setItemChosenCallback { target -> confirmAndInsert(project, vcs, root, changeHash, target) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun askForHash(project: Project): String? =
        Messages
            .showInputDialog(project, "Hash of the change to insert:", INSERT_TITLE, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun confirmAndInsert(project: Project, vcs: AtomicVcs, root: Path, changeHash: String, target: String) {
        val confirm = Messages.showYesNoDialog(
            project,
            "Insert change ${changeHash.take(12)} into view '$target'?\n\n" +
                "Its dependencies are inserted automatically. Review the change's scope in the Log tab first; " +
                "this cannot be previewed safely, so it is applied immediately.",
            INSERT_TITLE,
            "Insert",
            "Cancel",
            Messages.getWarningIcon(),
        )
        if (confirm == Messages.YES) runInsert(project, vcs, root, changeHash, target)
    }

    private fun runInsert(project: Project, vcs: AtomicVcs, root: Path, changeHash: String, target: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Inserting Atomic Change...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = runBlocking { vcs.cli.insertChange(root, changeHash, target) }
                    LocalFileSystem.getInstance().refresh(true)
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    AtomicLogProvider.refresh(project)
                    val message = if (result.inserted) {
                        "Inserted ${changeHash.take(12)} into '$target'."
                    } else {
                        "Nothing was inserted: '$target' already contains ${changeHash.take(12)}."
                    }
                    AtomicNotificationManager.notifyInfo(project, INSERT_TITLE, message)
                } catch (ex: Exception) {
                    AtomicNotificationManager.notifyError(project, "$INSERT_TITLE Failed", ex.message ?: "Unknown error")
                }
            }
        })
    }

    private fun blockingViews(vcs: AtomicVcs, root: Path) =
        try {
            runBlocking { vcs.cli.listViews(root) }
        } catch (_: dev.atomic.core.cli.AtomicCliException) {
            emptyList()
        }
}
