package com.autoflow.app.ui.text

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.autoflow.app.R
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Automation
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.ExecutionStatus
import com.autoflow.core.model.ExecutionStep
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.HttpAuth
import com.autoflow.core.model.NotificationPriority
import com.autoflow.core.model.SettingsPanel
import com.autoflow.core.model.SoundType
import com.autoflow.core.model.StepKind
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.VariableScope
import com.autoflow.core.model.Weekday
import com.autoflow.core.model.typeKey
import java.time.format.TextStyle
import java.util.Locale

/** Turns domain objects into localized, human readable text. */
class SpecFormatter(private val res: Resources) {

    private fun s(id: Int, vararg args: Any): String = res.getString(id, *args)

    fun res(id: Int): String = res.getString(id)

    // region Triggers
    fun triggerTitle(spec: TriggerSpec): String = triggerTypeTitle(spec.typeKey)

    fun triggerTypeTitle(typeKey: String): String = TRIGGER_TITLES[typeKey]?.let(::s) ?: typeKey

    fun triggerDetail(spec: TriggerSpec): String = when (spec) {
        is TriggerSpec.Time -> listOf(spec.at.toString(), days(spec.days)).joinToString(" · ")
        is TriggerSpec.Interval -> res.getQuantityString(R.plurals.every_minutes, spec.everyMinutes, spec.everyMinutes)
        is TriggerSpec.WifiConnected -> spec.ssid?.takeIf { it.isNotBlank() } ?: s(R.string.any_network)
        is TriggerSpec.WifiDisconnected -> spec.ssid?.takeIf { it.isNotBlank() } ?: s(R.string.any_network)
        is TriggerSpec.BluetoothConnected -> spec.deviceName?.takeIf { it.isNotBlank() } ?: s(R.string.any_device)
        is TriggerSpec.BluetoothDisconnected -> spec.deviceName?.takeIf { it.isNotBlank() } ?: s(R.string.any_device)
        is TriggerSpec.BatteryLevel -> when (spec.direction) {
            ThresholdDirection.BELOW -> s(R.string.battery_below, spec.threshold)
            ThresholdDirection.ABOVE -> s(R.string.battery_above, spec.threshold)
        }
        TriggerSpec.ChargerConnected, TriggerSpec.ChargerDisconnected -> ""
        is TriggerSpec.AppOpened -> spec.appLabel.ifBlank { spec.packageName }
        is TriggerSpec.HeadphonesConnected -> headphoneKind(spec.kind)
        is TriggerSpec.HeadphonesDisconnected -> headphoneKind(spec.kind)
        is TriggerSpec.NotificationReceived -> listOfNotNull(
            spec.appLabel.ifBlank { spec.packageName } ?: s(R.string.any_app),
            spec.textContains?.takeIf { it.isNotBlank() }?.let { s(R.string.contains_format, it) },
        ).joinToString(" · ")
        is TriggerSpec.CalendarEventStart -> calendarFilter(spec.titleContains)
        is TriggerSpec.CalendarEventEnd -> calendarFilter(spec.titleContains)
        is TriggerSpec.LocationEnter -> place(spec.place)
        is TriggerSpec.LocationExit -> place(spec.place)
    }

    fun place(place: com.autoflow.core.model.GeoPlace): String = s(R.string.place_detail, place.name, place.radiusMeters)

    private fun calendarFilter(titleContains: String?): String =
        titleContains?.takeIf { it.isNotBlank() }?.let { s(R.string.contains_format, it) } ?: s(R.string.any_event)

    fun triggerEvent(key: String): String = EVENT_TITLES[key]?.let(::s) ?: key
    // endregion

    // region Conditions
    fun conditionTitle(node: ConditionNode): String = conditionTypeTitle(node.typeKey)

    fun conditionTypeTitle(typeKey: String): String = CONDITION_TITLES[typeKey]?.let(::s) ?: typeKey

