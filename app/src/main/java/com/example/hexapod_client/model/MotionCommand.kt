package com.example.hexapod_client.model

enum class GaitType { TRIPOD, RIPPLE, WAVE }

enum class SpeedLevel(val factor: Float) {
    SLOW(0.35f), MEDIUM(0.65f), FAST(1.0f)
}

/**
 * Body configuration mode — matches server's MODE_* config entries.
 * Sent on every move frame; server only applies on change (cached).
 */
enum class GaitMode(val serverName: String, val label: String) {
    STANDARD ("MODE_STANDARD",  "STD"),
    OFFROAD  ("MODE_OFFROAD",   "OFRD"),
    QUADRUPED("MODE_QUADRUPED", "QUAD"),
    BLOCK    ("MODE_BLOCK",     "BLCK")
}

/**
 * 6-DOF motion command.
 *
 * Joystick mapping:
 *   LEFT   stick Y → vx   (forward/back,  normalized [-1,1])
 *   LEFT   stick X → vy   (strafe,        normalized [-1,1], negated so right=right)
 *   RIGHT  stick X → roll (body tilt,     normalized [-1,1]; server applies ×15°)
 *                           stick-left → left side down; stick-right → right side down
 *   RIGHT  stick Y → vz   (body height,   normalized [-1,1], 2.5× pre-clamp)
 *   YAW    stick X → yaw  (turn rate,     normalized [-1,1]; wire: × MAX_YAW_DEG_S = ±60°/s)
 *
 * pitch field kept for future use but not currently mapped to any input.
 */
data class MotionCommand(
    val vx:         Float      = 0f,  // forward(+) / backward(-)  — LEFT  Y
    val vy:         Float      = 0f,  // strafe right(+) / left(-) — LEFT  X (negated)
    val vz:         Float      = 0f,  // body up(+) / down(-)      — RIGHT Y (2.5×)
    val yaw:        Float      = 0f,  // reserved — not currently mapped
    val pitch:      Float      = 0f,  // reserved — not currently mapped
    val roll:       Float      = 0f,  // body tilt right(+) / left(-) — RIGHT X (negated, ×15°)
    val gaitType:   GaitType   = GaitType.TRIPOD,
    val speedLevel: SpeedLevel = SpeedLevel.MEDIUM,
    val gaitMode:   GaitMode   = GaitMode.STANDARD
) {
    val isMoving: Boolean get() = vx != 0f || vy != 0f || vz != 0f || roll != 0f || yaw != 0f

    companion object {
        fun stop(
            gaitType:   GaitType   = GaitType.TRIPOD,
            speedLevel: SpeedLevel = SpeedLevel.MEDIUM,
            gaitMode:   GaitMode   = GaitMode.STANDARD
        ) = MotionCommand(gaitType = gaitType, speedLevel = speedLevel, gaitMode = gaitMode)
    }
}
