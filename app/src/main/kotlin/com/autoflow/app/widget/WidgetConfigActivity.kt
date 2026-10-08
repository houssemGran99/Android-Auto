package com.autoflow.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.autoflow.app.R
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.app.ui.theme.AutoFlowTheme
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.model.Automation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shown by the launcher when an "AutoFlow button" widget is placed: pick the automation it runs. */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var automations: AutomationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Leaving without a choice cancels the widget placement.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            AutoFlowTheme {
                val list by automations.observeAll().collectAsStateWithLifecycle(initialValue = null)
                val formatter = rememberSpecFormatter()
                Scaffold(topBar = { TopAppBar(title = { Text(getString(R.string.widget_choose_automation)) }) }) { padding ->
                    val items = list
                    when {
                        items == null -> Unit
                        items.isEmpty() -> EmptyState(
                            Icons.Outlined.SmartToy,
                            getString(R.string.empty_automations_title),
                            getString(R.string.widget_config_empty),
                            androidx.compose.ui.Modifier.padding(padding),
                        )
                        else -> LazyColumn(androidx.compose.ui.Modifier.padding(padding)) {
                            items(items, key = { it.id }) { automation ->
                                ListItem(
                                    headlineContent = { Text(automation.name) },
                                    supportingContent = { Text(formatter.automationSummary(automation), maxLines = 2) },
                                    modifier = androidx.compose.ui.Modifier.clickable { choose(appWidgetId, automation) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, automation: Automation) {
        lifecycleScope.launch {
            AutomationButtonWidget.bind(this@WidgetConfigActivity, appWidgetId, automation.id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
