package com.malcolmocean.randoping

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class PingConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Ping!",
    val message: String = "notice the vividness of reality",
    val avgMinutes: Int = 45,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    val enabled: Boolean = true,
    val colorValue: Long = PingColors.defaultColor,
    val iconName: String = "bell"
) {
    val requestCode: Int get() = id.hashCode()
    val notificationId: Int get() = id.hashCode() ushr 1
    val channelId: String get() = "randoping_channel_$id"
}

object PingColors {
    val Purple = 0xFF6750A4
    val Blue = 0xFF1976D2
    val Green = 0xFF388E3C
    val Orange = 0xFFF57C00
    val Pink = 0xFFD81B60
    val Cyan = 0xFF00897B
    val DeepOrange = 0xFFE64A19
    val DeepPurple = 0xFF512DA8

    val defaultColor = Purple

    val all = listOf(Purple, Blue, Green, Orange, Pink, Cyan, DeepOrange, DeepPurple)

    fun nextUnusedColor(existingConfigs: List<PingConfig>): Long {
        val usedColors = existingConfigs.map { it.colorValue }.toSet()
        return all.firstOrNull { it !in usedColors } ?: all.random()
    }

    fun toComposeColor(value: Long): Color = Color(value)
}

data class IconEntry(
    val key: String,
    val label: String,
    val composeIcon: ImageVector,
    val drawableResId: Int
)

object PingIcons {
    val all: List<IconEntry> by lazy {
        listOf(
            IconEntry("bell", "Bell", Icons.Filled.Notifications, R.drawable.ic_notif_bell),
            IconEntry("star", "Star", Icons.Filled.Star, R.drawable.ic_notif_star),
            IconEntry("heart", "Heart", Icons.Filled.Favorite, R.drawable.ic_notif_heart),
            IconEntry("lightbulb", "Lightbulb", Icons.Filled.Lightbulb, R.drawable.ic_notif_lightbulb),
            IconEntry("eye", "Eye", Icons.Filled.Visibility, R.drawable.ic_notif_eye),
            IconEntry("meditate", "Meditate", Icons.Filled.SelfImprovement, R.drawable.ic_notif_meditate),
            IconEntry("brain", "Brain", Icons.Filled.Psychology, R.drawable.ic_notif_brain),
            IconEntry("leaf", "Leaf", Icons.Filled.Eco, R.drawable.ic_notif_leaf),
            IconEntry("music", "Music", Icons.Filled.MusicNote, R.drawable.ic_notif_music),
            IconEntry("fitness", "Fitness", Icons.Filled.FitnessCenter, R.drawable.ic_notif_fitness),
            IconEntry("coffee", "Coffee", Icons.Filled.Coffee, R.drawable.ic_notif_coffee),
            IconEntry("bolt", "Bolt", Icons.Filled.Bolt, R.drawable.ic_notif_bolt),
            IconEntry("water", "Water", Icons.Filled.WaterDrop, R.drawable.ic_notif_water),
            IconEntry("sun", "Sun", Icons.Filled.WbSunny, R.drawable.ic_notif_sun),
            IconEntry("moon", "Moon", Icons.Filled.DarkMode, R.drawable.ic_notif_moon),
            IconEntry("paw", "Paw", Icons.Filled.Pets, R.drawable.ic_notif_paw),
            IconEntry("briefcase", "Work", Icons.Filled.Work, R.drawable.ic_notif_briefcase),
            IconEntry("school", "School", Icons.Filled.School, R.drawable.ic_notif_school),
            IconEntry("runner", "Running", Icons.Filled.DirectionsRun, R.drawable.ic_notif_runner),
            IconEntry("timer", "Timer", Icons.Filled.Timer, R.drawable.ic_notif_timer),
            IconEntry("palette", "Palette", Icons.Filled.Palette, R.drawable.ic_notif_palette),
            IconEntry("restaurant", "Food", Icons.Filled.Restaurant, R.drawable.ic_notif_restaurant),
            IconEntry("house", "Home", Icons.Filled.Home, R.drawable.ic_notif_house),
            IconEntry("compass", "Explore", Icons.Filled.Explore, R.drawable.ic_notif_compass),
            IconEntry("cloud", "Cloud", Icons.Filled.Cloud, R.drawable.ic_notif_cloud),
            IconEntry("spa", "Spa", Icons.Filled.Spa, R.drawable.ic_notif_spa),
            IconEntry("sparkle", "Sparkle", Icons.Filled.AutoAwesome, R.drawable.ic_notif_sparkle),
            IconEntry("anchor", "Anchor", Icons.Filled.Anchor, R.drawable.ic_notif_anchor),
            IconEntry("diamond", "Diamond", Icons.Filled.Diamond, R.drawable.ic_notif_diamond),
            IconEntry("rocket", "Rocket", Icons.Filled.RocketLaunch, R.drawable.ic_notif_rocket),
        )
    }

