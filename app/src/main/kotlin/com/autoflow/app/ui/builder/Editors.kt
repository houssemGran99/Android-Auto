package com.autoflow.app.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.ui.text.SpecFormatter
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.HttpAuth
import com.autoflow.core.model.HttpMethod
import com.autoflow.core.model.NotificationPriority
import com.autoflow.core.model.SettingsPanel
import com.autoflow.core.model.SoundType
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Variable
import com.autoflow.core.model.VariableScope
import com.autoflow.platform.triggers.source.WifiTriggerSource

/** Dialog hosting a type-specific form. The value is only applied when confirmed and valid. */
@Composable
fun <T> SpecEditorDialog(
    title: String,
    initial: T,
    isValid: (T) -> Boolean,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
    form: @Composable (value: T, onChange: (T) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                form(value) { value = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = isValid(value)) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// region Triggers

fun isValidTrigger(spec: TriggerSpec): Boolean = when (spec) {
    is TriggerSpec.AppOpened -> spec.packageName.isNotBlank()
    is TriggerSpec.LocationEnter -> isValidPlace(spec.place)
    is TriggerSpec.LocationExit -> isValidPlace(spec.place)
    else -> true
}

@Composable
fun TriggerForm(spec: TriggerSpec, onChange: (TriggerSpec) -> Unit) {
    val f = rememberSpecFormatter()
    when (spec) {
        is TriggerSpec.Time -> {
            TimeField(stringResource(R.string.field_at), spec.at) { onChange(spec.copy(at = it)) }
            DaysSelector(spec.days, f) { onChange(spec.copy(days = it)) }
            Hint(stringResource(R.string.hint_exact_alarm))
        }
        is TriggerSpec.Interval -> {
            NumberField(stringResource(R.string.field_every_minutes), spec.everyMinutes.toLong(), { onChange(TriggerSpec.Interval(it.toInt())) }, 1L..1440L)
            Hint(stringResource(R.string.hint_interval))
        }
        is TriggerSpec.WifiConnected -> SsidField(spec.ssid) { onChange(spec.copy(ssid = it)) }
        is TriggerSpec.WifiDisconnected -> SsidField(spec.ssid) { onChange(spec.copy(ssid = it)) }
        is TriggerSpec.BluetoothConnected -> DeviceField(spec.deviceName) { onChange(spec.copy(deviceName = it)) }
        is TriggerSpec.BluetoothDisconnected -> DeviceField(spec.deviceName) { onChange(spec.copy(deviceName = it)) }
        is TriggerSpec.BatteryLevel -> {
            SegmentedChoice(ThresholdDirection.entries, spec.direction, {
                if (it == ThresholdDirection.BELOW) f.comparison(Comparison.LESS_THAN) else f.comparison(Comparison.GREATER_THAN)
            }) { onChange(spec.copy(direction = it)) }
            PercentSlider(stringResource(R.string.field_threshold), spec.threshold, { onChange(spec.copy(threshold = it)) }, 1..99)
            Hint(stringResource(R.string.hint_battery_trigger))
        }
        TriggerSpec.ChargerConnected, TriggerSpec.ChargerDisconnected -> Unit
        is TriggerSpec.AppOpened -> {
            AppPickerField(spec.packageName, spec.appLabel) { onChange(spec.copy(packageName = it.packageName, appLabel = it.label)) }
            Hint(stringResource(R.string.hint_app_opened))
        }
        is TriggerSpec.HeadphonesConnected ->
            SegmentedChoice(HeadphoneKind.entries, spec.kind, f::headphoneKind) { onChange(spec.copy(kind = it)) }
        is TriggerSpec.HeadphonesDisconnected ->
            SegmentedChoice(HeadphoneKind.entries, spec.kind, f::headphoneKind) { onChange(spec.copy(kind = it)) }
        is TriggerSpec.NotificationReceived -> {
            OptionalAppField(spec.packageName, spec.appLabel) { pkg, label -> onChange(spec.copy(packageName = pkg, appLabel = label)) }
            TextInput(
                stringResource(R.string.field_text_contains),
                spec.textContains.orEmpty(),
                { onChange(spec.copy(textContains = it.ifEmpty { null })) },
                supporting = stringResource(R.string.hint_notification_trigger),
            )
        }
        is TriggerSpec.CalendarEventStart -> CalendarFilterField(spec.titleContains) { onChange(spec.copy(titleContains = it)) }
        is TriggerSpec.CalendarEventEnd -> CalendarFilterField(spec.titleContains) { onChange(spec.copy(titleContains = it)) }
        is TriggerSpec.LocationEnter -> PlaceEditor(spec.place) { onChange(spec.copy(place = it)) }
        is TriggerSpec.LocationExit -> PlaceEditor(spec.place) { onChange(spec.copy(place = it)) }
    }
}

@Composable
private fun SsidField(ssid: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    TextInput(
        label = stringResource(R.string.field_ssid),
        value = ssid.orEmpty(),
        onValueChange = { onChange(it.ifBlank { null }) },
        supporting = stringResource(R.string.hint_ssid),
    )
    var notFound by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val current = WifiTriggerSource.currentSsid(context)
        notFound = current == null
        if (current != null) onChange(current)
    }) { Text(stringResource(R.string.use_current_network)) }
    if (notFound) Hint(stringResource(R.string.current_network_unknown))
}

