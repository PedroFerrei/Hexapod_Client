# Hexapod Client — Master TODO

## Overview
Android remote-controller app for the Hexapod robot.  Connects via WiFi TCP to the
`CommandServer` running on the Hexapod Server app.  Landscape-locked, gaming aesthetic,
three horizontally paged screens.

---

## App Layout — HorizontalPager (3 pages)

```
◀ swipe ──────────────────────────────────────────── swipe ▶
┌──────────────┬──────────────────────────────┬──────────────┐
│  PAGE 0      │  PAGE 1  ← default           │  PAGE 2      │
│  Waypoint    │  Controller                  │  Config      │
│  Map         │  (dual joystick + gait HUD)  │  Screen      │
└──────────────┴──────────────────────────────┴──────────────┘
```

Page indicator: 3 subtle dots in top-right corner (active = neon cyan).

---

## Screen Design System

| Token        | Value        | Usage                              |
|--------------|--------------|------------------------------------|
| BG           | #0D0F14      | Full-screen background             |
| PANEL        | #12161E      | Card / panel backgrounds           |
| ACCENT_CYAN  | #00E5FF      | Active joystick thumb, highlights  |
| ACCENT_GREEN | #00FF87      | Connected / OK indicators          |
| AMBER        | #FFB300      | Warnings, RIPPLE gait button       |
| RED_HALT     | #FF1744      | STOP button, error states          |
| LABEL        | #546E7A      | Dim labels                         |
| VALUE        | #ECEFF1      | Live readout values                |
| BORDER_DIM   | #1E2A35      | Card stroke when idle              |
| BORDER_GLOW  | #00E5FF55    | Card stroke when active / touched  |

Font: `Rajdhani` (gaming-style, narrow, high-legibility at small sizes).
Use `FontFamily.Monospace` for live numeric readouts only.

All interactive surfaces: min touch target 48 dp; haptic feedback on press.

---

## File Checklist

### Dependencies to add in `app/build.gradle.kts`
- [ ] `androidx.lifecycle:lifecycle-viewmodel-compose` — ViewModel integration
- [ ] `androidx.lifecycle:lifecycle-runtime-compose` — `collectAsStateWithLifecycle`
- [ ] `androidx.compose.foundation:foundation` — HorizontalPager, gestures
- [ ] `com.google.accompanist:accompanist-pager` (or Compose Foundation pager ≥ 1.4)
- [ ] `org.osmdroid:osmdroid-android` — map tiles for waypoint screen
- [ ] `org.osmdroid:osmdroid-wms` (optional, WMS satellite overlay)
- [ ] `androidx.datastore:datastore-preferences` — persist server IP/port/prefs
- [ ] `org.jetbrains.kotlinx:kotlinx-coroutines-android` — explicit version
- [ ] `com.google.code.gson:gson` OR `org.jetbrains.kotlinx:kotlinx-serialization-json` — TCP command serialization
- [ ] Google fonts dependency for Rajdhani (`androidx.compose.ui:ui-text-google-fonts`)

### Permissions to add in `AndroidManifest.xml`
- [ ] `INTERNET` — WiFi TCP connection
- [ ] `ACCESS_NETWORK_STATE` — check WiFi connectivity before connect
- [ ] `ACCESS_FINE_LOCATION` — GPS for live position dot on map (optional)
- [ ] `ACCESS_COARSE_LOCATION` — coarse fallback
- [ ] Lock orientation to landscape: `android:screenOrientation="sensorLandscape"`
- [ ] Keep screen on while controller is active: `android:keepScreenOn="true"` via `FLAG_KEEP_SCREEN_ON` in code
- [ ] `android:launchMode="singleTop"` on MainActivity

### `gradle/libs.versions.toml`
- [ ] Add version entries for all new dependencies above
- [ ] Bump `composeBom` to latest stable if needed for pager support

---

## Source Files

### `model/MotionCommand.kt`
- [ ] Data class: `forward: Float (-1..1)`, `lateral: Float (-1..1)`, `turn: Float (-1..1)`, `gaitType: GaitType`, `speedLevel: SpeedLevel`
- [ ] `GaitType` enum: TRIPOD, RIPPLE, WAVE
- [ ] `SpeedLevel` enum: SLOW, MEDIUM, FAST (mirrors server-side)
- [ ] `fun toJson(): String` — serialize for TCP transmission
- [ ] Companion `fun stop(): MotionCommand` — zero-vector convenience

### `model/Waypoint.kt`
- [ ] Data class: `id: UUID`, `lat: Double`, `lon: Double`, `label: String`, `radiusM: Float = 1.0f`
- [ ] `fun toJson()` / `companion fun fromJson()` — for file save/load and server send
- [ ] `fun distanceTo(other: Waypoint): Float` — Haversine formula

