package com.example.soccergamemanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "soccer_manager_settings")

class SettingsStore(private val context: Context) {
    private val selectedSeasonKey = stringPreferencesKey("selected_season_id")

    val selectedSeasonId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[selectedSeasonKey]
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
}