    fun conditionDetail(node: ConditionNode): String = when (node) {
        is ConditionNode.And, is ConditionNode.Or, is ConditionNode.Not -> ""
        is ConditionNode.TimeRange -> "${node.start} – ${node.end}"
        is ConditionNode.DaysOfWeek -> days(node.days)
        is ConditionNode.BatteryLevel -> "${comparison(node.comparison)} ${node.value}%"
        is ConditionNode.Charging -> s(if (node.charging) R.string.state_charging else R.string.state_not_charging)
        is ConditionNode.WifiState -> when {
            !node.connected && node.ssid.isNullOrBlank() -> s(R.string.state_disconnected)
            !node.connected -> s(R.string.state_not_connected_to, node.ssid.orEmpty())
            node.ssid.isNullOrBlank() -> s(R.string.state_connected)
            else -> s(R.string.state_connected_to, node.ssid.orEmpty())
        }
        is ConditionNode.BluetoothState -> s(if (node.enabled) R.string.state_on else R.string.state_off)
        is ConditionNode.HeadphonesState -> s(if (node.connected) R.string.state_connected else R.string.state_disconnected)
        is ConditionNode.VariableCompare -> "${node.variable} ${comparison(node.comparison)} ${node.value}"
    }
    // endregion

    // region Actions
    fun actionTitle(spec: ActionSpec): String = actionTypeTitle(spec.typeKey)

    fun actionTypeTitle(typeKey: String): String = ACTION_TITLES[typeKey]?.let(::s) ?: typeKey

    fun actionDetail(spec: ActionSpec): String = when (spec) {
        is ActionSpec.ShowNotification -> listOf(spec.title, spec.message).filter { it.isNotBlank() }.joinToString(" — ")
        is ActionSpec.LaunchApp -> spec.appLabel.ifBlank { spec.packageName }
        is ActionSpec.OpenUrl -> spec.url
        is ActionSpec.OpenSettings -> panel(spec.panel)
        is ActionSpec.SetBrightness -> "${spec.percent}%"
        is ActionSpec.SetVolume -> "${stream(spec.stream)} · ${spec.percent}%"
        is ActionSpec.SetDoNotDisturb -> s(if (spec.enabled) R.string.state_on else R.string.state_off)
        is ActionSpec.Speak -> spec.text
        is ActionSpec.PlaySound -> sound(spec.sound)
        is ActionSpec.Vibrate -> s(R.string.duration_ms, spec.durationMs)
        is ActionSpec.HttpRequest -> "${spec.method} ${spec.url}" + when (spec.auth) {
            is HttpAuth.Basic, is HttpAuth.Bearer -> " 🔒"
            null -> ""
        }
        is ActionSpec.Delay -> duration(spec.durationMs)
        is ActionSpec.SetVariable -> "\$${spec.name} = ${spec.value}" +
            (if (spec.scope == VariableScope.LOCAL) " (${s(R.string.scope_local)})" else "")
        is ActionSpec.IfElse -> conditionSummary(spec.condition)
        is ActionSpec.Repeat -> res.getQuantityString(R.plurals.repeat_times, spec.times, spec.times)
        is ActionSpec.DismissNotifications -> listOfNotNull(
            spec.appLabel.ifBlank { spec.packageName } ?: s(R.string.any_app),
            spec.textContains?.takeIf { it.isNotBlank() }?.let { s(R.string.contains_format, it) },
        ).joinToString(" · ")
    }
    // endregion

