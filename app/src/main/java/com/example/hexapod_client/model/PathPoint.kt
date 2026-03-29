package com.example.hexapod_client.model

/**
 * A single recorded position in the robot's mission path.
 *
 * Collected from server telemetry once per second while [HexapodPose.isNavigating] is true.
 * The full list is written to a timestamped TXT file when the mission ends.
 *
 * @param timestampMs    Wall-clock time of this sample (System.currentTimeMillis()).
 * @param lat            WGS-84 latitude (decimal degrees).
 * @param lon            WGS-84 longitude (decimal degrees).
 * @param altitudeM      GPS altitude above MSL (metres) — equals the body-centre elevation.
 * @param terrainElevM   Estimated ground elevation (metres): altitudeM − bodyHeightMm / 1000.
 *                       Derived from the server's IK stance height; accuracy ±5–10 cm.
 * @param accuracyM      GPS horizontal accuracy, 68 % CEP (metres).  Values < 5 m are excellent;
 *                       5–20 m are acceptable outdoors; > 20 m indicates a poor fix.
 * @param gpsFix         True when accuracy ≤ 20 m and the fix is fresh (mirrors RobotPose.isValid).
 * @param speedMs        Robot ground speed in m/s from GPS Doppler.
 */
data class PathPoint(
    val timestampMs:   Long,
    val lat:           Double,
    val lon:           Double,
    val altitudeM:     Double,
    val terrainElevM:  Double,
    val accuracyM:     Float,
    val gpsFix:        Boolean,
    val speedMs:       Float
)
