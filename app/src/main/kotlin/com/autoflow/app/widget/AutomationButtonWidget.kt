package com.autoflow.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.autoflow.app.MainActivity
import com.autoflow.app.R

/**
 * One-tap widget bound to a single automation, chosen when the widget is placed
 * (and changeable later on Android 12+ through "reconfigure").
 */
class AutomationButtonWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val automationId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[AUTOMATION_ID]
        val automation = automationId?.let { widgetEntryPoint(context).automations().get(it) }
        provideContent {
            GlanceTheme {
                val modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(16.dp)
                    .padding(8.dp)
                val action = if (automation != null) {
                    actionRunCallback<RunAutomationCallback>(actionParametersOf(RunAutomationCallback.AUTOMATION_ID to automation.id))
                } else {
                    actionStartActivity<MainActivity>()
                }
                Column(
                    modifier = modifier.clickable(action),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "▶",
                        style = TextStyle(fontSize = 22.sp, color = GlanceTheme.colors.onPrimaryContainer, textAlign = TextAlign.Center),
                    )
                    Text(
                        text = automation?.name ?: context.getString(R.string.widget_button_missing),
                        maxLines = 2,
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        val AUTOMATION_ID = stringPreferencesKey("automation_id")

        /** Binds the widget instance [appWidgetId] to [automationId] and redraws it. */
        suspend fun bind(context: Context, appWidgetId: Int, automationId: String) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
                prefs.toMutablePreferences().apply { this[AUTOMATION_ID] = automationId }
            }
            AutomationButtonWidget().update(context, glanceId)
        }
    }
}

class AutomationButtonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AutomationButtonWidget()
}
