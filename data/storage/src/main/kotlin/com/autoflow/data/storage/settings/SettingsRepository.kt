package com.autoflow.data.storage.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.autoflow.core.engine.repository.EngineSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val masterEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val logSkippedRuns: Boolean = true,
)

/** User settings persisted with DataStore (small key/value data, observed reactively). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : EngineSettings {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            masterEnabled = prefs[MASTER] ?: true,
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            logSkippedRuns = prefs[LOG_SKIPPED] ?: true,
        )
    }

    override suspend fun isMasterEnabled(): Boolean = settings.first().masterEnabled

    override suspend fun logSkippedRuns(): Boolean = settings.first().logSkippedRuns

    suspend fun setMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[MASTER] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLogSkippedRuns(enabled: Boolean) {
        dataStore.edit { it[LOG_SKIPPED] = enabled }
    }

    companion object {
        private val MASTER = booleanPreferencesKey("master_enabled")
        private val THEME = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val LOG_SKIPPED = booleanPreferencesKey("log_skipped_runs")

        fun create(context: Context): SettingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile("settings") },
        )
    }
}
