package com.example.soccergamemanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.soccergamemanager.ui.OrientationLockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val DEFAULT_CLOUD_SERVICE_URL = "https://manager.soccergrowthhub.com"
private const val LEGACY_CLOUD_SERVICE_URL = "https://soccer-game-manager-collab.jakubowski-andy.workers.dev"

internal fun currentCloudServiceUrl(serviceUrl: String): String =
    if (serviceUrl.trim().trimEnd('/') == LEGACY_CLOUD_SERVICE_URL) {
        DEFAULT_CLOUD_SERVICE_URL
    } else {
        serviceUrl
    }

data class CloudConnectionSettings(
    val localTeamId: String,
    val serviceUrl: String,
    val cloudTeamId: String,
    val deviceId: String,
    val deviceName: String,
)

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

    fun cloudConnection(localTeamId: String): Flow<CloudConnectionSettings?> = context.dataStore.data.map { preferences ->
        val serviceUrl = preferences[stringPreferencesKey("cloud_${localTeamId}_service_url")]
        val cloudTeamId = preferences[stringPreferencesKey("cloud_${localTeamId}_team_id")]
        val deviceId = preferences[stringPreferencesKey("cloud_${localTeamId}_device_id")]
        val deviceName = preferences[stringPreferencesKey("cloud_${localTeamId}_device_name")]
        if (serviceUrl == null || cloudTeamId == null || deviceId == null || deviceName == null) {
            null
        } else {
            CloudConnectionSettings(
                localTeamId = localTeamId,
                serviceUrl = currentCloudServiceUrl(serviceUrl),
                cloudTeamId = cloudTeamId,
                deviceId = deviceId,
                deviceName = deviceName,
            )
        }
    }

    suspend fun saveCloudConnection(settings: CloudConnectionSettings) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("cloud_${settings.localTeamId}_service_url")] = settings.serviceUrl
            preferences[stringPreferencesKey("cloud_${settings.localTeamId}_team_id")] = settings.cloudTeamId
            preferences[stringPreferencesKey("cloud_${settings.localTeamId}_device_id")] = settings.deviceId
            preferences[stringPreferencesKey("cloud_${settings.localTeamId}_device_name")] = settings.deviceName
        }
    }

    suspend fun clearCloudConnection(localTeamId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("cloud_${localTeamId}_service_url"))
            preferences.remove(stringPreferencesKey("cloud_${localTeamId}_team_id"))
            preferences.remove(stringPreferencesKey("cloud_${localTeamId}_device_id"))
            preferences.remove(stringPreferencesKey("cloud_${localTeamId}_device_name"))
        }
    }
}
