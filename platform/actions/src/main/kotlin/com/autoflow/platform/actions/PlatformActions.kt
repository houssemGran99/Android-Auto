package com.autoflow.platform.actions

import android.content.Context
import com.autoflow.core.engine.action.ActionRegistry
import com.autoflow.core.engine.http.HttpExecutor
import com.autoflow.core.engine.http.HttpRequestActionHandler
import com.autoflow.core.model.ActionSpec
import com.autoflow.platform.actions.handler.LaunchAppActionHandler
import com.autoflow.platform.actions.handler.OpenSettingsActionHandler
import com.autoflow.platform.actions.handler.OpenUrlActionHandler
import com.autoflow.platform.actions.handler.PlaySoundActionHandler
import com.autoflow.platform.actions.handler.SetBrightnessActionHandler
import com.autoflow.platform.actions.handler.SetDoNotDisturbActionHandler
import com.autoflow.platform.actions.handler.SetVolumeActionHandler
import com.autoflow.platform.actions.handler.ShowNotificationActionHandler
import com.autoflow.platform.actions.handler.SpeakActionHandler
import com.autoflow.platform.actions.handler.VibrateActionHandler
import com.autoflow.platform.permissions.PermissionManager

/** Registers every Android action handler. Control-flow actions are handled by the engine itself. */
object PlatformActions {
    fun registry(
        context: Context,
        permissions: PermissionManager,
        httpExecutor: HttpExecutor,
        isAppInForeground: () -> Boolean,
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
            .register<ActionSpec.Vibrate>(VibrateActionHandler(appContext))
            .register<ActionSpec.HttpRequest>(HttpRequestActionHandler(httpExecutor))
            .build()
    }
}
