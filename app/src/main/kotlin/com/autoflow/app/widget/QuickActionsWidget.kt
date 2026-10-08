package com.autoflow.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.autoflow.app.MainActivity
import com.autoflow.app.R
import com.autoflow.app.runtime.AutomationRunWorker
import com.autoflow.core.engine.DeviceState
import com.autoflow.core.engine.DeviceStateProvider
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.Automation
import com.autoflow.data.storage.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun automations(): AutomationRepository
    fun settings(): SettingsRepository
    fun deviceState(): DeviceStateProvider
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/**
 * Home-screen widget: master switch, Wi-Fi / Bluetooth status and one button per automation
 * marked as "quick action". Android does not let apps toggle Wi-Fi or Bluetooth, so their rows
 * show the state and open the system panel on tap.
 */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val quickActions = entryPoint.automations().getAll().filter { it.quickAction }.take(MAX_BUTTONS)
        val masterEnabled = entryPoint.settings().settings.first().masterEnabled
        val state = runCatching { entryPoint.deviceState().snapshot() }.getOrDefault(DeviceState())
        provideContent {
            GlanceTheme {
                Content(context, masterEnabled, state, quickActions)
            }
        }
    }

    @Composable
    private fun Content(context: Context, masterEnabled: Boolean, state: DeviceState, quickActions: List<Automation>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.widget_title),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                )
                Text(
                    text = context.getString(R.string.widget_refresh),
                    style = TextStyle(color = GlanceTheme.colors.primary),
                    modifier = GlanceModifier.padding(horizontal = 8.dp).clickable(actionRunCallback<RefreshWidgetsCallback>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            StatusRow(
                context.getString(R.string.widget_automations),
                onOff(context, masterEnabled),
                actionRunCallback<ToggleMasterCallback>(),
            )
            StatusRow(
                context.getString(R.string.quick_wifi),
                state.wifiConnected?.let { onOff(context, it) } ?: "—",
                actionStartActivity(SettingsPanelActivity.intent(context, SettingsPanelActivity.PANEL_WIFI)),
            )
            StatusRow(
                context.getString(R.string.quick_bluetooth),
                state.bluetoothEnabled?.let { onOff(context, it) } ?: "—",
                actionStartActivity(SettingsPanelActivity.intent(context, SettingsPanelActivity.PANEL_BLUETOOTH)),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (quickActions.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                LazyColumn {
                    items(quickActions, itemId = { it.id.hashCode().toLong() }) { automation ->
                        Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Button(
                                text = automation.name,
                                onClick = actionRunCallback<RunAutomationCallback>(
                                    actionParametersOf(RunAutomationCallback.AUTOMATION_ID to automation.id),
                                ),
                                modifier = GlanceModifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, value: String, onClick: androidx.glance.action.Action) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurface), modifier = GlanceModifier.defaultWeight())
            Text(value, style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.primary))
        }
    }

    private fun onOff(context: Context, on: Boolean) =
        context.getString(if (on) R.string.widget_on else R.string.widget_off)

    companion object {
        private const val MAX_BUTTONS = 6

        /** Re-renders every AutoFlow widget (quick actions and single-automation buttons). */
        suspend fun refresh(context: Context) {
            QuickActionsWidget().updateAll(context)
            AutomationButtonWidget().updateAll(context)
        }
    }
}

/** Runs the tapped automation through WorkManager so it completes even if the widget host goes away. */
class RunAutomationCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[AUTOMATION_ID] ?: return
        AutomationRunWorker.enqueueManual(context, id, AutomationRunWorker.SOURCE_WIDGET)
    }

    companion object {
        val AUTOMATION_ID = ActionParameters.Key<String>("automation_id")
    }
}

/** Flips the master switch; the coordinator reacts by (un)registering triggers. */
class ToggleMasterCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settings = widgetEntryPoint(context).settings()
        settings.setMasterEnabled(!settings.settings.first().masterEnabled)
        QuickActionsWidget.refresh(context)
    }
}

class RefreshWidgetsCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        QuickActionsWidget.refresh(context)
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
