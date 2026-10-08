package com.autoflow.core.engine.trigger

import com.autoflow.core.engine.normalizeSsid
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerSpec

/** Decides whether a [TriggerEvent] satisfies a configured [TriggerSpec]. Pure and side-effect free. */
class TriggerMatcher {
    fun matches(spec: TriggerSpec, event: TriggerEvent): Boolean = when (spec) {
        // Time and location triggers are delivered as events addressed to one automation.
        is TriggerSpec.Time, is TriggerSpec.Interval -> false
        is TriggerSpec.LocationEnter, is TriggerSpec.LocationExit -> false
        is TriggerSpec.WifiConnected -> event is TriggerEvent.WifiConnected && ssidMatches(spec.ssid, event.ssid)
        is TriggerSpec.WifiDisconnected -> event is TriggerEvent.WifiDisconnected && ssidMatches(spec.ssid, event.ssid)
        is TriggerSpec.BluetoothConnected ->
            event is TriggerEvent.BluetoothConnected && deviceMatches(spec.deviceName, event.deviceName, event.address)
        is TriggerSpec.BluetoothDisconnected ->
            event is TriggerEvent.BluetoothDisconnected && deviceMatches(spec.deviceName, event.deviceName, event.address)
        is TriggerSpec.BatteryLevel -> event is TriggerEvent.BatteryChanged && crossed(spec, event)
        TriggerSpec.ChargerConnected -> event is TriggerEvent.ChargerConnected
        TriggerSpec.ChargerDisconnected -> event is TriggerEvent.ChargerDisconnected
        is TriggerSpec.AppOpened -> event is TriggerEvent.AppOpened && event.packageName == spec.packageName
        is TriggerSpec.HeadphonesConnected -> event is TriggerEvent.HeadphonesConnected && kindMatches(spec.kind, event.kind)
        is TriggerSpec.HeadphonesDisconnected ->
            event is TriggerEvent.HeadphonesDisconnected && kindMatches(spec.kind, event.kind)
    }

    /** Whether the location trigger [spec] corresponds to the transition direction of [event]. */
    fun matchesTransition(spec: TriggerSpec, event: TriggerEvent.LocationTransition): Boolean = when (spec) {
        is TriggerSpec.LocationEnter -> event.entered
        is TriggerSpec.LocationExit -> !event.entered
        else -> false
    }

    private fun ssidMatches(wanted: String?, actual: String?): Boolean {
        val expected = normalizeSsid(wanted) ?: return true
        return expected.equals(normalizeSsid(actual), ignoreCase = true)
    }

    private fun deviceMatches(wanted: String?, name: String?, address: String?): Boolean {
        val expected = wanted?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return expected.equals(name?.trim(), ignoreCase = true) || expected.equals(address, ignoreCase = true)
    }

    /** Fires only when the level crosses the threshold, never on the first reading. */
    private fun crossed(spec: TriggerSpec.BatteryLevel, event: TriggerEvent.BatteryChanged): Boolean {
        val previous = event.previousLevel ?: return false
        return when (spec.direction) {
            ThresholdDirection.BELOW -> previous >= spec.threshold && event.level < spec.threshold
            ThresholdDirection.ABOVE -> previous <= spec.threshold && event.level > spec.threshold
        }
    }

    private fun kindMatches(wanted: HeadphoneKind, actual: HeadphoneKind): Boolean =
        wanted == HeadphoneKind.ANY || wanted == actual
}
