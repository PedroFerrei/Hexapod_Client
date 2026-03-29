package com.example.hexapod_client.model

/**
 * Live pose broadcast from the robot's server over TCP telemetry.
 *
 * All fields have safe defaults so callers that parse a subset of the JSON
 * (e.g. older server firmware) still compile and run correctly.
 */
data class HexapodPose(
    val lat:           Double,
    val lon:           Double,
    val headingDeg:    Float,
    val speedMs:       Float,
    val accuracyM:     Float,
    /** GPS altitude above mean sea level (metres). Equals the body-centre elevation. */
    val altitudeM:     Double  = 0.0,
    /** IK-derived body height above the ground plane (mm). Used to estimate terrain elevation. */
    val bodyHeightMm:  Float   = 90f,
    /** True while the server is executing autonomous waypoint navigation. */
    val isNavigating:  Boolean = false,
    /** Label of the current target waypoint, or "" when idle. */
    val navTarget:     String  = "",
    /** Number of waypoints still queued after the current target. */
    val navRemaining:  Int     = 0,
    /** Battery voltage in volts.  -1 when the server has no reading yet. */
    val voltageV:      Float   = -1f,
    /** Total servo current draw in amperes.  -1 when unavailable. */
    val currentA:      Float   = -1f,
    /** Battery state-of-charge 0–100 %.  -1 when unavailable. */
    val batteryPct:    Int     = -1,
    /**
     * Coarse battery health level transmitted from the server.
     * Matches [com.example.hexapod_server.BatteryLevel].
     * "NORMAL" | "LOW" | "CRITICAL" | "EMERGENCY" | "UNKNOWN"
     */
    val batteryLevel:  String  = "UNKNOWN"
)
