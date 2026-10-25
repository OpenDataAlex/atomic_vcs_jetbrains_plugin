package dev.atomic.vcs.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import dev.atomic.core.cli.AtomicCliException
import dev.atomic.core.model.AtomicView
import dev.atomic.vcs.AtomicNotificationManager
import dev.atomic.vcs.AtomicVcs
import kotlinx.coroutines.runBlocking
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel

class AtomicViewStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "AtomicViewStatusBarWidget"

    override fun getDisplayName(): String = "Atomic View Switcher"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = AtomicViewStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private val LOG = Logger.getInstance(AtomicViewStatusBarWidget::class.java)

class AtomicViewStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
    private val label = JLabel("Atomic: main")
    private var statusBar: StatusBar? = null

    init {
        label.toolTipText = "Atomic View (shared working copy across views)"
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                showViewPopup()
                return true
            }
        }.installOn(label)

        updateWidget()
    }

    override fun ID(): String = "AtomicViewStatusBarWidget"

    override fun getComponent(): JComponent = label

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    fun updateWidget() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val vcs = AtomicVcs.getInstance(project) ?: return@executeOnPooledThread
            val root = project.basePath?.let { Path.of(it) } ?: return@executeOnPooledThread
            try {
                val status = runBlocking { vcs.cli.status(root) }
                ApplicationManager.getApplication().invokeLater {
                    label.text = "Atomic: ${status.currentView}"
                }
            } catch (e: AtomicCliException) {
                LOG.debug("Could not read the current Atomic view", e)
            }
        }
    }

    private fun showViewPopup() {
        val vcs = AtomicVcs.getInstance(project) ?: return
        val root = project.basePath?.let { Path.of(it) } ?: return

        ProgressManager.getInstance().run(object : Task.Modal(project, "Loading Atomic Views...", false) {
            private var views: List<AtomicView> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                views = try {
                    runBlocking { vcs.cli.listViews(root) }
                } catch (e: AtomicCliException) {
                    LOG.debug("Could not list Atomic views", e)
                    listOf(AtomicView("main", isCurrent = true, isDraft = false))
                }
            }

            override fun onSuccess() {
                val group = DefaultActionGroup()
                group.add(object : AnAction("Views share working copy (untracked files remain)", null, null) {
                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = false
                    }

                    override fun actionPerformed(e: AnActionEvent) = Unit
                })
                group.addSeparator()

                for (view in views) {
                    val title = if (view.isCurrent) "✓ ${view.name}" else "   ${view.name}" + (if (view.isDraft) " (draft)" else "")
                    group.add(object : AnAction(title) {
                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                        override fun actionPerformed(e: AnActionEvent) {
                            if (!view.isCurrent) {
                                switchView(view.name)
                            }
                        }
                    })
                }

                group.addSeparator()
                group.add(object : AnAction("+ Create Draft View...") {
                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                    override fun actionPerformed(e: AnActionEvent) {
                        createDraftView()
                    }
                })

                val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                    "Atomic Views",
                    group,
                    DataManager.getInstance().getDataContext(label),
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    true,
                )
                popup.showUnderneathOf(label)
            }
        })
    }

    private fun switchView(viewName: String) {
        val vcs = AtomicVcs.getInstance(project) ?: return
        val root = project.basePath?.let { Path.of(it) } ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Switching to View $viewName...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runBlocking { vcs.cli.switchView(root, viewName) }
                    LocalFileSystem.getInstance().refresh(true)
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    updateWidget()
                    AtomicNotificationManager.notifyInfo(project, "Atomic View Switched", "Switched to view '$viewName'. Working copy changes were preserved.")
                } catch (e: Exception) {
                    AtomicNotificationManager.notifyError(project, "Switch View Failed", e.message ?: "Unknown error")
                }
            }
        })
    }

    private fun createDraftView() {
        val vcs = AtomicVcs.getInstance(project) ?: return
        val root = project.basePath?.let { Path.of(it) } ?: return

        val viewName = Messages
            .showInputDialog(
                project,
                "Enter new draft view name:",
                "Create Draft View",
                Messages.getQuestionIcon(),
            )?.trim()

        if (viewName.isNullOrEmpty()) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Creating Draft View $viewName...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runBlocking {
                        vcs.cli.createDraftView(root, viewName)
                        vcs.cli.switchView(root, viewName)
                    }
                    LocalFileSystem.getInstance().refresh(true)
                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                    updateWidget()
                    AtomicNotificationManager.notifyInfo(project, "Atomic Draft View Created", "Created and switched to draft view '$viewName'.")
                } catch (e: Exception) {
                    AtomicNotificationManager.notifyError(project, "Create Draft View Failed", e.message ?: "Unknown error")
                }
            }
        })
    }
}
