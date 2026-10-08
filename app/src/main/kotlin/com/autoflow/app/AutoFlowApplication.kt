package com.autoflow.app

import android.app.Application
import com.autoflow.app.runtime.AppForegroundTracker
import com.autoflow.app.runtime.AppNotifications
import com.autoflow.app.runtime.TriggerCoordinator
import com.autoflow.platform.actions.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AutoFlowApplication : Application() {
    @Inject lateinit var coordinator: TriggerCoordinator

    @Inject lateinit var foregroundTracker: AppForegroundTracker

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        AppNotifications.ensureChannels(this)
        foregroundTracker.start()
        // The engine runs independently of the UI: the coordinator keeps alarms and
        // monitoring in sync with the stored automations whenever the process is alive.
        coordinator.start()
    }
}
