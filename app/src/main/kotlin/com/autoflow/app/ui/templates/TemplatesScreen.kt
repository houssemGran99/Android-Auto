package com.autoflow.app.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoflow.app.R
import com.autoflow.app.data.TemplateCatalog
import com.autoflow.app.ui.text.SpecIcons
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.model.typeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(val catalog: TemplateCatalog) : ViewModel()

@Composable
fun TemplatesScreen(onUseTemplate: (String) -> Unit, viewModel: TemplatesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val f = rememberSpecFormatter()
    val previews = remember(viewModel) { viewModel.catalog.templates.map { it to it.build(context) } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_templates)) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(previews, key = { it.first.id }) { (template, automation) ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            automation.triggers.firstOrNull()?.let {
                                Icon(SpecIcons.trigger(it.typeKey), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                stringResource(template.title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(stringResource(template.description), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            f.automationSummary(automation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        automation.actions.forEach { action ->
                            Text(
                                "• " + listOf(f.actionTitle(action), f.actionDetail(action)).filter { it.isNotBlank() }.joinToString(": "),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        FilledTonalButton(onClick = { onUseTemplate(template.id) }, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.use_template))
                        }
                    }
                }
            }
        }
    }
}
