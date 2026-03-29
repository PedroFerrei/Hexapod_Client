package com.example.hexapod_client.model

import com.google.gson.Gson
import java.util.UUID
import kotlin.math.*

data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    val lat: Double,
    val lon: Double,
    val label: String,
    val radiusM: Float = 3.0f    // 3 m default — achievable with consumer GPS (3–10 m CEP)
) {
    fun toJson(): String = Gson().toJson(this)

    fun distanceTo(other: Waypoint): Float {
        val R = 6_371_000.0
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(other.lat)
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).toFloat()
    }

    companion object {
        fun fromJson(json: String): Waypoint = Gson().fromJson(json, Waypoint::class.java)
    }
}

data class WaypointPath(
    val type: String = "WAYPOINT_PATH",
    val waypoints: List<Waypoint>
) {
    fun toJson(): String = Gson().toJson(this)
}
