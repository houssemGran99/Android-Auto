package com.autoflow.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.autoflow.app.R
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.AudioStream
import com.autoflow.core.model.Automation
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.HeadphoneKind
import com.autoflow.core.model.HttpMethod
import com.autoflow.core.model.NotificationPriority
import com.autoflow.core.model.SettingsPanel
import com.autoflow.core.model.ThresholdDirection
import com.autoflow.core.model.TimeOfDay
import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Weekday
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationTemplate(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val build: (Context) -> Automation,
)

/** Ready-made automations; the user reviews and saves them in the builder. */
@Singleton
class TemplateCatalog @Inject constructor(@ApplicationContext private val context: Context) {

    val templates: List<AutomationTemplate> = listOf(
        AutomationTemplate("music_mode", R.string.template_music_title, R.string.template_music_description) { ctx ->
            automation(
                ctx,
                R.string.template_music_title,
                R.string.template_music_description,
                triggers = listOf(TriggerSpec.HeadphonesConnected(HeadphoneKind.ANY)),
                condition = ConditionNode.And(listOf(ConditionNode.TimeRange(TimeOfDay(7, 0), TimeOfDay(22, 0)))),
                actions = listOf(
                    ActionSpec.SetVolume(AudioStream.MEDIA, 70),
                    ActionSpec.LaunchApp(SPOTIFY, "Spotify"),
                    ActionSpec.ShowNotification(
                        ctx.getString(R.string.app_name),
                        ctx.getString(R.string.template_music_notification),
                    ),
                ),
            )
        },
        AutomationTemplate("night_mode", R.string.template_night_title, R.string.template_night_description) { ctx ->
            automation(
                ctx,
                R.string.template_night_title,
                R.string.template_night_description,
                triggers = listOf(TriggerSpec.Time(TimeOfDay(23, 0))),
                actions = listOf(
                    ActionSpec.SetBrightness(10),
                    ActionSpec.SetVolume(AudioStream.RING, 20),
                    ActionSpec.SetVolume(AudioStream.MEDIA, 20),
                    ActionSpec.SetDoNotDisturb(true),
                ),
            )
        },
        AutomationTemplate("morning", R.string.template_morning_title, R.string.template_morning_description) { ctx ->
            automation(
                ctx,
                R.string.template_morning_title,
                R.string.template_morning_description,
                triggers = listOf(TriggerSpec.Time(TimeOfDay(7, 30), Weekday.WORKDAYS)),
                actions = listOf(
                    ActionSpec.SetDoNotDisturb(false),
                    ActionSpec.SetVolume(AudioStream.RING, 70),
                    ActionSpec.Speak(ctx.getString(R.string.template_morning_speech)),
                ),
            )
        },
        AutomationTemplate("work_mode", R.string.template_work_title, R.string.template_work_description) { ctx ->
            automation(
                ctx,
                R.string.template_work_title,
                R.string.template_work_description,
                triggers = listOf(TriggerSpec.WifiConnected(ctx.getString(R.string.template_work_ssid))),
                condition = ConditionNode.And(
                    listOf(
                        ConditionNode.DaysOfWeek(Weekday.WORKDAYS),
                        ConditionNode.TimeRange(TimeOfDay(8, 0), TimeOfDay(18, 0)),
                    ),
                ),
                actions = listOf(
                    ActionSpec.SetVolume(AudioStream.RING, 30),
                    ActionSpec.SetBrightness(50),
                    ActionSpec.LaunchApp(TEAMS, "Teams"),
                    ActionSpec.OpenSettings(SettingsPanel.BLUETOOTH),
                ),
            )
        },
        AutomationTemplate("battery_saver", R.string.template_battery_title, R.string.template_battery_description) { ctx ->
            automation(
                ctx,
                R.string.template_battery_title,
                R.string.template_battery_description,
                triggers = listOf(TriggerSpec.BatteryLevel(20, ThresholdDirection.BELOW)),
                actions = listOf(
                    ActionSpec.SetBrightness(30),
                    ActionSpec.ShowNotification(
                        ctx.getString(R.string.template_battery_title),
                        ctx.getString(R.string.template_battery_notification),
                        NotificationPriority.HIGH,
                    ),
                    ActionSpec.OpenSettings(SettingsPanel.BATTERY_SAVER),
                ),
            )
        },
        AutomationTemplate("charging", R.string.template_charging_title, R.string.template_charging_description) { ctx ->
            automation(
                ctx,
                R.string.template_charging_title,
                R.string.template_charging_description,
                triggers = listOf(TriggerSpec.ChargerConnected),
                condition = ConditionNode.And(listOf(ConditionNode.TimeRange(TimeOfDay(22, 0), TimeOfDay(7, 0)))),
                actions = listOf(
                    ActionSpec.SetDoNotDisturb(true),
                    ActionSpec.SetBrightness(5),
                ),
            )
        },
        AutomationTemplate("meeting_silence", R.string.template_meeting_title, R.string.template_meeting_description) { ctx ->
            automation(
                ctx,
                R.string.template_meeting_title,
                R.string.template_meeting_description,
                triggers = listOf(TriggerSpec.CalendarEventStart(), TriggerSpec.CalendarEventEnd()),
                actions = listOf(
                    // One automation for both edges: %trigger% tells whether the event started or ended.
                    ActionSpec.IfElse(
                        condition = ConditionNode.VariableCompare("trigger", Comparison.EQUALS, "CALENDAR_EVENT_START"),
                        thenActions = listOf(ActionSpec.SetDoNotDisturb(true)),
                        elseActions = listOf(ActionSpec.SetDoNotDisturb(false)),
                    ),
                ),
            )
        },
        AutomationTemplate("read_messages", R.string.template_read_messages_title, R.string.template_read_messages_description) { ctx ->
            automation(
                ctx,
                R.string.template_read_messages_title,
                R.string.template_read_messages_description,
                triggers = listOf(TriggerSpec.NotificationReceived(WHATSAPP, "WhatsApp")),
                condition = ConditionNode.And(listOf(ConditionNode.HeadphonesState(connected = true))),
                actions = listOf(ActionSpec.Speak(ctx.getString(R.string.template_read_messages_speech))),
            )
        },
        AutomationTemplate("http_report", R.string.template_http_title, R.string.template_http_description) { ctx ->
            automation(
                ctx,
                R.string.template_http_title,
                R.string.template_http_description,
                triggers = listOf(TriggerSpec.Interval(60)),
                actions = listOf(
                    ActionSpec.HttpRequest(
                        method = HttpMethod.POST,
                        url = "https://example.com/api/device",
                        body = "{\n  \"battery\": \"%battery%\",\n  \"charging\": \"%charging%\",\n  \"time\": \"%datetime%\"\n}",
                    ),
                ),
            )
        },
    )

    fun find(id: String): AutomationTemplate? = templates.firstOrNull { it.id == id }

    fun instantiate(id: String): Automation? = find(id)?.build?.invoke(context)

    private fun automation(
        ctx: Context,
        @StringRes title: Int,
        @StringRes description: Int,
        triggers: List<TriggerSpec>,
        condition: ConditionNode? = null,
        actions: List<ActionSpec>,
    ) = Automation(
        id = UUID.randomUUID().toString(),
        name = ctx.getString(title),
        description = ctx.getString(description),
        triggers = triggers,
        condition = condition,
        actions = actions,
    )

    private companion object {
        const val SPOTIFY = "com.spotify.music"
        const val TEAMS = "com.microsoft.teams"
        const val WHATSAPP = "com.whatsapp"
    }
}
