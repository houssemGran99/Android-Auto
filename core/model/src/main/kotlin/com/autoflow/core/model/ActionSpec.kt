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
