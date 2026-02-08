package com.malcolmocean.randoping

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PingSettings(
    val avgMinutes: Int = 45,
    val quietStartHour: Int = 22,  // 10 PM
    val quietEndHour: Int = 8,     // 8 AM
    val message: String = "notice the vividness of reality",
    val enabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    companion object {
        val AVG_MINUTES = intPreferencesKey("avg_minutes")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val MESSAGE = stringPreferencesKey("message")
        val ENABLED = booleanPreferencesKey("enabled")
    }

    val settings: Flow<PingSettings> = context.dataStore.data.map { prefs ->
        PingSettings(
            avgMinutes = prefs[AVG_MINUTES] ?: 45,
            quietStartHour = prefs[QUIET_START] ?: 22,
            quietEndHour = prefs[QUIET_END] ?: 8,
            message = prefs[MESSAGE] ?: "notice the vividness of reality",
            enabled = prefs[ENABLED] ?: true
        )
    }

    suspend fun updateAvgMinutes(minutes: Int) {
        context.dataStore.edit { it[AVG_MINUTES] = minutes }
    }

    suspend fun updateQuietHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[QUIET_START] = start
            it[QUIET_END] = end
        }
    }

    suspend fun updateMessage(message: String) {
        context.dataStore.edit { it[MESSAGE] = message }
    }

    suspend fun updateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ENABLED] = enabled }
    }
}