@Composable
private fun DeviceField(name: String?, onChange: (String?) -> Unit) {
    TextInput(
        label = stringResource(R.string.field_device_name),
        value = name.orEmpty(),
        onValueChange = { onChange(it.ifBlank { null }) },
        supporting = stringResource(R.string.hint_device_name),
    )
}
// endregion

// region Conditions

fun isValidCondition(node: ConditionNode): Boolean = when (node) {
    is ConditionNode.VariableCompare -> node.variable.isNotBlank()
    else -> true
}

@Composable
fun ConditionForm(node: ConditionNode, onChange: (ConditionNode) -> Unit) {
    val f = rememberSpecFormatter()
    when (node) {
        is ConditionNode.TimeRange -> {
            TimeField(stringResource(R.string.field_from), node.start) { onChange(node.copy(start = it)) }
            TimeField(stringResource(R.string.field_to), node.end) { onChange(node.copy(end = it)) }
            Hint(stringResource(R.string.hint_time_range))
        }
        is ConditionNode.DaysOfWeek -> DaysSelector(node.days, f) { onChange(node.copy(days = it)) }
        is ConditionNode.BatteryLevel -> {
            ComparisonField(f, NUMERIC_COMPARISONS, node.comparison) { onChange(node.copy(comparison = it)) }
            PercentSlider(stringResource(R.string.field_level), node.value, { onChange(node.copy(value = it)) })
        }
        is ConditionNode.Charging ->
            LabeledSwitch(stringResource(R.string.state_charging), node.charging, { onChange(node.copy(charging = it)) })
        is ConditionNode.WifiState -> {
            LabeledSwitch(stringResource(R.string.state_connected), node.connected, { onChange(node.copy(connected = it)) })
            SsidField(node.ssid) { onChange(node.copy(ssid = it)) }
        }
        is ConditionNode.BluetoothState ->
            LabeledSwitch(stringResource(R.string.field_bluetooth_on), node.enabled, { onChange(node.copy(enabled = it)) })
        is ConditionNode.HeadphonesState ->
            LabeledSwitch(stringResource(R.string.state_connected), node.connected, { onChange(node.copy(connected = it)) })
        is ConditionNode.VariableCompare -> {
            TextInput(
                stringResource(R.string.field_variable),
                node.variable,
                { onChange(node.copy(variable = it.trim())) },
                supporting = stringResource(R.string.hint_variable_condition),
            )
            ComparisonField(f, Comparison.entries, node.comparison) { onChange(node.copy(comparison = it)) }
            TextInput(stringResource(R.string.field_value), node.value, { onChange(node.copy(value = it)) })
        }
        is ConditionNode.And, is ConditionNode.Or, is ConditionNode.Not -> Unit
    }
}

