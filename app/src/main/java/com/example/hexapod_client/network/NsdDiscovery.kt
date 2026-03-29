package com.example.hexapod_client.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG              = "NsdDiscovery"
private const val SERVICE_TYPE     = "_hexapod._tcp."

/**
 * Wraps Android's [NsdManager] to discover the Hexapod Server on the local network.
 *
 * The server must have registered itself with the same [SERVICE_TYPE] using NSD.
 * Once found, [state] transitions to [State.Found] with the resolved IP and port,
 * which the ViewModel uses to auto-fill and connect.
 *
 * Lifecycle:
 *   [startDiscovery] — begins scanning; [state] goes to [State.Scanning]
 *   [stopDiscovery]  — stops the scan explicitly (also called internally on find)
 *   [reset]          — returns state to [State.Idle] without re-scanning
 */
class NsdDiscovery(context: Context) {

    sealed class State {
        object Idle     : State()
        object Scanning : State()
        data class Found(val ip: String, val port: Int, val name: String) : State()
        data class Error(val message: String) : State()
    }

    private val nsdManager = context.getSystemService(NsdManager::class.java)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var resolving = false   // guard: only one resolve at a time

    fun startDiscovery() {
        if (_state.value is State.Scanning) return
        stopDiscovery()            // clean up any previous listener
        resolving = false
        _state.value = State.Scanning

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery start failed: $errorCode")
                _state.value = State.Error("Scan failed (error $errorCode)")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery stop failed: $errorCode")
            }
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Scanning for $serviceType …")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Found service: ${serviceInfo.serviceName}")
                if (!resolving) {
                    resolving = true
                    resolveService(serviceInfo)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }
        }

        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { e ->
            Log.e(TAG, "discoverServices threw: ${e.message}")
            _state.value = State.Error(e.message ?: "Scan error")
            discoveryListener = null
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            discoveryListener = null
        }
        if (_state.value is State.Scanning) _state.value = State.Idle
    }

    fun reset() {
        stopDiscovery()
        _state.value = State.Idle
    }

    @Suppress("DEPRECATION")   // resolveService(info, listener) deprecated API 34, still works
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed: $errorCode")
                resolving = false
                _state.value = State.Error("Could not resolve host (error $errorCode)")
            }
            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val ip   = resolvedInfo.host?.hostAddress
                val port = resolvedInfo.port
                if (ip == null || port <= 0) {
                    Log.w(TAG, "Resolved but host/port invalid: $resolvedInfo")
                    resolving = false
                    _state.value = State.Error("Resolved host invalid")
                    return
                }
                Log.i(TAG, "Resolved → $ip:$port  (${resolvedInfo.serviceName})")
                _state.value = State.Found(ip, port, resolvedInfo.serviceName)
                stopDiscovery()   // we have what we need — stop scanning
            }
        })
    }
}
