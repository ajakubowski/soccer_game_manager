package com.example.soccergamemanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.soccergamemanager.ui.OrientationLockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "soccer_manager_settings")

class SettingsStore(private val context: Context) {
    private val selectedSeasonKey = stringPreferencesKey("selected_season_id")
    private val orientationLockKey = stringPreferencesKey("orientation_lock_mode")

    val selectedSeasonId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[selectedSeasonKey]
    }

    val orientationLockMode: Flow<OrientationLockMode> = context.dataStore.data.map { preferences ->
        preferences[orientationLockKey]
            ?.let { runCatching { OrientationLockMode.valueOf(it) }.getOrNull() }
            ?: OrientationLockMode.AUTO
    }

    suspend fun setSelectedSeasonId(seasonId: String?) {
        context.dataStore.edit { preferences ->
            if (seasonId == null) {
                preferences.remove(selectedSeasonKey)
            } else {
                preferences[selectedSeasonKey] = seasonId
            }
        }
    }

    suspend fun setOrientationLockMode(mode: OrientationLockMode) {
        context.dataStore.edit { preferences ->
            preferences[orientationLockKey] = mode.name
        }
    }
}
