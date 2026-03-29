package com.example.hexapod_client.network

import com.example.hexapod_client.model.MotionCommand
import com.example.hexapod_client.model.WaypointPath
import com.google.gson.Gson

/**
 * Wire format: UTF-8 JSON, newline-terminated.
 *
 * Motion frame:
 *   {"type":"move","forward":<vx>,"lateral":<vy>,
 *    "turn":<yaw × MAX_YAW_DEG_S>,"vz":<vz>,"roll":<roll [-1,+1]>,
 *    "gait":"TRIPOD|RIPPLE|WAVE","speed":<0.35|0.65|1.0>,
 *    "mode":"MODE_STANDARD|MODE_OFFROAD|MODE_QUADRUPED|MODE_BLOCK"}\n
 *
 * "turn"   : turn rate in °/s = cmd.yaw × MAX_YAW_DEG_S (±60°/s at full stick deflection).
 *            Driven by the horizontal-only Yaw stick in the controller screen.
 *            Server field: turnRateDegS, consumed by GaitController.
 * "roll"   : normalised [-1,+1]; server applies × 15° around the robot's forward axis.
 * "pitch"  : omitted — reserved, server does not consume it.
 */

private const val MAX_YAW_DEG_S = 60f   // ±60°/s at full stick
object CommandProtocol {

    private val gson = Gson()

    fun encode(cmd: MotionCommand): ByteArray {
        val map = linkedMapOf(
            "type"    to "move",
            "forward" to cmd.vx,
            "lateral" to cmd.vy,
            "turn"    to (cmd.yaw * MAX_YAW_DEG_S),   // normalized → deg/s
            "vz"      to cmd.vz,
            "roll"    to cmd.roll,
            "gait"    to cmd.gaitType.name,
            "speed"   to cmd.speedLevel.factor,
            "mode"    to cmd.gaitMode.serverName
        )
        return (gson.toJson(map) + "\n").toByteArray(Charsets.UTF_8)
    }

    fun encodeStop(): ByteArray =
        ("{\"type\":\"stop\"}\n").toByteArray(Charsets.UTF_8)

    /** Emergency stop + servo power cut. */
    fun encodeHalt(): ByteArray =
        ("{\"type\":\"halt\"}\n").toByteArray(Charsets.UTF_8)

    /**
     * Builds the exact wire format the server expects:
     *   {"type":"waypoints","points":[{"lat":...,"lon":...,"label":"...","radius":...}, ...]}
     *
     * NOTE: WaypointPath.toJson() produces {"type":"WAYPOINT_PATH","waypoints":[...]} which
     * does NOT match the server — the type and array-key names both differ.
     */
    fun encodeWaypoints(path: WaypointPath): ByteArray {
        val sb = StringBuilder()
        sb.append("{\"type\":\"waypoints\",\"points\":[")
        path.waypoints.forEachIndexed { i, wp ->
            if (i > 0) sb.append(',')
            sb.append("{\"lat\":").append(wp.lat)
            sb.append(",\"lon\":").append(wp.lon)
            sb.append(",\"label\":\"").append(wp.label.replace("\"", "\\\"")).append("\"")
            sb.append(",\"radius\":").append(wp.radiusM)
            sb.append('}')
        }
        sb.append("]}\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Cancel autonomous navigation — robot stops at current position. */
    fun encodeClearNav(): ByteArray =
        ("{\"type\":\"clear_nav\"}\n").toByteArray(Charsets.UTF_8)

    /** Emergency mission abort — immediately halts robot + clears all waypoints. */
    fun encodeStopMission(): ByteArray =
        ("{\"type\":\"stop_mission\"}\n").toByteArray(Charsets.UTF_8)

    /**
     * Encodes the client phone's GPS position as a **base-station** message.
     *
     * Wire format:
     *   {"type":"gps_base","lat":<double>,"lon":<double>,"accuracy":<float>,"ts":<long>}
     *
     * The server uses this to compute a DGPS drift-correction vector that cancels
     * common-mode GPS errors (atmospheric delay, satellite clock drift) between the
     * two phones.  Sent approximately every 2–3 s while connected.
     *
     * @param lat       WGS-84 latitude of the client phone (decimal degrees).
     * @param lon       WGS-84 longitude of the client phone (decimal degrees).
     * @param accuracy  Horizontal 68% CEP accuracy (metres); Float.MAX_VALUE if unavailable.
     * @param timestampMs  Wall-clock time of this fix (System.currentTimeMillis()).
     */
    fun encodeGpsBase(lat: Double, lon: Double, accuracy: Float, timestampMs: Long): ByteArray {
        val sb = StringBuilder()
        sb.append("{\"type\":\"gps_base\"")
        sb.append(",\"lat\":").append(lat)
        sb.append(",\"lon\":").append(lon)
        sb.append(",\"accuracy\":").append(accuracy)
        sb.append(",\"ts\":").append(timestampMs)
        sb.append("}\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun encodeSetTouchSafety(enabled: Boolean): ByteArray =
        ("{\"type\":\"set_touch_safety\",\"enabled\":$enabled}\n").toByteArray(Charsets.UTF_8)

    fun decode(line: String): Map<*, *>? =
        runCatching { gson.fromJson(line, Map::class.java) }.getOrNull()
}
