package dev.atomic.vcs

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import dev.atomic.vcs.settings.AtomicSettingsConfigurable

object AtomicNotificationManager {
    private const val GROUP_ID = "Atomic VCS Notifications"

    fun notifyMissingBinary(project: Project) {
        val notification = NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                "Atomic CLI Not Found",
                "The `atomic` version control binary was not found on PATH. Please install it or configure its location in settings.",
                NotificationType.WARNING,
            )

        notification.addAction(
            NotificationAction.createSimpleExpiring("Configure Atomic") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AtomicSettingsConfigurable::class.java)
            },
        )

        notification.notify(project)
    }

    fun notifyUseAtomicMapping(project: Project, currentVcs: String, onAccept: () -> Unit) {
        val notification = NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(
                "Atomic Repository Detected",
                "This project contains an Atomic repository (.atomic) but its root is mapped to $currentVcs. " +
                    "Switch the project root to Atomic to see file statuses and record changes from the Commit tool window.",
                NotificationType.INFORMATION,
            )

        notification.addAction(
            NotificationAction.createSimpleExpiring("Use Atomic") { onAccept() },
        )

        notification.notify(project)
    }

    fun notifyError(project: Project, title: String, message: String) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, message, NotificationType.ERROR)
            .notify(project)
    }

    fun notifyInfo(project: Project, title: String, message: String) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }
}
