package com.autoflow.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.BackupManager
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.model.InvalidAutomationFileException
import com.autoflow.data.storage.settings.AppSettings
import com.autoflow.data.storage.settings.SettingsRepository
import com.autoflow.data.storage.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface SettingsMessage {
    data object Exported : SettingsMessage
    data class Imported(val automations: Int, val variables: Int) : SettingsMessage
    data class ImportFailed(val reason: String) : SettingsMessage
    data object IoFailed : SettingsMessage
    data object HistoryCleared : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backup: BackupManager,
    private val executions: ExecutionRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setMaster(enabled: Boolean) = launch { settingsRepository.setMasterEnabled(enabled) }
    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { settingsRepository.setDynamicColor(enabled) }
    fun setLogSkipped(enabled: Boolean) = launch { settingsRepository.setLogSkippedRuns(enabled) }

    fun clearHistory() = launch {
        executions.clear()
        _messages.send(SettingsMessage.HistoryCleared)
    }

    fun exportTo(uri: Uri) = launch {
        try {
            val json = backup.exportAll()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: throw IOException("No stream")
            }
            _messages.send(SettingsMessage.Exported)
        } catch (e: IOException) {
            _messages.send(SettingsMessage.IoFailed)
        }
    }

    fun importFrom(uri: Uri) = launch {
        try {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText().also { require(it.length <= MAX_IMPORT_CHARS) { "File too large" } }
                } ?: throw IOException("No stream")
            }
            val result = backup.import(json)
            _messages.send(SettingsMessage.Imported(result.automations, result.variables))
        } catch (e: InvalidAutomationFileException) {
            _messages.send(SettingsMessage.ImportFailed(e.message.orEmpty()))
        } catch (e: IllegalArgumentException) {
            _messages.send(SettingsMessage.ImportFailed(e.message.orEmpty()))
        } catch (e: IOException) {
            _messages.send(SettingsMessage.IoFailed)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 5_000_000
    }
}
