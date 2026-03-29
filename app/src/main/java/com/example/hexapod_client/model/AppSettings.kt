package com.example.hexapod_client.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MapTileSource { STREET, SATELLITE }

data class AppSettings(
    val serverIp: String = "",
    val serverPort: Int = 8765,  // must match SERVER_PORT in Hexapod_Server CommandServer.kt

    /** Gait type seeded at startup; overridden by server telemetry once connected. */
    val defaultGaitType: GaitType = GaitType.TRIPOD,
    val defaultSpeedLevel: SpeedLevel = SpeedLevel.MEDIUM,

    /** Stick dead-zone in percent (5–20 %); prevents drift when thumb doesn't center. */
    val joystickDeadZonePct: Int = 8,

    /** Rate at which MotionCommand frames are sent to the server (Hz). */
    val commandRateHz: Int = 20,
    val hapticEnabled: Boolean = true,

    /**
     * Touch-sensor safety preference.  Transmitted to the server as a
     * "set_touch_safety" message whenever this value changes or the client
     * reconnects.  The server sets [GaitController.touchSafetyEnabled] accordingly.
     */
    val touchSafetyEnabled: Boolean = false,

    /** Whether the map view uses satellite imagery (Esri) or OpenStreetMap tiles. */
    val mapTileSource: MapTileSource = MapTileSource.STREET,

    /** When true, all motion is silently capped to SpeedLevel.SLOW on the client. */
    val beginnerMode: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hexapod_client_settings"
)

class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_IP       = stringPreferencesKey("server_ip")
        val SERVER_PORT     = intPreferencesKey("server_port")
        val DEFAULT_GAIT    = stringPreferencesKey("default_gait")
        val DEFAULT_SPEED   = stringPreferencesKey("default_speed")
        val DEAD_ZONE_PCT   = intPreferencesKey("dead_zone_pct")
        val CMD_RATE_HZ     = intPreferencesKey("cmd_rate_hz")
        val HAPTIC_ENABLED  = booleanPreferencesKey("haptic_enabled")
        val TOUCH_SAFETY    = booleanPreferencesKey("touch_safety")
        val MAP_TILE_SOURCE = stringPreferencesKey("map_tile_source")
        val BEGINNER_MODE   = booleanPreferencesKey("beginner_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            serverIp         = prefs[Keys.SERVER_IP]       ?: "",
            serverPort       = prefs[Keys.SERVER_PORT]     ?: 8765,
            defaultGaitType  = GaitType.valueOf(prefs[Keys.DEFAULT_GAIT]    ?: GaitType.TRIPOD.name),
            defaultSpeedLevel= SpeedLevel.valueOf(prefs[Keys.DEFAULT_SPEED] ?: SpeedLevel.MEDIUM.name),
            joystickDeadZonePct = prefs[Keys.DEAD_ZONE_PCT] ?: 8,
            commandRateHz    = prefs[Keys.CMD_RATE_HZ]     ?: 20,
            hapticEnabled    = prefs[Keys.HAPTIC_ENABLED]  ?: true,
            touchSafetyEnabled = prefs[Keys.TOUCH_SAFETY]  ?: false,
            mapTileSource    = MapTileSource.valueOf(prefs[Keys.MAP_TILE_SOURCE] ?: MapTileSource.STREET.name),
            beginnerMode     = prefs[Keys.BEGINNER_MODE]   ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_IP]       = settings.serverIp
            prefs[Keys.SERVER_PORT]     = settings.serverPort
            prefs[Keys.DEFAULT_GAIT]    = settings.defaultGaitType.name
            prefs[Keys.DEFAULT_SPEED]   = settings.defaultSpeedLevel.name
            prefs[Keys.DEAD_ZONE_PCT]   = settings.joystickDeadZonePct
            prefs[Keys.CMD_RATE_HZ]     = settings.commandRateHz
            prefs[Keys.HAPTIC_ENABLED]  = settings.hapticEnabled
            prefs[Keys.TOUCH_SAFETY]    = settings.touchSafetyEnabled
            prefs[Keys.MAP_TILE_SOURCE] = settings.mapTileSource.name
            prefs[Keys.BEGINNER_MODE]   = settings.beginnerMode
        }
    }
}
