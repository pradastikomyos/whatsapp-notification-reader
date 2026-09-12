package com.ridenotify.app.wa_reader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val THEME_DATASTORE_NAME = "theme_settings"
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = THEME_DATASTORE_NAME,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class ThemeSettingsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.themeDataStore,
) {
    fun observeThemeMode(): Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[KEY_THEME_MODE] }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }
}
