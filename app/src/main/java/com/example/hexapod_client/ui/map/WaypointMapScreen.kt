package com.example.hexapod_client.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.hexapod_client.HexapodClientViewModel
import com.example.hexapod_client.model.MapTileSource
import com.example.hexapod_client.model.PathPoint
import com.example.hexapod_client.model.Waypoint
import com.example.hexapod_client.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Esri World Imagery — global, free, zoom up to 19. */
private val ESRI_SATELLITE = object : OnlineTileSourceBase(
    "EsriWorldImagery", 1, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
        MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private const val MAX_ZOOM = 19.0

/** Cyan circle — user (client phone) location. */
private fun userIcon(context: Context, hasFix: Boolean): BitmapDrawable {
    val size = 48; val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp); val cx = size / 2f; val r = cx - 3f
    cv.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (hasFix) android.graphics.Color.parseColor("#00E5FF")
                else        android.graphics.Color.parseColor("#546E7A")
    })
    cv.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0D0F14")
        style = Paint.Style.STROKE; strokeWidth = 3f
    })
    cv.drawText("ME", cx, cx + 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0D0F14")
        textSize = 13f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    })
    return BitmapDrawable(context.resources, bmp)
}

/** Green circle — hexapod (server) location. */
private fun hexapodIcon(context: Context): BitmapDrawable {
    val size = 56; val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp); val cx = size / 2f; val r = cx - 2.5f
    cv.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#00FF87")
    })
    cv.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0D0F14")
        style = Paint.Style.STROKE; strokeWidth = 3.5f
    })
    cv.drawText("HEX", cx, cx + 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0D0F14")
        textSize = 15f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    })
    return BitmapDrawable(context.resources, bmp)
}

private val OverlayBg = Color(0xCC0D0F14)   // 80 % opaque dark panel

