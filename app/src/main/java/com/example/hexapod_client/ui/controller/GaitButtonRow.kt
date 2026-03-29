package com.example.hexapod_client.ui.controller

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexapod_client.model.GaitMode
import com.example.hexapod_client.model.GaitType
import com.example.hexapod_client.ui.theme.*

@Composable
fun GaitButtonRow(
    selectedGait:   GaitType,
    selectedMode:   GaitMode,
    isWalking:      Boolean,
    onGaitSelected: (GaitType) -> Unit,
    onModeSelected: (GaitMode) -> Unit,
    onStop:         () -> Unit,
    hapticEnabled:  Boolean  = true,
    modifier:       Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        // ---- Gait type buttons ----
        GaitType.entries.forEach { gait ->
            val active    = gait == selectedGait
            val gaitColor = when (gait) {
                GaitType.TRIPOD -> AccentCyan
                GaitType.RIPPLE -> Amber
                GaitType.WAVE   -> AccentGreen
            }
            val scale by animateFloatAsState(
                targetValue   = if (active) 1.06f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label         = "scale_${gait.name}"
            )

            Button(
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onGaitSelected(gait)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .scale(scale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) gaitColor.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor   = if (active) gaitColor else gaitColor.copy(alpha = 0.55f)
                ),
                border = BorderStroke(
                    width = if (active) 1.5.dp else 1.dp,
                    color = if (active) gaitColor else gaitColor.copy(alpha = 0.35f)
                ),
                shape          = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(gait.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(2.dp))

        // ---- Body mode buttons (2 × 2 grid) ----
        val modeEntries = GaitMode.entries
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modeEntries.take(2).forEach { mode ->
                ModeChip(mode, mode == selectedMode, haptic, hapticEnabled) { onModeSelected(mode) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modeEntries.drop(2).forEach { mode ->
                ModeChip(mode, mode == selectedMode, haptic, hapticEnabled) { onModeSelected(mode) }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---- STOP button ----
        val stopAlpha = if (isWalking) pulseAlpha else 0.5f
        Button(
            onClick = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onStop()
            },
            modifier       = Modifier.size(68.dp),
            shape          = CircleShape,
            colors         = ButtonDefaults.buttonColors(
                containerColor = RedHalt.copy(alpha = stopAlpha * 0.25f),
                contentColor   = RedHalt.copy(alpha = stopAlpha)
            ),
            border         = BorderStroke(1.5.dp, RedHalt.copy(alpha = stopAlpha)),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("■", fontSize = 20.sp, color = RedHalt.copy(alpha = stopAlpha))
        }
    }
}

@Composable
private fun ModeChip(
    mode:          GaitMode,
    active:        Boolean,
    haptic:        HapticFeedback,
    hapticEnabled: Boolean,
    onClick:       () -> Unit
) {
    val modeColor = when (mode) {
        GaitMode.STANDARD  -> LabelColor
        GaitMode.OFFROAD   -> Amber
        GaitMode.QUADRUPED -> AccentGreen
        GaitMode.BLOCK     -> AccentCyan
    }
    OutlinedButton(
        onClick = { if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        modifier = Modifier.height(26.dp),
        colors   = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) modeColor.copy(alpha = 0.18f) else Color.Transparent,
            contentColor   = if (active) modeColor else modeColor.copy(alpha = 0.5f)
        ),
        border         = BorderStroke(if (active) 1.5.dp else 1.dp,
                             if (active) modeColor else modeColor.copy(alpha = 0.3f)),
        shape          = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
    ) {
        Text(mode.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
