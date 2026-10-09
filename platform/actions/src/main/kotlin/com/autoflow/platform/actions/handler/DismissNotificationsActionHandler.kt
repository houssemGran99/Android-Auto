package com.autoflow.platform.actions.handler

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.platform.actions.ActiveNotification
import com.autoflow.platform.actions.NotificationController

/** Dismisses clearable notifications of other apps matching the optional app / text filters. */
class DismissNotificationsActionHandler(
    private val controller: NotificationController,
    private val ownPackage: String,
) : ActionHandler<ActionSpec.DismissNotifications> {

    override suspend fun execute(action: ActionSpec.DismissNotifications, context: AutomationContext): ActionResult {
        if (!controller.isConnected) {
            return ActionResult.Failure(FailureKind.PERMISSION_DENIED, "Notification access is not enabled")
        }
        val needle = action.textContains?.let(context::resolve)?.trim().orEmpty()
        val matching = controller.activeNotifications().filter { matches(it, action.packageName, needle) }
        matching.forEach { controller.dismiss(it.key) }
        return ActionResult.Success("${matching.size} dismissed")
    }

    private fun matches(notification: ActiveNotification, packageName: String?, needle: String): Boolean =
        notification.clearable &&
            notification.packageName != ownPackage &&
            (packageName.isNullOrBlank() || notification.packageName == packageName) &&
            (
                needle.isEmpty() ||
                    notification.title.contains(needle, ignoreCase = true) ||
                    notification.text.contains(needle, ignoreCase = true)
                )
}