private val NUMERIC_COMPARISONS = listOf(
    Comparison.LESS_THAN,
    Comparison.LESS_OR_EQUAL,
    Comparison.EQUALS,
    Comparison.GREATER_OR_EQUAL,
    Comparison.GREATER_THAN,
)

@Composable
private fun ComparisonField(f: SpecFormatter, options: List<Comparison>, selected: Comparison, onSelect: (Comparison) -> Unit) {
    DropdownField(stringResource(R.string.field_comparison), options, selected, f::comparison, onSelect)
}
// endregion

// region Actions

fun isValidAction(spec: ActionSpec): Boolean = when (spec) {
    is ActionSpec.ShowNotification -> spec.title.isNotBlank() || spec.message.isNotBlank()
    is ActionSpec.LaunchApp -> spec.packageName.isNotBlank()
    is ActionSpec.OpenUrl -> spec.url.length > "https://".length
    is ActionSpec.HttpRequest -> spec.url.startsWith("http://") || spec.url.startsWith("https://")
    is ActionSpec.Speak -> spec.text.isNotBlank()
    is ActionSpec.SetVariable -> Variable.isValidName(spec.name)
    else -> true
}

@Composable
fun ActionForm(spec: ActionSpec, onChange: (ActionSpec) -> Unit) {
    val f = rememberSpecFormatter()
    when (spec) {
        is ActionSpec.ShowNotification -> {
            TextInput(stringResource(R.string.field_title), spec.title, { onChange(spec.copy(title = it)) })
            TextInput(
                stringResource(R.string.field_message),
                spec.message,
                { onChange(spec.copy(message = it)) },
                singleLine = false,
                supporting = stringResource(R.string.hint_placeholders),
            )
            SegmentedChoice(NotificationPriority.entries, spec.priority, f::priority) { onChange(spec.copy(priority = it)) }
        }
        is ActionSpec.LaunchApp -> {
            AppPickerField(spec.packageName, spec.appLabel) { onChange(spec.copy(packageName = it.packageName, appLabel = it.label)) }
            Hint(stringResource(R.string.hint_background_launch))
        }
        is ActionSpec.OpenUrl -> {
            TextInput(stringResource(R.string.field_url), spec.url, { onChange(spec.copy(url = it.trim())) }, keyboardType = KeyboardType.Uri)
            Hint(stringResource(R.string.hint_background_launch))
        }
        is ActionSpec.OpenSettings -> {
            DropdownField(stringResource(R.string.field_panel), SettingsPanel.entries, spec.panel, f::panel) { onChange(spec.copy(panel = it)) }
            Hint(stringResource(R.string.hint_settings_panel))
        }
        is ActionSpec.SetBrightness -> {
            PercentSlider(stringResource(R.string.field_brightness), spec.percent, { onChange(spec.copy(percent = it)) })
            Hint(stringResource(R.string.hint_brightness))
        }
        is ActionSpec.SetVolume -> {
            DropdownField(stringResource(R.string.field_stream), AudioStream.entries, spec.stream, f::stream) { onChange(spec.copy(stream = it)) }
            PercentSlider(stringResource(R.string.field_volume), spec.percent, { onChange(spec.copy(percent = it)) })
        }
        is ActionSpec.SetDoNotDisturb ->
            LabeledSwitch(stringResource(R.string.action_dnd), spec.enabled, { onChange(spec.copy(enabled = it)) })
        is ActionSpec.Speak -> TextInput(
            stringResource(R.string.field_text),
            spec.text,
            { onChange(spec.copy(text = it)) },
            singleLine = false,
            supporting = stringResource(R.string.hint_placeholders),
        )
        is ActionSpec.PlaySound -> {
            SegmentedChoice(SoundType.entries, spec.sound, f::sound) { onChange(spec.copy(sound = it)) }
            PercentSlider(
                stringResource(R.string.field_max_duration),
                spec.maxDurationSeconds,
                { onChange(spec.copy(maxDurationSeconds = it)) },
                1..60,
                suffix = " s",
            )
        }
        is ActionSpec.Vibrate -> PercentSlider(
            stringResource(R.string.field_duration),
            spec.durationMs.toInt(),
            { onChange(spec.copy(durationMs = it.toLong())) },
            50..5000,
            suffix = " ms",
        )
        is ActionSpec.HttpRequest -> HttpForm(spec, onChange)
        is ActionSpec.Delay -> {
            NumberField(
                stringResource(R.string.field_seconds),
                spec.durationMs / 1000,
                { onChange(ActionSpec.Delay(it * 1000)) },
                0L..86_400L,
            )
            Hint(stringResource(R.string.hint_delay))
        }
        is ActionSpec.SetVariable -> {
            val globalLabel = stringResource(R.string.scope_global)
            val localLabel = stringResource(R.string.scope_local)
            TextInput(
                stringResource(R.string.field_name),
                spec.name,
                { onChange(spec.copy(name = it.trim().removePrefix("$"))) },
                isError = spec.name.isNotEmpty() && !Variable.isValidName(spec.name),
                supporting = stringResource(R.string.hint_variable_name),
            )
            TextInput(
                stringResource(R.string.field_value),
                spec.value,
                { onChange(spec.copy(value = it)) },
                supporting = stringResource(R.string.hint_variable_value),
            )
            LabeledSwitch(stringResource(R.string.field_math), spec.evaluateMath, { onChange(spec.copy(evaluateMath = it)) })
            SegmentedChoice(
                VariableScope.entries,
                spec.scope,
                { if (it == VariableScope.GLOBAL) globalLabel else localLabel },
            ) { onChange(spec.copy(scope = it)) }
        }
        is ActionSpec.IfElse -> {
            Hint(stringResource(R.string.hint_if_else))
            ConditionTreeEditor(
                root = ConditionTree.asEditableRoot(spec.condition),
                onChange = { onChange(spec.copy(condition = it)) },
            )
        }
        is ActionSpec.Repeat -> NumberField(
            stringResource(R.string.field_times),
            spec.times.toLong(),
            { onChange(spec.copy(times = it.toInt())) },
            1L..ActionSpec.Repeat.MAX_REPEAT.toLong(),
        )
        is ActionSpec.DismissNotifications -> {
            OptionalAppField(spec.packageName, spec.appLabel) { pkg, label -> onChange(spec.copy(packageName = pkg, appLabel = label)) }
            TextInput(
                stringResource(R.string.field_text_contains),
                spec.textContains.orEmpty(),
                { onChange(spec.copy(textContains = it.ifEmpty { null })) },
                supporting = stringResource(R.string.hint_dismiss_notifications),
            )
        }
    }
}