    fun findByKey(key: String): IconEntry? = all.find { it.key == key }

    val default: IconEntry get() = all.first()
}

private val json = Json { ignoreUnknownKeys = true }

class SettingsRepository(private val context: Context) {

    companion object {
        private val PING_CONFIGS_JSON = stringPreferencesKey("ping_configs_json")
        private val LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated")

        // Legacy keys for migration
        private val LEGACY_AVG_MINUTES = intPreferencesKey("avg_minutes")
        private val LEGACY_QUIET_START = intPreferencesKey("quiet_start_hour")
        private val LEGACY_QUIET_END = intPreferencesKey("quiet_end_hour")
        private val LEGACY_MESSAGE = stringPreferencesKey("message")
        private val LEGACY_ENABLED = booleanPreferencesKey("enabled")
    }

    val pingConfigs: Flow<List<PingConfig>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[PING_CONFIGS_JSON]
        if (jsonStr != null) {
            json.decodeFromString<List<PingConfig>>(jsonStr)
        } else {
            emptyList()
        }
    }

    suspend fun ensureMigrated() {
        val prefs = context.dataStore.data.first()
        if (prefs[LEGACY_MIGRATED] == true) return

        // Check if there are legacy settings to migrate
        val hasLegacy = prefs[LEGACY_AVG_MINUTES] != null ||
                prefs[LEGACY_MESSAGE] != null ||
                prefs[LEGACY_ENABLED] != null

        if (hasLegacy && prefs[PING_CONFIGS_JSON] == null) {
            val legacyConfig = PingConfig(
                avgMinutes = prefs[LEGACY_AVG_MINUTES] ?: 45,
                quietStartHour = prefs[LEGACY_QUIET_START] ?: 22,
                quietEndHour = prefs[LEGACY_QUIET_END] ?: 8,
                message = prefs[LEGACY_MESSAGE] ?: "notice the vividness of reality",
                enabled = prefs[LEGACY_ENABLED] ?: true
            )
            context.dataStore.edit {
                it[PING_CONFIGS_JSON] = json.encodeToString(listOf(legacyConfig))
                it[LEGACY_MIGRATED] = true
            }
        } else {
            context.dataStore.edit {
                it[LEGACY_MIGRATED] = true
            }
        }
    }

    suspend fun saveConfigs(configs: List<PingConfig>) {
        context.dataStore.edit {
            it[PING_CONFIGS_JSON] = json.encodeToString(configs)
        }
    }

    suspend fun updateConfig(updated: PingConfig) {
        val configs = pingConfigs.first().toMutableList()
        val index = configs.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            configs[index] = updated
            saveConfigs(configs)
        }
    }

    suspend fun addConfig(config: PingConfig) {
        val configs = pingConfigs.first() + config
        saveConfigs(configs)
    }

    suspend fun removeConfig(configId: String) {
        val configs = pingConfigs.first().filter { it.id != configId }
        saveConfigs(configs)
    }
}
