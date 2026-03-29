package com.example.hexapod_client.ui.controller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hexapod_client.HexapodClientViewModel
import com.example.hexapod_client.model.MotionCommand
import com.example.hexapod_client.model.SpeedLevel
import com.example.hexapod_client.ui.theme.AccentCyan
import com.example.hexapod_client.ui.theme.AccentGreen
import com.example.hexapod_client.ui.theme.Amber
import com.example.hexapod_client.ui.theme.BgColor
import com.example.hexapod_client.ui.theme.RedHalt

// Yaw stick size — smaller than the 140dp main sticks to signal a secondary control.
private const val YAW_STICK_DP = 96

private fun Float.format2() = "%.2f".format(this)

@Composable
fun ControllerScreen(
    vm:                 HexapodClientViewModel,
    onNavigateToConfig: () -> Unit,
    modifier:           Modifier = Modifier
) {
    val isConnected    by vm.isConnected.collectAsStateWithLifecycle()
    val currentCommand by vm.currentCommand.collectAsStateWithLifecycle()
    val gaitType       by vm.gaitType.collectAsStateWithLifecycle()
    val speedLevel     by vm.speedLevel.collectAsStateWithLifecycle()
    val gaitMode       by vm.gaitMode.collectAsStateWithLifecycle()
    val settings       by vm.settings.collectAsStateWithLifecycle()
    val hexapodPose    by vm.hexapodPose.collectAsStateWithLifecycle()

    val isNavigating  = hexapodPose?.isNavigating == true
    val isWalking     = isConnected && currentCommand.isMoving
    val deadZonePct   = settings.joystickDeadZonePct
    val hapticEnabled = settings.hapticEnabled

    val batteryPct    = hexapodPose?.batteryPct   ?: -1
    val voltageV      = hexapodPose?.voltageV     ?: -1f
    val batteryLevel  = hexapodPose?.batteryLevel ?: "UNKNOWN"

    // LEFT   stick: Y → Vx  (forward/back)   X → Vy (strafe, negated)
    // RIGHT  stick: Y → Vz  (body height)    X → roll (body tilt, negated)
    //   roll = -x: stick-right → right side down, left side up (server: bodyRoll × 15°).
    // YAW    stick: X → yaw (turn rate)  — normalized → server × MAX_YAW_DEG_S = ±60°/s
    var vx   by remember { mutableFloatStateOf(0f) }
    var vy   by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var vz   by remember { mutableFloatStateOf(0f) }
    var yaw  by remember { mutableFloatStateOf(0f) }

    fun dispatch() {
        if (isConnected) {
            vm.sendCommand(
                MotionCommand(
                    vx         = vx.coerceIn(-1f, 1f),
                    vy         = vy.coerceIn(-1f, 1f),
                    vz         = vz.coerceIn(-1f, 1f),
                    yaw        = yaw.coerceIn(-1f, 1f),
                    roll       = roll.coerceIn(-1f, 1f),
                    gaitType   = gaitType,
                    speedLevel = speedLevel,
                    gaitMode   = gaitMode
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        StatusBar(
            isConnected           = isConnected,
            serverIp              = settings.serverIp,
            serverPort            = settings.serverPort,
            gaitType              = gaitType,
            batteryPct            = batteryPct,
            voltageV              = voltageV,
            batteryLevel          = batteryLevel,
            onTapWhenDisconnected = onNavigateToConfig
        )

        // Battery alert banner — shown only when the server reports LOW or worse
        if (isConnected && batteryLevel in setOf("LOW", "CRITICAL", "EMERGENCY")) {
            val (bannerBg, bannerText, message) = when (batteryLevel) {
                "EMERGENCY" -> Triple(RedHalt,  Color.White,          "⚡ BATTERY EMERGENCY — servo power will cut automatically")
                "CRITICAL"  -> Triple(Color(0xFFFF6D00), Color.White, "⚠ BATTERY CRITICAL ${voltageV.format2()}V — stop robot now")
                else        -> Triple(Amber,    Color(0xFF0D0F14),    "Battery low ${voltageV.format2()}V — finish current task")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerBg)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text       = message,
                    color      = bannerText,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Left side — left stick + speed column to its right
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left stick:
                //   Y → Vx (forward / backward)
                //   X → Vy (strafe left / right) — negated so stick-right = robot-right
                VirtualJoystick(
                    axis          = JoystickAxis.FREE,
                    onValueChange = { x, y ->
                        vx = y
                        vy = -x   // mirrored: positive X on screen → strafe right (+vy)
                        dispatch()
                    },
                    showSpeedRing = true,
                    speedLevel    = speedLevel,
                    onSpeedTap    = { vm.setSpeedLevel(it) },
                    deadZonePct   = deadZonePct,
                    hapticEnabled = hapticEnabled
                )

                SpeedColumn(
                    selectedSpeed   = speedLevel,
                    onSpeedSelected = { vm.setSpeedLevel(it) },
                    hapticEnabled   = hapticEnabled
                )
            }

            // Center — gait type + body mode + stop
            GaitButtonRow(
                selectedGait   = gaitType,
                selectedMode   = gaitMode,
                isWalking      = isWalking,
                onGaitSelected = { vm.setGaitType(it) },
                onModeSelected = { vm.setGaitMode(it) },
                onStop         = {
                    vx = 0f; vy = 0f; roll = 0f; vz = 0f; yaw = 0f
                    if (isNavigating) vm.stopMission() else vm.stop()
                    vm.disconnect()
                },
                hapticEnabled = hapticEnabled,
                modifier      = Modifier.width(120.dp)
            )

            // Right side — body stick (top) + yaw stick (bottom)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Right stick:
                //   X → roll (body tilt): negated so stick-right = right side down.
                //              Server applies: bodyRoll × 15° around the forward axis.
                //   Y → Vz  (body height): 2.5× so full range ±30 mm reached at 40% deflection.
                VirtualJoystick(
                    axis          = JoystickAxis.FREE,
                    onValueChange = { x, y ->
                        roll = -x   // stick-left → left side down; stick-right → right side down
                        vz   = (y * 2.5f).coerceIn(-1f, 1f)
                        dispatch()
                    },
                    deadZonePct   = deadZonePct,
                    hapticEnabled = hapticEnabled
                )

                // Yaw stick — horizontal only, smaller, Amber accent
                //   X → yaw: right(+) = turn right; left(-) = turn left
                //   Wire format: yaw × MAX_YAW_DEG_S = ±60°/s sent as "turn" field
                YawStick(
                    deadZonePct   = deadZonePct,
                    hapticEnabled = hapticEnabled,
                    onYaw         = { v -> yaw = v; dispatch() },
                    onRelease     = { yaw = 0f; dispatch() }
                )
            }
        }
    }
}

/**
 * Small horizontal-only joystick for yaw (turn rate) control.
 *
 * Placed below the right body-pose stick.  Amber accent distinguishes it
 * from the main sticks (AccentCyan).  Auto-centres on finger release so
 * the robot stops turning when the user lets go.
 *
 * [onYaw]    — called while dragging; value in [-1, 1] (positive = turn right)
 * [onRelease] — called on drag end / cancel; UI should reset yaw to 0 and re-dispatch
 */
@Composable
private fun YawStick(
    deadZonePct:   Int,
    hapticEnabled: Boolean,
    onYaw:         (Float) -> Unit,
    onRelease:     () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ← · YAW · → label strip
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("←", color = Amber.copy(alpha = 0.6f), fontSize = 10.sp)
            Text(
                "YAW",
                color         = Amber.copy(alpha = 0.8f),
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text("→", color = Amber.copy(alpha = 0.6f), fontSize = 10.sp)
        }

        VirtualJoystick(
            axis          = JoystickAxis.HORIZONTAL_ONLY,
            sizeDp        = YAW_STICK_DP,
            accentColor   = Amber,
            onValueChange = { x, _ ->
                if (x != 0f) onYaw(x) else onRelease()
            },
            deadZonePct   = deadZonePct,
            hapticEnabled = hapticEnabled
        )
    }
}

/** Vertical column of SLOW / MEDIUM / FAST speed buttons shown to the left of the left joystick. */
@Composable
private fun SpeedColumn(
    selectedSpeed:   SpeedLevel,
    onSpeedSelected: (SpeedLevel) -> Unit,
    hapticEnabled:   Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Column(
        verticalArrangement   = Arrangement.spacedBy(6.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        SpeedLevel.entries.forEach { level ->
            val active     = level == selectedSpeed
            val speedColor = when (level) {
                SpeedLevel.SLOW   -> Amber
                SpeedLevel.MEDIUM -> AccentCyan
                SpeedLevel.FAST   -> AccentGreen
            }
            OutlinedButton(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSpeedSelected(level)
                },
                modifier = Modifier
                    .width(44.dp)
                    .height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (active) speedColor.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor   = if (active) speedColor else speedColor.copy(alpha = 0.5f)
                ),
                border         = BorderStroke(
                    if (active) 1.5.dp else 1.dp,
                    if (active) speedColor else speedColor.copy(alpha = 0.3f)
                ),
                shape          = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
            ) {
                Text(
                    level.name.take(3),
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
