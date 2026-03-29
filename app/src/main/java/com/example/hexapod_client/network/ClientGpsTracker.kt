package com.example.hexapod_client.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "ClientGpsTracker"

// Request a new GPS fix at most every 2 s (battery vs. freshness trade-off)
private const val GPS_MIN_INTERVAL_MS = 2_000L

/**
 * Tracks the **client phone's** GPS position using the Android [LocationManager] API.
 *
 * Used as the base station for dual-phone differential GPS (DGPS) corrections:
 * the client phone's position is periodically transmitted to the server, which
 * uses the drift between the current and anchor base position to correct the
 * rover (server phone) GPS readings.
 *
 * Also used by [WaypointMapScreen] to display the user's location on the map,
 * replacing the screen-local [LocationListener] so GPS runs throughout a mission
 * (not just while the map screen is visible).
 *
 * Lifecycle: call [start] from [HexapodClientViewModel] init and [stop] from [onCleared].
 * Requires [Manifest.permission.ACCESS_FINE_LOCATION] — silently no-ops if the
 * permission has not been granted.
 */
class ClientGpsTracker(private val context: Context) {

    private val _location = MutableStateFlow<Location?>(null)

    /**
     * Latest known client-phone position.  Null until the first fix is received.
     * Updates approximately every [GPS_MIN_INTERVAL_MS] when the phone is moving.
     */
    val location: StateFlow<Location?> = _location

    private val lm: LocationManager by lazy {
        context.getSystemService(LocationManager::class.java)
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            _location.value = loc
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    fun start() {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission not granted — DGPS base station disabled")
            return
        }
        try {
            // Seed from last-known so first fix is available immediately on launch
            val lastGps  = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet  = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            _location.value = lastGps ?: lastNet

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
                )
                Log.i(TAG, "GPS updates started")
            }
            // NETWORK_PROVIDER as fallback inside buildings
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_MIN_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting location updates", e)
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
        Log.i(TAG, "GPS updates stopped")
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