    fun conditionSummary(node: ConditionNode): String = when (node) {
        is ConditionNode.And -> node.children.joinToString(" ${s(R.string.logic_and)} ") { conditionSummary(it) }
        is ConditionNode.Or -> node.children.joinToString(" ${s(R.string.logic_or)} ") { conditionSummary(it) }
            .let { if (node.children.size > 1) "($it)" else it }
        is ConditionNode.Not -> "${s(R.string.logic_not)} ${conditionSummary(node.child)}"
        else -> listOf(conditionTitle(node), conditionDetail(node)).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun automationSummary(automation: Automation): String {
        val whenPart = if (automation.triggers.isEmpty()) {
            s(R.string.summary_manual)
        } else {
            automation.triggers.joinToString(", ") { trigger ->
                listOf(triggerTitle(trigger), triggerDetail(trigger)).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        val actions = res.getQuantityString(R.plurals.action_count, automation.actions.size, automation.actions.size)
        return "$whenPart → $actions"
    }

    fun stepLabel(step: ExecutionStep): String = when (step.kind) {
        StepKind.TRIGGER -> triggerEvent(step.key)
        StepKind.CONDITION -> conditionTypeTitle(step.key)
        StepKind.ACTION -> actionTypeTitle(step.key)
        StepKind.INFO -> INFO_TITLES[step.key]?.let(::s) ?: step.key
    }

    fun status(status: ExecutionStatus): String = s(
        when (status) {
            ExecutionStatus.SUCCESS -> R.string.status_success
            ExecutionStatus.PARTIAL -> R.string.status_partial
            ExecutionStatus.FAILED -> R.string.status_failed
            ExecutionStatus.SKIPPED -> R.string.status_skipped
        },
    )

    fun failureHint(kind: FailureKind): String = s(
        when (kind) {
            FailureKind.PERMISSION_DENIED -> R.string.failure_permission
            FailureKind.NOT_SUPPORTED -> R.string.failure_not_supported
            FailureKind.INVALID_CONFIGURATION -> R.string.failure_invalid
            FailureKind.TIMEOUT -> R.string.failure_timeout
            FailureKind.ERROR -> R.string.failure_error
        },
    )

    fun days(days: Set<Weekday>): String = when {
        days.isEmpty() || days.size == 7 -> s(R.string.every_day)
        days == Weekday.WORKDAYS -> s(R.string.weekdays)
        days == Weekday.WEEKEND -> s(R.string.weekend)
        else -> days.sortedBy { it.ordinal }.joinToString(", ") { dayShort(it) }
    }

    fun dayShort(day: Weekday): String = day.toDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun comparison(comparison: Comparison): String = when (comparison) {
        Comparison.EQUALS -> "="
        Comparison.NOT_EQUALS -> "≠"
        Comparison.LESS_THAN -> "<"
        Comparison.LESS_OR_EQUAL -> "≤"
        Comparison.GREATER_THAN -> ">"
        Comparison.GREATER_OR_EQUAL -> "≥"
        Comparison.CONTAINS -> s(R.string.comparison_contains)
        Comparison.MATCHES_REGEX -> s(R.string.comparison_matches)
    }

    fun stream(stream: AudioStream): String = s(
        when (stream) {
            AudioStream.MEDIA -> R.string.stream_media
            AudioStream.RING -> R.string.stream_ring
            AudioStream.NOTIFICATION -> R.string.stream_notification
            AudioStream.ALARM -> R.string.stream_alarm
            AudioStream.VOICE_CALL -> R.string.stream_call
        },
    )

    fun panel(panel: SettingsPanel): String = s(
        when (panel) {
            SettingsPanel.WIFI -> R.string.panel_wifi
            SettingsPanel.BLUETOOTH -> R.string.panel_bluetooth
            SettingsPanel.INTERNET -> R.string.panel_internet
            SettingsPanel.VOLUME -> R.string.panel_volume
            SettingsPanel.DISPLAY -> R.string.panel_display
            SettingsPanel.BATTERY_SAVER -> R.string.panel_battery_saver
            SettingsPanel.AIRPLANE_MODE -> R.string.panel_airplane
            SettingsPanel.LOCATION -> R.string.panel_location
        },
    )

    fun sound(sound: SoundType): String = s(
        when (sound) {
            SoundType.NOTIFICATION -> R.string.sound_notification
            SoundType.ALARM -> R.string.sound_alarm
            SoundType.RINGTONE -> R.string.sound_ringtone
        },
    )

    fun priority(priority: NotificationPriority): String = s(
        when (priority) {
            NotificationPriority.LOW -> R.string.priority_low
            NotificationPriority.DEFAULT -> R.string.priority_default
            NotificationPriority.HIGH -> R.string.priority_high
        },
    )

    fun headphoneKind(kind: HeadphoneKind): String = s(
        when (kind) {
            HeadphoneKind.ANY -> R.string.headphones_any
            HeadphoneKind.WIRED -> R.string.headphones_wired
            HeadphoneKind.BLUETOOTH -> R.string.headphones_bluetooth
        },
    )

    fun duration(ms: Long): String = when {
        ms >= 3_600_000 && ms % 3_600_000 == 0L -> s(R.string.duration_hours, ms / 3_600_000)
        ms >= 60_000 && ms % 60_000 == 0L -> s(R.string.duration_minutes, ms / 60_000)
        ms >= 1000 && ms % 1000 == 0L -> s(R.string.duration_seconds, ms / 1000)
        else -> s(R.string.duration_ms, ms)
    }

    companion object {
        val TRIGGER_TITLES = mapOf(
            "TIME" to R.string.trigger_time,
            "INTERVAL" to R.string.trigger_interval,
            "WIFI_CONNECTED" to R.string.trigger_wifi_connected,
            "WIFI_DISCONNECTED" to R.string.trigger_wifi_disconnected,
            "BLUETOOTH_CONNECTED" to R.string.trigger_bluetooth_connected,
            "BLUETOOTH_DISCONNECTED" to R.string.trigger_bluetooth_disconnected,
            "BATTERY_LEVEL" to R.string.trigger_battery_level,
            "CHARGER_CONNECTED" to R.string.trigger_charger_connected,
            "CHARGER_DISCONNECTED" to R.string.trigger_charger_disconnected,
            "APP_OPENED" to R.string.trigger_app_opened,
            "HEADPHONES_CONNECTED" to R.string.trigger_headphones_connected,
            "HEADPHONES_DISCONNECTED" to R.string.trigger_headphones_disconnected,
            "LOCATION_ENTER" to R.string.trigger_location_enter,
            "LOCATION_EXIT" to R.string.trigger_location_exit,
            "NOTIFICATION_RECEIVED" to R.string.trigger_notification,
            "CALENDAR_EVENT_START" to R.string.trigger_calendar_start,
            "CALENDAR_EVENT_END" to R.string.trigger_calendar_end,
        )

        val EVENT_TITLES = TRIGGER_TITLES + mapOf(
            "MANUAL" to R.string.event_manual,
            "BATTERY_CHANGED" to R.string.trigger_battery_level,
        )

        val CONDITION_TITLES = mapOf(
            "AND" to R.string.logic_all,
            "OR" to R.string.logic_any,
            "NOT" to R.string.logic_not,
            "TIME_RANGE" to R.string.condition_time_range,
            "DAYS_OF_WEEK" to R.string.condition_days,
            "BATTERY_LEVEL" to R.string.condition_battery,
            "CHARGING" to R.string.condition_charging,
            "WIFI_STATE" to R.string.condition_wifi,
            "BLUETOOTH_STATE" to R.string.condition_bluetooth,
            "HEADPHONES_STATE" to R.string.condition_headphones,
            "VARIABLE" to R.string.condition_variable,
            "IF_ELSE" to R.string.action_if_else,
        )

        val ACTION_TITLES = mapOf(
            "NOTIFICATION" to R.string.action_notification,
            "LAUNCH_APP" to R.string.action_launch_app,
            "OPEN_URL" to R.string.action_open_url,
            "OPEN_SETTINGS" to R.string.action_open_settings,
            "SET_BRIGHTNESS" to R.string.action_brightness,
            "SET_VOLUME" to R.string.action_volume,
            "DO_NOT_DISTURB" to R.string.action_dnd,
            "SPEAK" to R.string.action_speak,
            "PLAY_SOUND" to R.string.action_play_sound,
            "VIBRATE" to R.string.action_vibrate,
            "HTTP_REQUEST" to R.string.action_http,
            "DELAY" to R.string.action_delay,
            "SET_VARIABLE" to R.string.action_set_variable,
            "IF_ELSE" to R.string.action_if_else,
            "REPEAT" to R.string.action_repeat,
            "DISMISS_NOTIFICATIONS" to R.string.action_dismiss_notifications,
        )

        val INFO_TITLES = mapOf(
            "CONDITIONS_MET" to R.string.info_conditions_met,
            "CONDITIONS_NOT_MET" to R.string.info_conditions_not_met,
            "ALREADY_RUNNING" to R.string.info_already_running,
            "ABORTED" to R.string.info_aborted,
            "CANCELLED" to R.string.info_cancelled,
        )
    }
}

@Composable
fun rememberSpecFormatter(): SpecFormatter {
    val context = LocalContext.current
    return remember(context) { SpecFormatter(context.resources) }
}
