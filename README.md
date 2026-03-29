# Hexapod Client

Android remote-controller app for the [Hexapod Server](https://github.com/PedroFerrei/Hexapod_Server). Connects over Wi-Fi TCP and lets you drive the hexapod robot with dual virtual joysticks, switch gaits, plan waypoint paths on a map, and tune connection settings.

![App screenshot](Client.png)

---

## Related Projects

- [PedroFerrei/Hexapod_Server](https://github.com/PedroFerrei/Hexapod_Server) — the Android app running on the robot's phone, which this client connects to
- [makeyourpet/hexapod](https://github.com/makeyourpet/hexapod) — the hexapod robot design
- [chica-servo2040-simpleDriver](https://github.com/EddieCarrera/chica-servo2040-simpleDriver) — Servo 2040 firmware on the robot

---

## Features

- **Dual virtual joysticks** — left stick for turning, right stick for forward/back
- **Gait selector** — TRIPOD, RIPPLE, WAVE with animated pill buttons
- **Speed control** — SLOW / MEDIUM / FAST via speed ring on the joystick
- **Waypoint map** — OSM/satellite map to plan and send a path to the robot
- **Config screen** — IP/port, default gait/speed, dead-zone, joystick rate, safety toggles
- **Auto-reconnect** — exponential back-off reconnect on Wi-Fi drop
- **Gaming UI** — dark theme, Rajdhani font, neon cyan/green/amber accent colors
- **NSD discovery** — auto-discovers the server on the local network

---

## Architecture

```
HexapodClientViewModel        (central state + command dispatch)
├── HexapodConnection         (TCP socket, send loop, auto-reconnect)
│   └── CommandProtocol       (encodes MotionCommand → wire bytes)
├── AppSettingsRepository     (DataStore-backed user preferences)
├── ClientGpsTracker          (device GPS for live map dot)
└── NsdDiscovery              (mDNS server discovery)

UI — HorizontalPager (3 pages)
├── Page 0: WaypointMapScreen  (osmdroid map, waypoint editor)
├── Page 1: ControllerScreen   (dual joystick, gait HUD, STOP)
└── Page 2: ConfigScreen       (IP/port, settings, about)
```

---

## Building

**Requirements:**
- Android Studio Meerkat or newer
- Android SDK 36
- JDK 11 (bundled with Android Studio)
- The robot must be running [Hexapod Server](https://github.com/PedroFerrei/Hexapod_Server) on the same Wi-Fi network

**Steps:**

1. Clone the repo:
   ```bash
   git clone https://github.com/PedroFerrei/Hexapod_Client.git
   cd Hexapod_Client
   ```

2. Open in Android Studio and let Gradle sync.

3. Build and install:
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

   Or hit **Run** in Android Studio.

---

## Usage

1. Start **Hexapod Server** on the robot's phone and connect it to USB.
2. Open **Hexapod Client** on your controller phone.
3. Swipe to **Config** (page 2), enter the server's IP address and port.
4. Tap **CONNECT** — the status dot turns green.
5. Swipe to **Controller** (page 1) and drive.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
