package com.example.hexapod_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.location.Location
import com.example.hexapod_client.model.*
import com.example.hexapod_client.network.ClientGpsTracker
import com.example.hexapod_client.network.HexapodConnection
import com.example.hexapod_client.network.NsdDiscovery
import com.example.hexapod_client.network.ServerState
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "HexapodClientVM"

class HexapodClientViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo   = AppSettingsRepository(app.applicationContext)
    private val connection     = HexapodConnection(viewModelScope)
    private val nsdDiscovery   = NsdDiscovery(app.applicationContext)
    private val clientGps      = ClientGpsTracker(app.applicationContext)

    /** Live mDNS discovery state — observe in ConfigScreen to drive the SCAN button UI. */
    val discoveryState: StateFlow<NsdDiscovery.State> = nsdDiscovery.state

    val isConnected:  StateFlow<Boolean>      = connection.isConnected
    val lastError:    StateFlow<String?>      = connection.lastError
    val hexapodPose:  StateFlow<HexapodPose?> = connection.hexapodPose

    /**
     * Client phone's current GPS position.  Null until first fix.
     * Used both to display "ME" on the map and as the DGPS base-station reference
     * transmitted to the server for drift correction.
     */
    val clientLocation: StateFlow<Location?> = clientGps.location

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _currentCommand = MutableStateFlow(MotionCommand.stop())
    val currentCommand: StateFlow<MotionCommand> = _currentCommand

    private val _gaitType  = MutableStateFlow(GaitType.TRIPOD)
    val gaitType: StateFlow<GaitType> = _gaitType

    private val _speedLevel = MutableStateFlow(SpeedLevel.MEDIUM)
    val speedLevel: StateFlow<SpeedLevel> = _speedLevel

    private val _gaitMode = MutableStateFlow(GaitMode.STANDARD)
    val gaitMode: StateFlow<GaitMode> = _gaitMode

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints

    private val _isSendingWaypoints = MutableStateFlow(false)
    val isSendingWaypoints: StateFlow<Boolean> = _isSendingWaypoints

    // ---- Mission path recording ----

    /** Breadcrumb path collected during autonomous navigation (one point per telemetry tick). */
    private val _robotPath = MutableStateFlow<List<PathPoint>>(emptyList())
    val robotPath: StateFlow<List<PathPoint>> = _robotPath

    /**
     * Absolute path of the last saved TXT file, or null.
     * UI reads this to show a snackbar; call [clearSavedFileNotification] after showing it.
     */
    private val _missionSavedFile = MutableStateFlow<String?>(null)
    val missionSavedFile: StateFlow<String?> = _missionSavedFile

    // Rate-limited command channel: joystick floods drop oldest, only latest sent
    private val commandChannel = MutableSharedFlow<MotionCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )

    init {
        // Start the client GPS tracker immediately so location is available from launch.
        // Requires ACCESS_FINE_LOCATION — silently no-ops if permission is not yet granted.
        clientGps.start()

        // Coroutine 1: Rate-limited command drain.
        // Joystick events flood commandChannel; conflate() keeps only the latest so we never
        // send stale positions.  The delay enforces commandRateHz (default 20 Hz = 50 ms).
        viewModelScope.launch {
            commandChannel.conflate().collect { cmd ->
                connection.send(cmd)
                delay(1000L / settings.value.commandRateHz.coerceAtLeast(1))
            }
        }

        // Coroutine 2: Seed gait type and speed level from saved preferences.
        // Waits for the first non-default settings emission so the UI reflects
        // the user's last-used configuration at startup.
        viewModelScope.launch {
            settings.first { it != AppSettings() }.let { s ->
                _gaitType.value   = s.defaultGaitType
                _speedLevel.value = s.defaultSpeedLevel
            }
        }

        // Coroutine 3: Auto-connect to the last saved server address.
        // Skips if no IP has been saved yet (first launch or after settings clear).
        viewModelScope.launch {
            val s = settingsRepo.settingsFlow.first()
            if (s.serverIp.isNotBlank()) connect()
        }

        // Coroutine 4: mDNS auto-discovery listener.
        // When NsdDiscovery resolves a "HexapodServer" service on the local network,
        // persist the discovered IP/port and initiate a connection automatically —
        // so the user never has to type an IP address.
        viewModelScope.launch {
            nsdDiscovery.state.collect { state ->
                if (state is NsdDiscovery.State.Found) {
                    val updated = settings.value.copy(
                        serverIp   = state.ip,
                        serverPort = state.port
                    )
                    settingsRepo.save(updated)
                    connection.connect(state.ip, state.port)
                }
            }
        }

        // Coroutine 5: Server-state sync.
        // Telemetry frames from the robot include the active gait type, speed factor,
        // and mode name.  Reflect those back into the client UI so both sides always show
        // the same values (important when the robot auto-switches to TRIPOD for navigation).
        viewModelScope.launch {
            connection.serverState.collect { state ->
                state.gaitTypeName?.let { name ->
                    runCatching { GaitType.valueOf(name) }.getOrNull()?.let { _gaitType.value = it }
                }
                state.speedFactor?.let { factor ->
                    // Map the server's raw multiplier to the nearest named SpeedLevel
                    _speedLevel.value = SpeedLevel.entries.minByOrNull { abs(it.factor - factor) }
                        ?: SpeedLevel.MEDIUM
                }
                state.modeName?.let { name ->
                    GaitMode.entries.firstOrNull { it.serverName == name }?.let { _gaitMode.value = it }
                }
            }
        }

        // ---- Mission path recording ----

        // Coroutine 6: Breadcrumb trail.
        // Appends one PathPoint per telemetry tick while the robot is navigating.
        // The trail is written to a TXT file when the mission ends (see Coroutine 7).
        viewModelScope.launch {
            hexapodPose.collect { pose ->
                pose ?: return@collect
                if (pose.isNavigating) {
                    val point = PathPoint(
                        timestampMs  = System.currentTimeMillis(),
                        lat          = pose.lat,
                        lon          = pose.lon,
                        altitudeM    = pose.altitudeM,
                        terrainElevM = pose.altitudeM - (pose.bodyHeightMm / 1000.0),
                        accuracyM    = pose.accuracyM,
                        gpsFix       = pose.accuracyM < 20f,
                        speedMs      = pose.speedMs
                    )
                    _robotPath.value = _robotPath.value + point
                }
            }
        }

        // Coroutine 7: Auto-save on mission end.
        // Detects the isNavigating true→false transition and writes the accumulated
        // trail to a timestamped TXT file in the app's external-files directory.
        viewModelScope.launch {
            var wasNavigating = false
            hexapodPose.collect { pose ->
                // Skip null frames (telemetry gap) — a missing packet must not be
                // treated as "mission ended" or it will trigger a premature path save.
                if (pose == null) return@collect
                val nowNavigating = pose.isNavigating
                if (wasNavigating && !nowNavigating) {
                    val path = _robotPath.value
                    if (path.isNotEmpty()) savePath(path)
                }
                wasNavigating = nowNavigating
            }
        }

        // Coroutine 8: Sync touch-safety toggle to server.
        // Sends the current state on every change and re-sends on reconnect so
        // the server always mirrors the client's preference.
        viewModelScope.launch {
            settings
                .map { it.touchSafetyEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (isConnected.value) {
                        connection.sendSetTouchSafety(enabled)
                    }
                }
        }

        // Coroutine 9: Re-send touch-safety state on reconnect.
        viewModelScope.launch {
            isConnected
                .filter { it }          // true = just connected
                .collect {
                    connection.sendSetTouchSafety(settings.value.touchSafetyEnabled)
                }
        }

        // Coroutine 10: DGPS base-station transmitter.
        // Sends the client phone's GPS position to the server every 3 s while connected.
        // The server accumulates these samples and uses the drift from the mission-start
        // anchor to apply a differential correction to the rover (server phone) GPS,
        // cancelling correlated atmospheric and satellite-clock errors between the two
        // phones.  Expected improvement: from ±5–10 m raw to ±1–3 m corrected CEP.
        viewModelScope.launch {
            while (true) {
                delay(3_000L)
                if (!isConnected.value) continue
                val loc = clientLocation.value ?: continue
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE
                // Only send if the fix is fresh (< 10 s) and accurate (≤ 30 m).
                // A stale fix from minutes ago would anchor the DGPS correction to
                // the wrong position and degrade rover accuracy rather than improve it.
                val ageMs = System.currentTimeMillis() - loc.time
                if (ageMs > 10_000L || accuracy > 30f) continue
                connection.sendGpsBase(loc.latitude, loc.longitude, accuracy)
            }
        }
    }

    // ---- Connection ----

    fun connect() {
        val s = settings.value
        if (s.serverIp.isBlank()) return
        viewModelScope.launch { connection.connect(s.serverIp, s.serverPort) }
    }

    fun disconnect() = connection.disconnect()

    /** Start mDNS scan for a Hexapod Server on the local network. */
    fun startDiscovery() = nsdDiscovery.startDiscovery()

    /** Stop an in-progress scan without connecting. */
    fun stopDiscovery() = nsdDiscovery.stopDiscovery()

    /** Clear discovery result so the SCAN button returns to idle state. */
    fun resetDiscovery() = nsdDiscovery.reset()

    // ---- Motion commands ----

    fun sendCommand(cmd: MotionCommand) {
        val effective = if (settings.value.beginnerMode) {
            cmd.copy(speedLevel = SpeedLevel.SLOW)
        } else cmd
        _currentCommand.value = effective
        commandChannel.tryEmit(effective)
    }

    /**
     * Graceful stop — sends a "stop" message that halts locomotion without cutting servo power.
     * The robot finishes its current gait tick and stands still.  Call this from the normal
     * controller STOP button (■).  The servos stay powered, so the robot holds its stance.
     */
    fun stop() {
        _currentCommand.value = MotionCommand.stop(_gaitType.value, _speedLevel.value, _gaitMode.value)
        viewModelScope.launch { connection.sendStop() }
    }

    /**
     * Emergency halt — sends a "halt" message that triggers an emergency stop AND cuts the
     * servo relay power.  The robot will instantly lose all joint torque and collapse.
     * Reserve this for a dedicated "EMERGENCY CUT POWER" button — do NOT call from the
     * normal STOP control.
     */
    fun emergencyHalt() {
        _currentCommand.value = MotionCommand.stop(_gaitType.value, _speedLevel.value, _gaitMode.value)
        viewModelScope.launch { connection.sendHalt() }
    }

    fun setGaitType(type: GaitType)      { _gaitType.value  = type }
    fun setSpeedLevel(level: SpeedLevel) { _speedLevel.value = level }
    fun setGaitMode(mode: GaitMode)      { _gaitMode.value   = mode }

    // ---- Waypoints ----

    fun addWaypoint(lat: Double, lon: Double, label: String) {
        _waypoints.value = _waypoints.value + Waypoint(
            id = UUID.randomUUID().toString(), lat = lat, lon = lon, label = label
        )
    }

    fun removeWaypoint(id: String) {
        _waypoints.value = _waypoints.value.filterNot { it.id == id }
    }

    fun reorderWaypoints(fromIndex: Int, toIndex: Int) {
        val list = _waypoints.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            list.add(toIndex, list.removeAt(fromIndex))
            _waypoints.value = list
        }
    }

    fun clearWaypoints() { _waypoints.value = emptyList() }

    fun sendWaypointPath() {
        if (!isConnected.value) return   // no-op if the TCP link is down
        val path = WaypointPath(waypoints = _waypoints.value)
        // Reset the recorded path so a fresh trail starts for the new mission
        _robotPath.value = emptyList()
        _missionSavedFile.value = null
        viewModelScope.launch {
            _isSendingWaypoints.value = true
            try {
                connection.sendWaypoints(path)
            } finally {
                // Always clear the sending flag — even if sendWaypoints throws or is cancelled —
                // so the SEND button in the map screen doesn't stay permanently disabled.
                _isSendingWaypoints.value = false
            }
        }
    }

    // ---- Mission control ----

    /**
     * Emergency abort: stops the robot immediately and cancels all queued waypoints.
     * The path is auto-saved when the server confirms navigation has ended (via telemetry).
     */
    fun stopMission() {
        viewModelScope.launch { connection.sendStopMission() }
    }

    /** Reset the snackbar notification after the UI has shown it. */
    fun clearSavedFileNotification() { _missionSavedFile.value = null }

    /** Manually save the current recorded path (e.g. from a UI button). */
    fun savePathNow() {
        val path = _robotPath.value
        if (path.isEmpty()) return
        viewModelScope.launch { savePath(path) }
    }

    /** Clear the in-memory breadcrumb trail (does not delete any saved file). */
    fun clearPath() { _robotPath.value = emptyList() }

    // ---- Persistence ----

    fun saveSettings(updated: AppSettings) {
        viewModelScope.launch { settingsRepo.save(updated) }
    }

    // ---- Private ----

    /**
     * Writes [points] to a timestamped TXT file in the app's external files directory.
     * File name: hexapod_path_YYYYMMDD_HHmmss.txt
     * No special Android permissions required (API 29+, app-scoped external storage).
     */
    private suspend fun savePath(points: List<PathPoint>) = withContext(Dispatchers.IO) {
        try {
            val stamp    = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "hexapod_path_$stamp.txt"
            val dir      = getApplication<Application>().getExternalFilesDir(null)
                ?: run { Log.e(TAG, "External storage unavailable"); return@withContext }
            val file     = File(dir, fileName)

            file.bufferedWriter(Charsets.UTF_8).use { w ->
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val fixedPts  = points.count { it.gpsFix }
                val avgAcc    = if (points.isNotEmpty()) points.map { it.accuracyM }.average() else 0.0
                val minAcc    = points.minOfOrNull { it.accuracyM } ?: 0f
                val maxAcc    = points.maxOfOrNull { it.accuracyM } ?: 0f

                // ---- Header ----
                w.write("Hexapod Mission Path\n")
                w.write("Date: ${dateFmt.format(Date())}\n")
                w.write("Total points: ${points.size}\n")
                w.write("Duration: %.1f s\n".format(
                    (points.last().timestampMs - points.first().timestampMs) / 1000.0
                ))
                w.write("\n")
                w.write("GPS Quality Summary\n")
                w.write("  Fixed points : $fixedPts / ${points.size} (accuracy <= 20 m)\n")
                w.write("  Accuracy avg : %.1f m\n".format(avgAcc))
                w.write("  Accuracy min : %.1f m  (best)\n".format(minAcc))
                w.write("  Accuracy max : %.1f m  (worst)\n".format(maxAcc))
                w.write("\n")
                w.write("Column definitions\n")
                w.write("  ElapsedSec      : seconds since first recorded point\n")
                w.write("  Latitude/Lon    : WGS-84 decimal degrees\n")
                w.write("  BodyAltM        : GPS altitude above MSL = body-centre elevation (m)\n")
                w.write("  TerrainElevM    : estimated ground elevation = BodyAltM - IK stance height (m)\n")
                w.write("  GPSAccuracyM    : horizontal 68% CEP from GPS (m); lower = better\n")
                w.write("  GPSFix          : YES if accuracy <= 20 m and fix is fresh, NO otherwise\n")
                w.write("  SpeedMs         : robot ground speed from GPS Doppler (m/s)\n")
                w.write("---\n")

                // ---- Data rows ----
                w.write("ElapsedSec,Latitude,Longitude,BodyAltM,TerrainElevM,GPSAccuracyM,GPSFix,SpeedMs\n")
                val t0 = points.first().timestampMs
                points.forEach { pt ->
                    val elapsed = (pt.timestampMs - t0) / 1000.0
                    w.write(String.format(Locale.ROOT,
                        "%.1f,%.7f,%.7f,%.3f,%.3f,%.1f,%s,%.2f\n",
                        elapsed,
                        pt.lat, pt.lon,
                        pt.altitudeM, pt.terrainElevM,
                        pt.accuracyM,
                        if (pt.gpsFix) "YES" else "NO",
                        pt.speedMs
                    ))
                }
            }

            Log.i(TAG, "Mission path saved: ${file.absolutePath}")
            _missionSavedFile.value = file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mission path", e)
        }
    }

    override fun onCleared() {
        nsdDiscovery.stopDiscovery()
        clientGps.stop()
        connection.disconnect()
        super.onCleared()
    }
}
