package com.autoflow.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationPriority { LOW, DEFAULT, HIGH }

@Serializable
enum class AudioStream { MEDIA, RING, NOTIFICATION, ALARM, VOICE_CALL }

@Serializable
enum class SoundType { NOTIFICATION, ALARM, RINGTONE }

@Serializable
enum class HttpMethod { GET, POST, PUT, DELETE, PATCH }

@Serializable
enum class VariableScope {
    /** Persisted, visible to every automation. */
    GLOBAL,

    /** Only lives for the current execution. */
    LOCAL,
}

/**
 * System settings screens. Android does not allow regular apps to toggle Wi-Fi,
 * Bluetooth, airplane mode or battery saver directly, so automations open the
 * relevant panel instead.
 */
@Serializable
enum class SettingsPanel { WIFI, BLUETOOTH, INTERNET, VOLUME, DISPLAY, BATTERY_SAVER, AIRPLANE_MODE, LOCATION }

/** Operations of the "Variable operation" action. Arguments are placeholders-resolved text. */
@Serializable
enum class VariableOp {
    /** value + argument1 (default 1). */
    INCREMENT,

    /** value - argument1 (default 1). */
    DECREMENT,

    /** value followed by argument1. */
    APPEND,

    /** Replaces every argument1 with argument2. */
    REPLACE,
    UPPERCASE,
    LOWERCASE,
    TRIM,

    /** Characters from argument1 (0-based, inclusive) to argument2 (exclusive, optional). */
    SUBSTRING,

    /** Splits by argument1 and keeps the part at index argument2 (0-based). */
    SPLIT,

    /** First match of regex argument1; group argument2 (default 1 if the regex has groups, else 0). */
    REGEX_EXTRACT,
    LENGTH,
    URL_ENCODE,
}

@Serializable
sealed interface HttpAuth {
    @Serializable
    @SerialName("BASIC")
    data class Basic(val username: String, val password: String) : HttpAuth

    @Serializable
    @SerialName("BEARER")
    data class Bearer(val token: String) : HttpAuth
}

/**
 * Configuration of a single step in an automation. Text fields support
 * variable placeholders like `%battery%` (built-in) or `$userName` (user).
 */
@Serializable
sealed interface ActionSpec {
    val capabilities: Set<Capability> get() = emptySet()

    /** Nested actions (for control-flow actions). */
    val children: List<ActionSpec> get() = emptyList()

    @Serializable
    @SerialName("NOTIFICATION")
    data class ShowNotification(
        val title: String,
        val message: String,
        val priority: NotificationPriority = NotificationPriority.DEFAULT,
    ) : ActionSpec {
        override val capabilities get() = setOf(Capability.NOTIFICATIONS)
    }

    @Serializable
    @SerialName("LAUNCH_APP")
    data class LaunchApp(
        val packageName: String,
        val appLabel: String = "",
    ) : ActionSpec {
        override val capabilities get() = setOf(Capability.BACKGROUND_ACTIVITY_START, Capability.NOTIFICATIONS)
    }

    @Serializable
    @SerialName("OPEN_URL")
    data class OpenUrl(val url: String) : ActionSpec {
        override val capabilities get() = setOf(Capability.BACKGROUND_ACTIVITY_START, Capability.NOTIFICATIONS)
    }

    @Serializable
    @SerialName("OPEN_SETTINGS")
    data class OpenSettings(val panel: SettingsPanel) : ActionSpec {
        override val capabilities get() = setOf(Capability.BACKGROUND_ACTIVITY_START, Capability.NOTIFICATIONS)
    }

    /** Dismisses other apps' notifications; null filters match everything that can be dismissed. */
    @Serializable
    @SerialName("DISMISS_NOTIFICATIONS")
    data class DismissNotifications(
        val packageName: String? = null,
        val appLabel: String = "",
        val textContains: String? = null,
    ) : ActionSpec {
        override val capabilities get() = setOf(Capability.NOTIFICATION_LISTENER)
    }

    @Serializable
    @SerialName("SET_BRIGHTNESS")
    data class SetBrightness(val percent: Int) : ActionSpec {
        init {
            require(percent in 0..100) { "Brightness must be in 0..100" }
        }

        override val capabilities get() = setOf(Capability.WRITE_SETTINGS)
    }

    @Serializable
    @SerialName("SET_VOLUME")
    data class SetVolume(
        val stream: AudioStream = AudioStream.MEDIA,
        val percent: Int,
    ) : ActionSpec {
        init {
            require(percent in 0..100) { "Volume must be in 0..100" }
        }
    }

    @Serializable
    @SerialName("DO_NOT_DISTURB")
    data class SetDoNotDisturb(val enabled: Boolean) : ActionSpec {
        override val capabilities get() = setOf(Capability.NOTIFICATION_POLICY)
    }