/** App picker with an "any app" option (null package). */
@Composable
private fun OptionalAppField(packageName: String?, label: String, onChange: (String?, String) -> Unit) {
    AppPickerField(packageName.orEmpty(), label.ifBlank { stringResource(R.string.any_app) }) {
        onChange(it.packageName, it.label)
    }
    if (!packageName.isNullOrBlank()) {
        TextButton(onClick = { onChange(null, "") }) { Text(stringResource(R.string.use_any_app)) }
    } else {
        Hint(stringResource(R.string.any_app_selected))
    }
}

@Composable
private fun CalendarFilterField(titleContains: String?, onChange: (String?) -> Unit) {
    TextInput(
        stringResource(R.string.field_title_contains),
        titleContains.orEmpty(),
        { onChange(it.ifEmpty { null }) },
        supporting = stringResource(R.string.hint_calendar_trigger),
    )
}

private enum class AuthType { NONE, BASIC, BEARER }

@Composable
private fun HttpForm(spec: ActionSpec.HttpRequest, onChange: (ActionSpec) -> Unit) {
    DropdownField(stringResource(R.string.field_method), HttpMethod.entries, spec.method, { it.name }) { onChange(spec.copy(method = it)) }
    TextInput(stringResource(R.string.field_url), spec.url, { onChange(spec.copy(url = it.trim())) }, keyboardType = KeyboardType.Uri)
    // Raw text is kept locally so partially typed lines are not dropped while editing.
    var headersText by remember { mutableStateOf(spec.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var queryText by remember { mutableStateOf(spec.queryParameters.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    TextInput(
        stringResource(R.string.field_headers),
        headersText,
        {
            headersText = it
            onChange(spec.copy(headers = parsePairs(it, ':')))
        },
        singleLine = false,
        supporting = stringResource(R.string.hint_headers),
    )
    TextInput(
        stringResource(R.string.field_query),
        queryText,
        {
            queryText = it
            onChange(spec.copy(queryParameters = parsePairs(it, '=')))
        },
        singleLine = false,
        supporting = stringResource(R.string.hint_query),
    )
    if (spec.method != HttpMethod.GET) {
        TextInput(
            stringResource(R.string.field_body),
            spec.body.orEmpty(),
            { onChange(spec.copy(body = it.ifEmpty { null })) },
            singleLine = false,
            supporting = stringResource(R.string.hint_placeholders),
        )
        TextInput(stringResource(R.string.field_content_type), spec.contentType, { onChange(spec.copy(contentType = it.trim())) })
    }
    val authType = when (spec.auth) {
        is HttpAuth.Basic -> AuthType.BASIC
        is HttpAuth.Bearer -> AuthType.BEARER
        null -> AuthType.NONE
    }
    val authLabels = mapOf(
        AuthType.NONE to stringResource(R.string.auth_none),
        AuthType.BASIC to stringResource(R.string.auth_basic),
        AuthType.BEARER to stringResource(R.string.auth_bearer),
    )
    SegmentedChoice(AuthType.entries, authType, { authLabels.getValue(it) }) {
        onChange(
            spec.copy(
                auth = when (it) {
                    AuthType.NONE -> null
                    AuthType.BASIC -> HttpAuth.Basic("", "")
                    AuthType.BEARER -> HttpAuth.Bearer("")
                },
            ),
        )
    }
    when (val auth = spec.auth) {
        is HttpAuth.Basic -> {
            TextInput(stringResource(R.string.field_username), auth.username, { onChange(spec.copy(auth = auth.copy(username = it))) })
            TextInput(
                stringResource(R.string.field_password),
                auth.password,
                { onChange(spec.copy(auth = auth.copy(password = it))) },
                supporting = stringResource(R.string.hint_secret_variable),
            )
        }
        is HttpAuth.Bearer -> TextInput(
            stringResource(R.string.field_token),
            auth.token,
            { onChange(spec.copy(auth = auth.copy(token = it))) },
            supporting = stringResource(R.string.hint_secret_variable),
        )
        null -> Unit
    }
    NumberField(stringResource(R.string.field_timeout), spec.timeoutSeconds.toLong(), { onChange(spec.copy(timeoutSeconds = it.toInt())) }, 1L..300L)
    TextInput(
        stringResource(R.string.field_response_variable),
        spec.responseVariable,
        { onChange(spec.copy(responseVariable = it.trim().removePrefix("$"))) },
        supporting = stringResource(R.string.hint_response_variable, spec.responseVariable.ifBlank { "http" }),
    )
}

/** Parses "key: value" (or "key=value") lines; lines without a separator are ignored. */
internal fun parsePairs(text: String, separator: Char): Map<String, String> =
    text.lines().mapNotNull { line ->
        val index = line.indexOf(separator)
        if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
    }.toMap(LinkedHashMap())
// endregion

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
