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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
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
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.Automation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun automations(): AutomationRepository
}

/** Home-screen widget with one button per automation marked as "quick action". */
class QuickActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).automations()
        val quickActions = repository.getAll().filter { it.quickAction }.take(MAX_BUTTONS)
        provideContent {
            GlanceTheme {
                Content(context, quickActions)
            }
        }
    }

    @Composable
    private fun Content(context: Context, quickActions: List<Automation>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
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

    companion object {
        private const val MAX_BUTTONS = 6

        suspend fun refresh(context: Context) = QuickActionsWidget().updateAll(context)
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

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