    @Serializable
    @SerialName("SPEAK")
    data class Speak(val text: String) : ActionSpec

    @Serializable
    @SerialName("PLAY_SOUND")
    data class PlaySound(
        val sound: SoundType = SoundType.NOTIFICATION,
        val maxDurationSeconds: Int = 10,
    ) : ActionSpec

    @Serializable
    @SerialName("VIBRATE")
    data class Vibrate(val durationMs: Long = 500) : ActionSpec

    @Serializable
    @SerialName("HTTP_REQUEST")
    data class HttpRequest(
        val method: HttpMethod = HttpMethod.GET,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val queryParameters: Map<String, String> = emptyMap(),
        val body: String? = null,
        val contentType: String = "application/json",
        val auth: HttpAuth? = null,
        val timeoutSeconds: Int = 15,
        /** Response is stored in local variables `<name>` (body) and `<name>_status`. */
        val responseVariable: String = "http",
    ) : ActionSpec

    @Serializable
    @SerialName("DELAY")
    data class Delay(val durationMs: Long) : ActionSpec {
        init {
            require(durationMs >= 0) { "Delay must not be negative" }
        }
    }

    @Serializable
    @SerialName("SET_VARIABLE")
    data class SetVariable(
        val name: String,
        val value: String,
        /** When true, [value] is evaluated as an arithmetic expression after placeholder substitution. */
        val evaluateMath: Boolean = false,
        val scope: VariableScope = VariableScope.GLOBAL,
    ) : ActionSpec

    @Serializable
    @SerialName("IF_ELSE")
    data class IfElse(
        val condition: ConditionNode,
        val thenActions: List<ActionSpec>,
        val elseActions: List<ActionSpec> = emptyList(),
    ) : ActionSpec {
        override val capabilities: Set<Capability>
            get() = condition.capabilities + (thenActions + elseActions).flatMap { it.capabilities }

        override val children get() = thenActions + elseActions
    }

    /** Repeats [actions] while [condition] is true, at most [maxIterations] times (protects against endless loops). */
    @Serializable
    @SerialName("WHILE")
    data class While(
        val condition: ConditionNode,
        val actions: List<ActionSpec>,
        val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    ) : ActionSpec {
        init {
            require(maxIterations in 1..Repeat.MAX_REPEAT) { "Max iterations must be in 1..${Repeat.MAX_REPEAT}" }
        }

        override val capabilities: Set<Capability>
            get() = condition.capabilities + actions.flatMap { it.capabilities }

        override val children get() = actions

        companion object {
            const val DEFAULT_MAX_ITERATIONS = 100
        }
    }

    /** Pauses until [condition] is true, re-checking every [checkIntervalSeconds]; fails after [timeoutSeconds]. */
    @Serializable
    @SerialName("WAIT_UNTIL")
    data class WaitUntil(
        val condition: ConditionNode,
        val timeoutSeconds: Int = 300,
        val checkIntervalSeconds: Int = 10,
    ) : ActionSpec {
        init {
            require(timeoutSeconds in 1..MAX_TIMEOUT_SECONDS) { "Timeout must be in 1..$MAX_TIMEOUT_SECONDS seconds" }
            require(checkIntervalSeconds in 1..3600) { "Check interval must be in 1..3600 seconds" }
        }

        override val capabilities get() = condition.capabilities

        companion object {
            const val MAX_TIMEOUT_SECONDS = 24 * 3600
        }
    }

    /** Ends the run here; remaining actions are skipped and the run still counts as successful. */
    @Serializable
    @SerialName("STOP")
    data object Stop : ActionSpec

    /** Applies [operation] to variable [name] and stores the result in [target] (default: [name]). */
    @Serializable
    @SerialName("VARIABLE_OPERATION")
    data class VariableOperation(
        val name: String,
        val operation: VariableOp,
        val argument1: String = "",
        val argument2: String = "",
        val target: String? = null,
        val scope: VariableScope = VariableScope.GLOBAL,
    ) : ActionSpec

    /** Reads [path] (dot notation, e.g. `data.items.0.name`) from the JSON in [source] into [target]. */
    @Serializable
    @SerialName("PARSE_JSON")
    data class ParseJson(
        val source: String,
        val path: String,
        val target: String,
        val scope: VariableScope = VariableScope.LOCAL,
    ) : ActionSpec

    @Serializable
    @SerialName("REPEAT")
    data class Repeat(
        val times: Int,
        val actions: List<ActionSpec>,
    ) : ActionSpec {
        init {
            require(times in 1..MAX_REPEAT) { "Repeat count must be in 1..$MAX_REPEAT" }
        }

        override val capabilities: Set<Capability> get() = actions.flatMapTo(mutableSetOf()) { it.capabilities }
        override val children get() = actions

        companion object {
            const val MAX_REPEAT = 1000
        }
    }
}
