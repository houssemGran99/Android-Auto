package com.autoflow.platform.triggers.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.TriggerEvent

/** Base class for sources backed by a runtime-registered BroadcastReceiver (system broadcasts only). */
abstract class BroadcastTriggerSource(context: Context) : TriggerSource {
    protected val context: Context = context.applicationContext
    private var receiver: BroadcastReceiver? = null
    private var listener: ((TriggerEvent) -> Unit)? = null

    protected abstract fun filter(): IntentFilter

    /** Maps a broadcast to an event, or null to ignore it. */
    protected abstract fun toEvent(intent: Intent): TriggerEvent?

    protected open fun onRegistered() = Unit

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        listener = onEvent
        onRegistered()
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                toEvent(intent)?.let { event -> listener?.invoke(event) }
            }
        }
        receiver = newReceiver
        ContextCompat.registerReceiver(context, newReceiver, filter(), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun unregister() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        listener = null
    }
}