@Composable
fun WaypointMapScreen(vm: HexapodClientViewModel, modifier: Modifier = Modifier) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHost   = remember { SnackbarHostState() }
    val scope          = rememberCoroutineScope()

    val waypoints        by vm.waypoints.collectAsStateWithLifecycle()
    val isSending        by vm.isSendingWaypoints.collectAsStateWithLifecycle()
    val isConnected      by vm.isConnected.collectAsStateWithLifecycle()
    val settings         by vm.settings.collectAsStateWithLifecycle()
    val hexapodPose      by vm.hexapodPose.collectAsStateWithLifecycle()
    val robotPath        by vm.robotPath.collectAsStateWithLifecycle()
    val missionSavedFile by vm.missionSavedFile.collectAsStateWithLifecycle()

    val isNavigating  = hexapodPose?.isNavigating ?: false
    val navTarget     = hexapodPose?.navTarget ?: ""
    val navRemaining  = hexapodPose?.navRemaining ?: 0

    // Show snackbar when path is saved; then clear the notification
    LaunchedEffect(missionSavedFile) {
        val path = missionSavedFile ?: return@LaunchedEffect
        val fileName = path.substringAfterLast('/')
        snackbarHost.showSnackbar(
            message  = "Path saved: $fileName",
            duration = SnackbarDuration.Long
        )
        vm.clearSavedFileNotification()
    }

    val hasPermission = ActivityCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var deletingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var mapViewRef       by remember { mutableStateOf<MapView?>(null) }
    var hasInitialZoomed by remember { mutableStateOf(false) }

    // GPS is tracked in the ViewModel (ClientGpsTracker) rather than locally, so that:
    //  (a) the DGPS base-station transmitter keeps running during navigation even when
    //      the map screen is not visible, and
    //  (b) there is only one LocationManager listener for the whole app.
    val userLocation by vm.clientLocation.collectAsStateWithLifecycle()
    val gpsFix = userLocation?.let { it.hasAccuracy() && it.accuracy <= 20f } ?: false

    val hexIcon = remember { hexapodIcon(context) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Auto-center on first location received
    LaunchedEffect(mapViewRef, userLocation) {
        val mv  = mapViewRef  ?: return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        if (hasInitialZoomed) return@LaunchedEffect
        hasInitialZoomed = true
        mv.controller.setZoom(17.0)
        mv.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
    }

    // ---- Lifecycle ----
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapViewRef?.onDetach() }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // ---- Pulsing animation for STOP MISSION button ----
    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
    val stopScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stop_scale"
    )

    // ---- Full-screen Box: map underneath, UI overlaid ----
    Box(modifier = modifier.fillMaxSize()) {

        // Map — fills entire screen
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mv.setTileSource(TileSourceFactory.MAPNIK)
                    mv.setMultiTouchControls(true)
                    mv.isTilesScaledToDpi = true
                    mv.setUseDataConnection(true)
                    mv.minZoomLevel = 2.0
                    mv.maxZoomLevel = MAX_ZOOM
                    mv.controller.setZoom(15.0)
                    mv.controller.setCenter(GeoPoint(0.0, 0.0))
                    mapViewRef = mv
                }
            },
            update = { mv ->
                val isSat = settings.mapTileSource == MapTileSource.SATELLITE
                mv.setTileSource(if (isSat) ESRI_SATELLITE else TileSourceFactory.MAPNIK)
                mv.maxZoomLevel = MAX_ZOOM
                mv.overlays.clear()

                // Long-press → add waypoint
                mv.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint) = false
                    override fun longPressHelper(p: GeoPoint): Boolean {
                        vm.addWaypoint(p.latitude, p.longitude, "WP${waypoints.size + 1}")
                        return true
                    }
                }))

                // Robot breadcrumb trail — solid green line showing the path taken
                if (robotPath.size >= 2) {
                    val trail = Polyline(mv)
                    trail.setPoints(robotPath.map { GeoPoint(it.lat, it.lon) })
                    trail.outlinePaint.color       = android.graphics.Color.parseColor("#00FF87")
                    trail.outlinePaint.strokeWidth = 6f
                    trail.outlinePaint.alpha       = 200
                    mv.overlays.add(trail)
                }

                // User location marker
                userLocation?.let { loc ->
                    val m = Marker(mv)
                    m.position = GeoPoint(loc.latitude, loc.longitude)
                    m.title    = if (gpsFix) "My location (fixed)" else "My location (no fix)"
                    m.snippet  = if (loc.hasAccuracy()) "±%.0f m".format(loc.accuracy) else ""
                    m.icon     = userIcon(context, gpsFix)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.setOnMarkerClickListener { it, _ -> it.showInfoWindow(); true }
                    mv.overlays.add(m)
                }

                // Hexapod location marker
                hexapodPose?.let { pose ->
                    val m = Marker(mv)
                    m.position = GeoPoint(pose.lat, pose.lon)
                    m.title    = if (pose.isNavigating) "Hexapod → ${pose.navTarget}" else "Hexapod"
                    m.snippet  = "%.0f°  %.1f m/s".format(pose.headingDeg, pose.speedMs)
                    m.icon     = hexIcon
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.setOnMarkerClickListener { it, _ -> it.showInfoWindow(); true }
                    mv.overlays.add(m)
                }

                // Numbered waypoint markers
                waypoints.forEachIndexed { index, wp ->
                    val m = Marker(mv)
                    m.position = GeoPoint(wp.lat, wp.lon)
                    m.title    = "#${index + 1}"
                    m.snippet  = "%.5f,  %.5f".format(wp.lat, wp.lon)
                    m.setOnMarkerClickListener { it, _ -> it.showInfoWindow(); deletingWaypoint = wp; true }
                    mv.overlays.add(m)
                }

                // Dashed cyan planned path
                if (waypoints.size >= 2) {
                    val poly = Polyline(mv)
                    poly.setPoints(waypoints.map { GeoPoint(it.lat, it.lon) })
                    poly.outlinePaint.color       = android.graphics.Color.parseColor("#00E5FF")
                    poly.outlinePaint.strokeWidth = 5f
                    poly.outlinePaint.pathEffect  = DashPathEffect(floatArrayOf(24f, 10f), 0f)
                    mv.overlays.add(poly)
                }
                mv.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ---- GPS fix status + nav state — top-left ----
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // GPS / hex status badge
            Row(
                modifier = Modifier
                    .background(OverlayBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (gpsFix) AccentGreen else Amber,
                            CircleShape
                        )
                )
                Text(
                    text = if (gpsFix) "GPS FIXED" else "NO FIX",
                    color = if (gpsFix) AccentGreen else Amber,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                if (hexapodPose != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).background(AccentCyan, CircleShape))
                    Text("HEX LIVE", color = AccentCyan,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            // Navigation status badge — only visible during a mission
            if (isNavigating) {
                Row(
                    modifier = Modifier
                        .background(Color(0xCC1B3A2A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).background(AccentGreen, CircleShape))
                    Text(
                        "NAVIGATING",
                        color = AccentGreen, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    if (navTarget.isNotEmpty()) {
                        Text(
                            "→ $navTarget",
                            color = ValueColor, fontSize = 11.sp
                        )
                    }
                    if (navRemaining > 0) {
                        Text(
                            "(+$navRemaining)",
                            color = LabelColor, fontSize = 10.sp
                        )
                    }
                    if (robotPath.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${robotPath.size} pts",
                            color = LabelColor, fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // ---- Map source toggle — top-right ----
        val isSatellite = settings.mapTileSource == MapTileSource.SATELLITE
        OutlinedButton(
            onClick = {
                val next = if (isSatellite) MapTileSource.STREET else MapTileSource.SATELLITE
                vm.saveSettings(settings.copy(mapTileSource = next))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            colors  = ButtonDefaults.outlinedButtonColors(
                containerColor = OverlayBg,
                contentColor   = if (isSatellite) AccentCyan else LabelColor
            ),
            border         = BorderStroke(1.dp, if (isSatellite) AccentCyan else BorderDim),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                if (isSatellite) "SAT" else "MAP",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }

        // ---- FABs — bottom-right ----
        Column(
            modifier              = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 64.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            horizontalAlignment   = Alignment.End
        ) {
            // STOP MISSION — pulsing red, only when navigating
            if (isNavigating) {
                FloatingActionButton(
                    onClick        = { vm.stopMission() },
                    modifier       = Modifier.scale(stopScale),
                    containerColor = RedHalt,
                    contentColor   = Color.White,
                    shape          = CircleShape
                ) {
                    Text("■", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            // My Location
            FloatingActionButton(
                onClick = {
                    if (hasPermission) {
                        userLocation?.let { mapViewRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude)) }
                    } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                containerColor = OverlayBg,
                contentColor   = AccentCyan,
                shape          = CircleShape
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
            }
        }

        // ---- Action bar — bottom, translucent ----
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(OverlayBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${waypoints.size} WP  •  long-press to add",
                    color = LabelColor, fontSize = 11.sp
                )
                hexapodPose?.let { pose ->
                    Text(
                        "Robot @ %.5f, %.5f".format(pose.lat, pose.lon),
                        color = AccentGreen, fontSize = 10.sp
                    )
                }
                userLocation?.let { loc ->
                    val accStr = if (loc.hasAccuracy()) " ±%.0fm".format(loc.accuracy) else ""
                    Text(
                        "Me @ %.5f, %.5f$accStr".format(loc.latitude, loc.longitude),
                        color = AccentCyan, fontSize = 10.sp
                    )
                }
                if (robotPath.isNotEmpty() && !isNavigating) {
                    Text(
                        "Trail: ${robotPath.size} pts recorded",
                        color = AccentGreen.copy(alpha = 0.7f), fontSize = 10.sp
                    )
                }
            }

            // CLEAR TRAIL — only shown when there is a recorded path and no active mission
            if (robotPath.isNotEmpty() && !isNavigating) {
                OutlinedButton(
                    onClick  = { vm.clearPath() },
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = LabelColor),
                    border   = BorderStroke(1.dp, BorderDim),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("CLR TRAIL", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }

            OutlinedButton(
                onClick  = { vm.clearWaypoints() },
                enabled  = waypoints.isNotEmpty(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedHalt),
                border   = BorderStroke(1.dp, RedHalt),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick  = { vm.sendWaypointPath() },
                enabled  = isConnected && waypoints.isNotEmpty() && !isSending && !isNavigating,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgColor),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(14.dp), color = BgColor, strokeWidth = 2.dp)
                else Text("SEND TO ROBOT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---- Snackbar host ----
        SnackbarHost(
            hostState = snackbarHost,
            modifier  = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
        ) { data ->
            Snackbar(
                snackbarData    = data,
                containerColor  = PanelColor,
                contentColor    = AccentGreen,
                actionColor     = AccentCyan
            )
        }
    }

    // Delete confirmation dialog
    deletingWaypoint?.let { wp ->
        val idx = waypoints.indexOf(wp) + 1
        AlertDialog(
            onDismissRequest  = { deletingWaypoint = null },
            containerColor    = PanelColor,
            titleContentColor = AccentCyan,
            textContentColor  = ValueColor,
            title = { Text("WAYPOINT #$idx") },
            text  = { Text("%.5f,  %.5f\n\nDelete this waypoint?".format(wp.lat, wp.lon)) },
            confirmButton = {
                TextButton(onClick = { vm.removeWaypoint(wp.id); deletingWaypoint = null }) {
                    Text("DELETE", color = RedHalt)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWaypoint = null }) { Text("CANCEL", color = LabelColor) }
            }
        )
    }
}
