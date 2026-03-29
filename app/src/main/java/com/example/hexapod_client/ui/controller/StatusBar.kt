package com.example.hexapod_client.ui.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexapod_client.model.GaitType
import com.example.hexapod_client.ui.theme.*

@Composable
fun StatusBar(
    isConnected: Boolean,
    serverIp: String,
    serverPort: Int,
    gaitType: GaitType,
    batteryPct: Int,
    voltageV: Float,
    batteryLevel: String,
    onTapWhenDisconnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        isConnected           -> AccentGreen
        serverIp.isNotBlank() -> Amber
        else                  -> RedHalt
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(PanelColor)
            .then(
                if (!isConnected) Modifier.clickable { onTapWhenDisconnected() }
                else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Connection dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        // IP / disconnect prompt
        Text(
            text = if (isConnected) "$serverIp:$serverPort"
                   else "NOT CONNECTED — tap to configure",
            color      = if (isConnected) ValueColor else Amber,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.weight(1f)
        )

        if (isConnected) {
            // Gait type
            Text(
                text  = gaitType.name,
                color = when (gaitType) {
                    GaitType.TRIPOD -> AccentCyan
                    GaitType.RIPPLE -> Amber
                    GaitType.WAVE   -> AccentGreen
                },
                fontSize = 10.sp
            )

            // Battery indicator — only shown when the server is sending readings
            if (batteryPct >= 0) {
                Spacer(Modifier.width(6.dp))
                BatteryChip(
                    batteryPct   = batteryPct,
                    voltageV     = voltageV,
                    batteryLevel = batteryLevel
                )
            }
        }
    }
}

@Composable
private fun BatteryChip(
    batteryPct: Int,
    voltageV: Float,
    batteryLevel: String
) {
    val (chipColor, textColor) = when (batteryLevel) {
        "EMERGENCY" -> Color(0xFFFF1744) to Color.White
        "CRITICAL"  -> Color(0xFFFF6D00) to Color.White
        "LOW"       -> Color(0xFFFFB300) to Color(0xFF0D0F14)
        else        -> Color(0xFF1E2A35) to Color(0xFF90A4AE)   // NORMAL / UNKNOWN
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text       = "$batteryPct%",
            color      = textColor,
            fontSize   = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (voltageV > 0f) {
            Text(
                text       = "${"%.2f".format(voltageV)}V",
                color      = textColor,
                fontSize   = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
