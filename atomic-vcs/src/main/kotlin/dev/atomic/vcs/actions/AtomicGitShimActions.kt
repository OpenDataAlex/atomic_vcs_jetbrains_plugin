package dev.atomic.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.atomic.core.cli.AtomicCli
import dev.atomic.vcs.AtomicNotificationManager
import dev.atomic.vcs.AtomicVcs
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val SHIM_TITLE = "Atomic Git Shim"
private const val MAX_MESSAGE = 2000

internal object AtomicGitShimRunner {
    fun run(
        project: Project,
        progressTitle: String,
        confirmMessage: String? = null,
        command: suspend (AtomicCli, Path) -> String,
    ) {
        val vcs = AtomicVcs.getInstance(project) ?: return
        val root = project.basePath?.let { Path.of(it) } ?: return
        if (confirmMessage != null) {
            val confirm = Messages.showYesNoDialog(
                project,
                confirmMessage,
                SHIM_TITLE,
                "Continue",
                "Cancel",
                Messages.getQuestionIcon(),
            )
            if (confirm != Messages.YES) return
        }
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, progressTitle, false) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val output = runBlocking { command(vcs.cli, root) }
                        val body = output.ifBlank { "Command finished." }.take(MAX_MESSAGE)
                        AtomicNotificationManager.notifyInfo(project, SHIM_TITLE, body)
                    } catch (ex: Exception) {
                        AtomicNotificationManager.notifyError(project, "$SHIM_TITLE Failed", ex.message ?: "Unknown error")
                    }
                }
            },
        )
    }
}

class AtomicGitShimEnableAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { AtomicVcs.getInstance(it) } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AtomicGitShimRunner.run(
            project,
            "Enabling Atomic Git Shim...",
            "Enable the Git shim so Atomic and Git share this working copy?\n\n" +
                "The IDE still maps one VCS per directory — keep Atomic as the mapping. " +
                "Git stays on disk for GitHub/GitLab; the shim keeps both histories in agreement.",
        ) { cli, root -> cli.gitShimEnable(root) }
    }
}

class AtomicGitShimDisableAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { AtomicVcs.getInstance(it) } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AtomicGitShimRunner.run(
            project,
            "Disabling Atomic Git Shim...",
            "Disable the Git shim for this repository?",
        ) { cli, root -> cli.gitShimDisable(root) }
    }
}

class AtomicGitShimStatusAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { AtomicVcs.getInstance(it) } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AtomicGitShimRunner.run(project, "Checking Atomic Git Shim...") { cli, root -> cli.gitShimStatus(root) }
    }
}

class AtomicGitShimVerifyAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { AtomicVcs.getInstance(it) } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AtomicGitShimRunner.run(project, "Verifying Atomic Git Shim...") { cli, root -> cli.gitShimVerify(root) }
    }
}

class AtomicGitShimReconcileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { AtomicVcs.getInstance(it) } != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AtomicGitShimRunner.run(
            project,
            "Reconciling Atomic Git Shim...",
            "Reconcile Git HEAD with the current Atomic view?",
        ) { cli, root -> cli.gitShimReconcile(root) }
    }
}
