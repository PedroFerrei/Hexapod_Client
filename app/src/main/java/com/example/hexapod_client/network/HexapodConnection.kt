package com.example.hexapod_client.network

import com.example.hexapod_client.model.HexapodPose
import com.example.hexapod_client.model.MotionCommand
import com.example.hexapod_client.model.WaypointPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Server gait state received in telemetry — used to keep client UI in sync. */
data class ServerState(
    val gaitTypeName:  String? = null,  // e.g. "TRIPOD", "RIPPLE", "WAVE"
    val speedFactor:   Float?  = null,  // e.g. 0.35, 0.65, 1.0
    val modeName:      String? = null   // e.g. "MODE_STANDARD"
)

class HexapodConnection(private val scope: CoroutineScope) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _hexapodPose = MutableStateFlow<HexapodPose?>(null)
    /** Latest pose broadcast from the robot (lat/lon/heading). Null until first telemetry. */
    val hexapodPose: StateFlow<HexapodPose?> = _hexapodPose

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState

    private var socket: Socket? = null
    private var readerJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectIp: String = ""
    private var reconnectPort: Int = 0

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val SOCKET_TIMEOUT_MS  = 10_000
        val BACKOFF_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000)
    }

    suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        reconnectIp = ip
        reconnectPort = port
        _lastError.value = null
        runCatching {
            val s = Socket()
            s.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = SOCKET_TIMEOUT_MS
            socket = s
            _isConnected.value = true
            startReaderLoop(s)
            true
        }.getOrElse { e ->
            _isConnected.value = false
            _lastError.value = e.message ?: "Connection failed"
            false
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        _isConnected.value = false
    }

    suspend fun send(cmd: MotionCommand) = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encode(cmd))
    }

    suspend fun sendStop() = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeStop())
    }

    suspend fun sendHalt() = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeHalt())
    }

    suspend fun sendWaypoints(path: WaypointPath) = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeWaypoints(path))
    }

    /** Emergency abort: stop autonomous navigation and halt the robot immediately. */
    suspend fun sendStopMission() = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeStopMission())
    }

    /**
     * Transmits the client phone's current GPS position to the server as a DGPS base-station
     * sample.  The server uses successive samples to compute a drift-correction vector that
     * cancels common-mode GPS errors shared by both phones (atmospheric delay, satellite
     * clock drift).  Call this approximately every 2–3 s while connected.
     *
     * Silently no-ops if the socket is not open (e.g. between reconnect attempts).
     */
    suspend fun sendSetTouchSafety(enabled: Boolean) = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeSetTouchSafety(enabled))
    }

    suspend fun sendGpsBase(lat: Double, lon: Double, accuracy: Float) = withContext(Dispatchers.IO) {
        writeFrame(CommandProtocol.encodeGpsBase(lat, lon, accuracy, System.currentTimeMillis()))
    }

    private fun writeFrame(bytes: ByteArray) {
        runCatching {
            socket?.getOutputStream()?.also {
                it.write(bytes)
                it.flush()
            } ?: throw IllegalStateException("Not connected")
        }.onFailure { e ->
            _lastError.value = "Send failed: ${e.message}"
            handleConnectionLoss()
        }
    }

    private fun startReaderLoop(s: Socket) {
        readerJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
                while (isActive) {
                    // SocketTimeoutException means no data arrived within soTimeout —
                    // the connection is still alive, just idle (e.g. no GPS telemetry).
                    // Continue waiting instead of triggering a reconnect.
                    val line = try {
                        reader.readLine() ?: break   // null = server closed connection
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val msg = CommandProtocol.decode(line) ?: continue
                    if (msg["type"] == "telemetry") {
                        val lat = (msg["lat"] as? Number)?.toDouble() ?: continue
                        val lon = (msg["lon"] as? Number)?.toDouble() ?: continue
                        val altM         = (msg["altitude"]     as? Number)?.toDouble() ?: 0.0
                        val bodyHeightMm = (msg["bodyHeightMm"] as? Number)?.toFloat()  ?: 90f
                        _hexapodPose.value = HexapodPose(
                            lat          = lat,
                            lon          = lon,
                            headingDeg   = (msg["heading"]      as? Number)?.toFloat() ?: 0f,
                            speedMs      = (msg["speed"]        as? Number)?.toFloat()  ?: 0f,
                            accuracyM    = (msg["accuracy"]     as? Number)?.toFloat()  ?: Float.MAX_VALUE,
                            altitudeM    = altM,
                            bodyHeightMm = bodyHeightMm,
                            isNavigating = msg["isNavigating"] as? Boolean ?: false,
                            navTarget    = msg["navTarget"]    as? String  ?: "",
                            navRemaining = (msg["navRemaining"] as? Number)?.toInt() ?: 0,
                            voltageV     = (msg["voltageV"]    as? Number)?.toFloat()  ?: -1f,
                            currentA     = (msg["currentA"]    as? Number)?.toFloat()  ?: -1f,
                            batteryPct   = (msg["batteryPct"]  as? Number)?.toInt()    ?: -1,
                            batteryLevel = (msg["batteryLevel"] as? String) ?: "UNKNOWN"
                        )
                        // Sync gait state back to the client UI
                        val gaitTypeName = msg["gaitType"]    as? String
                        val speedFactor  = (msg["speedFactor"] as? Number)?.toFloat()
                        val modeName     = msg["modeName"]     as? String
                        if (gaitTypeName != null || speedFactor != null || modeName != null) {
                            _serverState.value = ServerState(
                                gaitTypeName = gaitTypeName,
                                speedFactor  = speedFactor,
                                modeName     = modeName
                            )
                        }
                    }
                }
            }.onFailure { e ->
                if (isActive) _lastError.value = "Connection lost: ${e.message}"
            }
            _hexapodPose.value = null   // clear stale pose on disconnect
            if (isActive) handleConnectionLoss()
        }
    }

    private fun handleConnectionLoss() {
        runCatching { socket?.close() }
        socket = null
        _isConnected.value = false
        scheduleReconnect()
    }

    /**
     * Schedules a sequence of reconnect attempts with exponential backoff.
     *
     * Backoff schedule (from [BACKOFF_DELAYS_MS]): 1 s → 2 s → 4 s → 8 s → 16 s.
     * Stops as soon as one attempt succeeds.  If all five attempts fail, sets
     * [lastError] to a user-visible message and gives up — the user must retry
     * manually from ConfigScreen or by re-entering an IP address.
     *
     * A previous reconnect job is always cancelled before starting a new one,
     * so calling this multiple times (e.g. on repeated send failures) is safe.
     */
    private fun scheduleReconnect() {
        if (reconnectIp.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (delayMs in BACKOFF_DELAYS_MS) {
                delay(delayMs)
                if (!isActive) return@launch
                val ok = connect(reconnectIp, reconnectPort)
                if (ok) return@launch
            }
            _lastError.value = "Could not reconnect after ${BACKOFF_DELAYS_MS.size} attempts"
        }
    }
}
