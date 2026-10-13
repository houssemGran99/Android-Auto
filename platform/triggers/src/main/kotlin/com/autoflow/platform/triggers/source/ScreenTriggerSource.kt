package com.autoflow.platform.triggers.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/** Screen on / off and "user present" (unlocked); these broadcasts only reach registered receivers. */
class ScreenTriggerSource(context: Context) : BroadcastTriggerSource(context) {
    override val family = TriggerFamily.SCREEN

    override fun filter() = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_USER_PRESENT)
    }

    override fun toEvent(intent: Intent): TriggerEvent? = when (intent.action) {
        Intent.ACTION_SCREEN_ON -> TriggerEvent.ScreenChanged(on = true)
        Intent.ACTION_SCREEN_OFF -> TriggerEvent.ScreenChanged(on = false)
        Intent.ACTION_USER_PRESENT -> TriggerEvent.DeviceUnlocked
        else -> null
    }
}
