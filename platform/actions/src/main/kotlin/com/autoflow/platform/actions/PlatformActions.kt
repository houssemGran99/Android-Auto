package com.autoflow.platform.actions

import android.content.Context
import com.autoflow.core.engine.action.ActionRegistry
import com.autoflow.core.engine.http.HttpExecutor
import com.autoflow.core.engine.http.HttpRequestActionHandler
import com.autoflow.core.model.ActionSpec
import com.autoflow.platform.actions.handler.CopyFileActionHandler
import com.autoflow.platform.actions.handler.DeleteFileActionHandler
import com.autoflow.platform.actions.handler.DismissNotificationsActionHandler
import com.autoflow.platform.actions.handler.FlashlightActionHandler
import com.autoflow.platform.actions.handler.LaunchAppActionHandler
import com.autoflow.platform.actions.handler.ListFilesActionHandler
import com.autoflow.platform.actions.handler.LockScreenActionHandler
import com.autoflow.platform.actions.handler.MediaControlActionHandler
import com.autoflow.platform.actions.handler.OpenSettingsActionHandler
import com.autoflow.platform.actions.handler.OpenUrlActionHandler
import com.autoflow.platform.actions.handler.PlayMusicFolderActionHandler
import com.autoflow.platform.actions.handler.PlaySoundActionHandler
import com.autoflow.platform.actions.handler.ReadFileActionHandler
import com.autoflow.platform.actions.handler.SetBrightnessActionHandler
import com.autoflow.platform.actions.handler.SetDoNotDisturbActionHandler
import com.autoflow.platform.actions.handler.SetScreenTimeoutActionHandler
import com.autoflow.platform.actions.handler.SetVolumeActionHandler
import com.autoflow.platform.actions.handler.ShowNotificationActionHandler
import com.autoflow.platform.actions.handler.SpeakActionHandler
import com.autoflow.platform.actions.handler.StopMusicActionHandler
import com.autoflow.platform.actions.handler.VibrateActionHandler
import com.autoflow.platform.actions.handler.WriteFileActionHandler
import com.autoflow.platform.permissions.PermissionManager

/** Registers every Android action handler. Control-flow actions are handled by the engine itself. */
object PlatformActions {
    fun registry(
        context: Context,
        permissions: PermissionManager,
        httpExecutor: HttpExecutor,
        notifications: NotificationController,
        isAppInForeground: () -> Boolean,
        /** Lets the app register handlers that need its own activities or services. */
        extra: ActionRegistry.Builder.(ActivityLauncher) -> Unit = {},
    ): ActionRegistry {
        val appContext = context.applicationContext
        val launcher = ActivityLauncher(appContext, permissions, isAppInForeground)
        return ActionRegistry.Builder()
            .register<ActionSpec.ShowNotification>(ShowNotificationActionHandler(appContext, permissions))
            .register<ActionSpec.LaunchApp>(LaunchAppActionHandler(appContext, launcher))
            .register<ActionSpec.OpenUrl>(OpenUrlActionHandler(launcher))
            .register<ActionSpec.OpenSettings>(OpenSettingsActionHandler(appContext, launcher))
            .register<ActionSpec.SetBrightness>(SetBrightnessActionHandler(appContext))
            .register<ActionSpec.SetVolume>(SetVolumeActionHandler(appContext))
            .register<ActionSpec.SetDoNotDisturb>(SetDoNotDisturbActionHandler(appContext))
            .register<ActionSpec.Speak>(SpeakActionHandler(appContext))
            .register<ActionSpec.PlaySound>(PlaySoundActionHandler(appContext))
            .register<ActionSpec.MediaControl>(MediaControlActionHandler(appContext))
            .register<ActionSpec.PlayMusicFolder>(PlayMusicFolderActionHandler(appContext))
            .register<ActionSpec.StopMusic>(StopMusicActionHandler())
            .register<ActionSpec.WriteFile>(WriteFileActionHandler(appContext))
            .register<ActionSpec.ReadFile>(ReadFileActionHandler(appContext))
            .register<ActionSpec.ListFiles>(ListFilesActionHandler(appContext))
            .register<ActionSpec.CopyFile>(CopyFileActionHandler(appContext))
            .register<ActionSpec.DeleteFile>(DeleteFileActionHandler(appContext))
            .register<ActionSpec.SetScreenTimeout>(SetScreenTimeoutActionHandler(appContext))
            .register<ActionSpec.LockScreen>(LockScreenActionHandler(appContext, permissions))
            .register<ActionSpec.Flashlight>(FlashlightActionHandler(appContext))
            .register<ActionSpec.Vibrate>(VibrateActionHandler(appContext))
            .register<ActionSpec.HttpRequest>(HttpRequestActionHandler(httpExecutor))
            .register<ActionSpec.DismissNotifications>(DismissNotificationsActionHandler(notifications, appContext.packageName))
            .apply { extra(launcher) }
            .build()
    }
}
