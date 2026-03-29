package com.example.hexapod_client.ui.controller

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.example.hexapod_client.model.SpeedLevel
import com.example.hexapod_client.ui.theme.*
import kotlin.math.sqrt
import kotlinx.coroutines.launch

// Thumb radius as a fraction of the joystick's own radius — scales with sizeDp automatically.
private const val THUMB_FRACTION = 0.257f   // ≈ 18dp / 70dp (half of default 140dp)
private const val GLOW_FRACTION  = 0.314f   // ≈ 22dp / 70dp

enum class JoystickAxis { HORIZONTAL_ONLY, VERTICAL_ONLY, FREE }

/**
 * Virtual joystick that reports normalized [-1, 1] x/y values.
 *
 * Axis constraints:
 *   HORIZONTAL_ONLY → y always 0; x = right(+) / left(-)
 *   VERTICAL_ONLY   → x always 0; y = up(+) / down(-)
 *   FREE            → both axes active; x = right(+), y = up(+)
 *
 * Dead zone of 8 % is applied independently to each axis before reporting.
 */
@Composable
fun VirtualJoystick(
    axis:          JoystickAxis,
    onValueChange: (x: Float, y: Float) -> Unit,
    modifier:      Modifier = Modifier,
    sizeDp:        Int      = 140,  // overall diameter; scale down for secondary sticks
    accentColor:   Color    = AccentCyan, // thumb/glow color while dragging
    showSpeedRing: Boolean = false,
    speedLevel:    SpeedLevel = SpeedLevel.MEDIUM,
    onSpeedTap:    ((SpeedLevel) -> Unit)? = null,
    deadZonePct:   Int     = 8,    // % of max radius treated as neutral — prevent stick drift
    hapticEnabled: Boolean = true  // vibrate on drag start and during movement
) {
    val haptic = LocalHapticFeedback.current
    val view   = LocalView.current

    // Tracks the thumb position at the last haptic tick so we only buzz every
    // ~8% of the joystick radius rather than every frame (~60 fps).
    var lastHapticOffset by remember { mutableStateOf(Offset.Zero) }

    var rawOffset  by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }

    // Snap to rawOffset while dragging; spring back to 0 on release
    LaunchedEffect(isDragging, rawOffset) {
        if (isDragging) {
            animX.snapTo(rawOffset.x)
            animY.snapTo(rawOffset.y)
        } else {
            launch {
                animX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                                           stiffness   = Spring.StiffnessMedium))
            }
            launch {
                animY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                                           stiffness   = Spring.StiffnessMedium))
            }
        }
    }

    val thumbX = animX.value
    val thumbY = animY.value

    Canvas(
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(axis) {
                val maxR = minOf(size.width, size.height).toFloat() / 2f

                fun deadBand(v: Float): Float {
                    val dz = deadZonePct.coerceIn(0, 40) / 100f
                    return when {
                        v >  dz ->  (v - dz) / (1f - dz)
                        v < -dz ->  (v + dz) / (1f - dz)
                        else    ->  0f
                    }.coerceIn(-1f, 1f)
                }

                // Haptic tick distance threshold: ~8% of the joystick radius in pixels.
                // This fires roughly once every 10–15° of thumb movement — noticeable
                // but not a continuous buzz.
                val hapticThresholdPx = maxR * 0.08f

                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        lastHapticOffset = Offset.Zero
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDragEnd = {
                        isDragging = false
                        rawOffset  = Offset.Zero
                        onValueChange(0f, 0f)
                    },
                    onDragCancel = {
                        isDragging = false
                        rawOffset  = Offset.Zero
                        onValueChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dx = if (axis != JoystickAxis.VERTICAL_ONLY)   dragAmount.x else 0f
                        val dy = if (axis != JoystickAxis.HORIZONTAL_ONLY) dragAmount.y else 0f

                        val candidate = rawOffset + Offset(dx, dy)
                        val dist      = sqrt(candidate.x * candidate.x + candidate.y * candidate.y)
                        rawOffset = if (dist <= maxR) candidate else candidate * (maxR / dist)

                        // x: right = positive; y: up = positive (negate screen Y)
                        val outX = if (axis != JoystickAxis.VERTICAL_ONLY)
                            deadBand(rawOffset.x / maxR) else 0f
                        val outY = if (axis != JoystickAxis.HORIZONTAL_ONLY)
                            deadBand(-rawOffset.y / maxR) else 0f

                        onValueChange(outX, outY)

                        // CLOCK_TICK is API 21+ and implemented on virtually all OEMs —
                        // unlike TextHandleMove which Samsung and others often ignore.
                        // Throttle by distance so it ticks rather than buzzes continuously.
                        if (hapticEnabled) {
                            val ddx = rawOffset.x - lastHapticOffset.x
                            val ddy = rawOffset.y - lastHapticOffset.y
                            if (sqrt(ddx * ddx + ddy * ddy) >= hapticThresholdPx) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                lastHapticOffset = rawOffset
                            }
                        }
                    }
                )
            }
            .then(
                if (showSpeedRing && onSpeedTap != null) {
                    Modifier.pointerInput(speedLevel) {
                        detectTapGestures {
                            val next = when (speedLevel) {
                                SpeedLevel.SLOW   -> SpeedLevel.MEDIUM
                                SpeedLevel.MEDIUM -> SpeedLevel.FAST
                                SpeedLevel.FAST   -> SpeedLevel.SLOW
                            }
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeedTap(next)
                        }
                    }
                } else Modifier
            )
    ) {
        val baseR  = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val thumbCenter = center + Offset(thumbX, thumbY)

        val thumbR = baseR * THUMB_FRACTION
        val glowR  = baseR * GLOW_FRACTION

        // Outer ring
        drawCircle(
            color  = if (isDragging) BorderGlow else BorderDim,
            radius = baseR - 2.dp.toPx(),
            center = center,
            style  = Stroke(width = 2.dp.toPx())
        )

        // Speed ring arcs (left stick only)
        if (showSpeedRing) {
            val arcR    = baseR * 0.72f
            val arcTop  = Offset(center.x - arcR, center.y - arcR)
            val arcSize = Size(arcR * 2, arcR * 2)
            val sw      = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)

            when (speedLevel) {
                SpeedLevel.SLOW   -> drawArc(Amber,       -90f, 120f, false, topLeft = arcTop, size = arcSize, style = sw)
                SpeedLevel.MEDIUM -> drawArc(AccentCyan,  -90f, 240f, false, topLeft = arcTop, size = arcSize, style = sw)
                SpeedLevel.FAST   -> drawArc(AccentGreen, -90f, 360f, false, topLeft = arcTop, size = arcSize, style = sw)
            }
        }

        // Dead-zone ring
        drawCircle(
            color  = LabelColor.copy(alpha = 0.25f),
            radius = baseR * 0.08f,
            center = center,
            style  = Stroke(width = 1.dp.toPx())
        )

        // Thumb glow
        if (isDragging) {
            drawCircle(
                color  = accentColor.copy(alpha = 0.2f),
                radius = glowR,
                center = thumbCenter
            )
        }
        // Thumb fill
        drawCircle(
            color  = if (isDragging) accentColor else LabelColor,
            radius = thumbR,
            center = thumbCenter
        )
    }
}