### `model/AppSettings.kt`
- [ ] Data class holding all user preferences: `serverIp`, `serverPort`, `defaultGaitType`, `defaultSpeedLevel`, `touchSafetyEnabled`, `hapticEnabled`, `mapTileSource (OSM | SATELLITE)`
- [ ] DataStore `Preferences` keys — one constant per field
- [ ] `AppSettingsRepository`: coroutine-based load / save via DataStore
- [ ] Singleton or DI-provided instance

### `network/CommandProtocol.kt`
- [ ] `object CommandProtocol`
- [ ] `fun encode(cmd: MotionCommand): ByteArray` — matches the format expected by `CommandServer` on the server side (check `CommandServer.kt` in Hexapod_Server for exact protocol — likely newline-delimited JSON)
- [ ] `fun encodeWaypoints(list: List<Waypoint>): ByteArray` — bulk waypoint upload frame
- [ ] `fun encodeStop(): ByteArray` — convenience
- [ ] `const val FRAME_TERMINATOR = '\n'.code.toByte()` — if text protocol
- [ ] Document the wire format here with byte-level comments

### `network/HexapodConnection.kt`
- [ ] `class HexapodConnection(private val scope: CoroutineScope)`
- [ ] `val isConnected: StateFlow<Boolean>`
- [ ] `val lastError: StateFlow<String?>`
- [ ] `suspend fun connect(ip: String, port: Int): Boolean` — opens `Socket`, sets `SO_TIMEOUT`, runs reader loop in `scope`
- [ ] `fun disconnect()` — closes socket, cancels reader job
- [ ] `suspend fun send(cmd: MotionCommand)` — encodes + writes on IO dispatcher; swallows and exposes socket errors to `lastError`
- [ ] `suspend fun sendWaypoints(list: List<Waypoint>)` — sends waypoint path to server
- [ ] Auto-reconnect strategy: exponential back-off up to 30 s, reset on manual connect()
- [ ] Receive loop: parse any telemetry / ACK frames the server sends back (server currently doesn't send data but leave the hook)

### `HexapodClientViewModel.kt`
- [ ] `class HexapodClientViewModel(app: Application) : AndroidViewModel(app)`
- [ ] Owns `HexapodConnection` and `AppSettingsRepository`
- [ ] `val isConnected: StateFlow<Boolean>` — from connection
- [ ] `val lastError: StateFlow<String?>` — from connection
- [ ] `val settings: StateFlow<AppSettings>` — from repository
- [ ] `val currentCommand: StateFlow<MotionCommand>` — last dispatched command
- [ ] `val waypoints: StateFlow<List<Waypoint>>` — editable waypoint list
- [ ] `val isSendingWaypoints: StateFlow<Boolean>`
- [ ] `fun connect()` — reads IP/port from settings, calls connection.connect()
- [ ] `fun disconnect()`
- [ ] `fun sendCommand(cmd: MotionCommand)` — debounced at 50 ms (matches server 20 Hz loop); calls connection.send() on IO
- [ ] `fun stop()` — send zero command immediately, no debounce
- [ ] `fun setGaitType(type: GaitType)`
- [ ] `fun setSpeedLevel(level: SpeedLevel)`
- [ ] `fun addWaypoint(lat: Double, lon: Double, label: String)`
- [ ] `fun removeWaypoint(id: UUID)`
- [ ] `fun reorderWaypoints(from: Int, to: Int)`
- [ ] `fun clearWaypoints()`
- [ ] `fun sendWaypointPath()` — sends full list; sets isSendingWaypoints while in-flight
- [ ] `fun saveSettings(settings: AppSettings)` — persists via repository
- [ ] Command rate-limiter: use `MutableSharedFlow(extraBufferCapacity=1, onBufferOverflow=DROP_OLDEST)` + `conflate()` so joystick floods don't queue

### `ui/theme/Color.kt`
- [ ] Replace default palette with gaming theme tokens listed in Design System above

### `ui/theme/Type.kt`
- [ ] Add Rajdhani font family via Google Fonts provider
- [ ] Define `Typography` using Rajdhani for all body/label/title styles
- [ ] Monospace style for telemetry readouts

### `ui/theme/Theme.kt`
- [ ] Dark-only theme (no light mode — controller app is always dark)
- [ ] Force `darkColorScheme` — no `isSystemInDarkTheme()` branch needed

### `MainActivity.kt`
- [ ] Lock to landscape: `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
- [ ] `FLAG_KEEP_SCREEN_ON` — set on window while servo is connected; clear on disconnect
- [ ] `FLAG_FULLSCREEN` / `WindowInsetsController.hide(SYSTEM_BARS)` — immersive gaming mode
- [ ] Instantiate `HexapodClientViewModel` via `viewModels()`
- [ ] Set content: `HexapodClientTheme { AppPager(vm) }`
- [ ] Handle back press: return to controller page if not already there; exit only from controller page

### `ui/AppPager.kt`  ← NEW FILE
- [ ] `@Composable fun AppPager(vm: HexapodClientViewModel)`
- [ ] `HorizontalPager(pageCount = 3, initialPage = 1)` — start on controller
- [ ] Wrap in `Box` so page indicator dots overlay the content
- [ ] Page 0 → `WaypointMapScreen(vm)`
- [ ] Page 1 → `ControllerScreen(vm)`
- [ ] Page 2 → `ConfigScreen(vm)`
- [ ] Page indicator: 3 dots top-right corner; active dot = `ACCENT_CYAN`, inactive = `LABEL` at 40% alpha; 6 dp diameter, 6 dp gap
- [ ] Swipe sensitivity: allow swipe only from left/right 15% edge of screen to prevent accidental swipe while joysticking

---

## Page 1 — Controller Screen

### `ui/controller/ControllerScreen.kt`
- [ ] Full-bleed dark background (`BG`)
- [ ] `BoxWithConstraints` to compute absolute positions for landscape layout
- [ ] **Layout (landscape, fixed regions)**:
  ```
  ┌─────────────────────────────────────────────────────────────────┐
  │  [status dot · IP]              [STOP]        [TRIPOD][RIPPLE][WAVE] │
  │                                                                  │
  │      LEFT STICK                              RIGHT STICK         │
  │   (turn axis only)                       (forward/back only)    │
  │                                                                  │
  │   [speed ring                                     ]             │
  └─────────────────────────────────────────────────────────────────┘
  ```
- [ ] Left stick: horizontal axis → turn (`turn` in MotionCommand); vertical axis ignored (locked to zero)
- [ ] Right stick: vertical axis → forward/backward (`forward`); horizontal axis ignored (locked to zero)
- [ ] Collect `isConnected`, `currentCommand`, `gaitType`, `speedLevel` from VM
- [ ] On any joystick change → `vm.sendCommand(...)` with the merged values

### `ui/controller/VirtualJoystick.kt`
- [ ] `@Composable fun VirtualJoystick(axis: JoystickAxis, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier)`
- [ ] `enum class JoystickAxis { HORIZONTAL_ONLY, VERTICAL_ONLY, FREE }` — constrains thumb movement
- [ ] Canvas-drawn: outer ring (static, dim), inner dead-zone ring (5% radius), draggable thumb circle
- [ ] Outer ring: 140 dp diameter, stroke 2 dp, color `BORDER_DIM`, glow `BORDER_GLOW` when touched
- [ ] Thumb: 36 dp diameter, filled `ACCENT_CYAN` while active, `LABEL` when idle
- [ ] Thumb moves only along constrained axis; returns to center on pointer up (spring animation)
- [ ] Dead-zone: ±8% of radius mapped to 0.0 output
- [ ] Output normalised -1.0..1.0 (already dead-zone-applied)
- [ ] Haptic: `HapticFeedbackType.TextHandleMove` while dragging, `LongPress` on first touch
- [ ] **Speed ring** (shown on left stick when `axis == HORIZONTAL_ONLY`):
  - Concentric colored arc inside the base circle representing current speed level
  - Tap segments to cycle SLOW → MEDIUM → FAST
  - SLOW = 1/3 arc amber; MEDIUM = 2/3 arc cyan; FAST = full arc green

### `ui/controller/GaitButtonRow.kt`
- [ ] `@Composable fun GaitButtonRow(selectedGait: GaitType, onGaitSelected: (GaitType) -> Unit, onStop: () -> Unit)`
- [ ] Three pill buttons: TRIPOD (cyan), RIPPLE (amber), WAVE (green)
- [ ] Active button: filled with glow border + slight scale-up animation
- [ ] Inactive button: outline only, label at 60% alpha
- [ ] STOP button: centered between sticks, circular, `RED_HALT`, pulsing glow animation when robot is walking
- [ ] All buttons: minimum 56 dp height, 16 dp corner radius, `Rajdhani` bold font, 13 sp
- [ ] Haptic on each press

### `ui/controller/StatusBar.kt`  ← NEW FILE
- [ ] Slim top strip showing: connection dot (green/amber/red), IP:port, battery voltage (if server sends telemetry), current gait type
- [ ] 24 dp height, full width
- [ ] "NOT CONNECTED" state: amber dot, tap anywhere on bar → navigates to Config screen

---

## Page 0 — Waypoint Map Screen

### `ui/map/WaypointMapScreen.kt`
- [ ] `@Composable fun WaypointMapScreen(vm: HexapodClientViewModel)`
- [ ] Embed `AndroidView` wrapping `MapView` (osmdroid)
- [ ] Map init: tile source = `TileSourceFactory.MAPNIK` (OSM street) default; toggle button for `TileSourceFactory.USGS_SAT` or custom Esri satellite WMS
- [ ] Satellite overlay: use `TilesOverlay` with `XYTileSource` pointing to Esri World Imagery tiles: `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}` — CHECK Esri ToS before using in production
- [ ] Map controls: +/- zoom buttons, compass, scale bar — all styled to match dark theme (custom drawable tint)
- [ ] **Waypoint markers**: custom icon — numbered hexagon with neon border; tap to show delete option
- [ ] **Add waypoint**: long-press on map → bottom sheet with label text input + confirm/cancel
- [ ] **Waypoint list panel**: collapsible side panel (right side, landscape) showing ordered list with drag-handles; tap to center map on waypoint
- [ ] **Path polyline**: cyan dashed line connecting waypoints in order
- [ ] **Robot position**: live dot (if GPS available from server telemetry) — pulsing green circle; heading arrow if IMU yaw available
- [ ] **Action bar** (bottom strip): [CLEAR ALL] [SEND TO ROBOT] [▶ START NAV] — SEND and START disabled until connected
- [ ] `DisposableEffect` to properly call `mapView.onResume()` / `mapView.onPause()` / `mapView.onDetach()`

### `ui/map/WaypointEditorSheet.kt`  ← NEW FILE
- [ ] Bottom sheet for new waypoint creation (label field + confirm)
- [ ] Edit existing: same sheet with pre-filled label + DELETE option
- [ ] Validate: non-empty label, max 20 chars

---

## Page 2 — Configuration Screen

### `ui/config/ConfigScreen.kt`
- [ ] `@Composable fun ConfigScreen(vm: HexapodClientViewModel)`
- [ ] Header: "CONFIGURATION" in gaming style
- [ ] **Connection card**:
  - IP address text field (keyboard type = IP address)
  - Port number field (default matching `SERVER_PORT` from Hexapod Server — check CommandServer.kt)
  - [CONNECT] / [DISCONNECT] button; spinner while connecting
  - Last error message in red below button
- [ ] **Control settings card**:
  - Default gait type selector (segmented: TRIPOD | RIPPLE | WAVE)
  - Default speed selector (segmented: SLOW | MEDIUM | FAST)
  - Joystick dead-zone slider (5%–20%)
  - Joystick send rate (10 Hz / 20 Hz / 30 Hz) — higher = more responsive but more WiFi traffic
- [ ] **Safety card**:
  - Touch sensor safety toggle (mirrors server-side setting — requires a server config command)
  - Max speed cap toggle ("Beginner mode" locks to SLOW)
- [ ] **Map card**:
  - Tile source toggle: Street / Satellite
  - [CLEAR TILE CACHE] button
- [ ] **About card**: app version, server version (if reported), GitHub link
- [ ] All settings auto-save to DataStore on change (no explicit Save button)

---

## Cross-cutting Concerns

### Haptic feedback
- [ ] Create `HapticManager` utility that wraps `Vibrator` / `VibrationEffect` (API 26+) and `HapticFeedbackConstants` for Compose
- [ ] Pattern: short click (20 ms) on button tap; double-pulse (10+10 ms) on gait change; long (200 ms) on STOP; error pattern (3× 50 ms) on disconnect

### Connection lifecycle
- [ ] Auto-connect on app start if saved IP is non-empty
- [ ] Show `Snackbar` (or custom toast) on connect success / fail
- [ ] On WiFi disconnect: show banner, stop sending commands, keep UI responsive
- [ ] ViewModel survives screen rotation (landscape lock makes this less critical but still correct)

### Accessibility
- [ ] All interactive elements have `contentDescription`
- [ ] Joystick: accessibility action to set value programmatically (for switch-access users)

### Testing
- [ ] Unit tests: `CommandProtocol.encode()` round-trip, `Waypoint.distanceTo()`, debounce logic
- [ ] Instrumented: joystick drag simulation, pager swipe navigation

---

## Implementation Order (suggested)
1. Dependencies + Manifest + Theme
2. `MotionCommand`, `Waypoint`, `AppSettings` models
3. `CommandProtocol` + `HexapodConnection` (can be tested without UI)
4. `HexapodClientViewModel`
5. `AppPager` + page routing
6. `VirtualJoystick` (standalone, testable)
7. `ControllerScreen` wiring sticks to VM
8. `ConfigScreen`
9. `WaypointMapScreen` (most complex — depends on osmdroid)
10. Polish: animations, haptics, gaming chrome
