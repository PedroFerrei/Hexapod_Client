package com.example.hexapod_client.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hexapod_client.HexapodClientViewModel
import com.example.hexapod_client.model.*
import com.example.hexapod_client.network.NsdDiscovery
import com.example.hexapod_client.ui.theme.*

@Composable
fun ConfigScreen(vm: HexapodClientViewModel, modifier: Modifier = Modifier) {
    val settings       by vm.settings.collectAsStateWithLifecycle()
    val isConnected    by vm.isConnected.collectAsStateWithLifecycle()
    val lastError      by vm.lastError.collectAsStateWithLifecycle()
    val discoveryState by vm.discoveryState.collectAsStateWithLifecycle()

    var ipText   by remember(settings.serverIp)   { mutableStateOf(settings.serverIp) }
    var portText by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CONFIGURATION", style = MaterialTheme.typography.titleLarge, color = AccentCyan)

        // ── Connection ──────────────────────────────────────────────────────
        ConfigCard("CONNECTION") {
            OutlinedTextField(
                value         = ipText,
                onValueChange = { ipText = it },
                label         = { Text("Server IP", color = LabelColor) },
                placeholder   = { Text("192.168.x.x", color = LabelColor) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = textFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = portText,
                onValueChange = { portText = it.filter { c -> c.isDigit() } },
                label         = { Text("Port", color = LabelColor) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine    = true,
                modifier      = Modifier.width(140.dp),
                colors        = textFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            ScanRow(
                state     = discoveryState,
                onScan    = { vm.startDiscovery() },
                onCancel  = { vm.stopDiscovery() },
                onReset   = { vm.resetDiscovery() }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: settings.serverPort
                    vm.saveSettings(settings.copy(serverIp = ipText.trim(), serverPort = port))
                    if (isConnected) vm.disconnect() else vm.connect()
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) RedHalt else AccentCyan,
                    contentColor   = BgColor
                )
            ) {
                Text(if (isConnected) "DISCONNECT" else "CONNECT")
            }
            if (lastError != null) {
                Spacer(Modifier.height(4.dp))
                Text(lastError!!, color = RedHalt, style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── Control ─────────────────────────────────────────────────────────
        ConfigCard("CONTROL") {
            SettingLabel("Default Gait")
            SegmentedRow(
                options  = GaitType.entries.map { it.name },
                selected = settings.defaultGaitType.name,
                onSelect = { vm.saveSettings(settings.copy(defaultGaitType = GaitType.valueOf(it))) }
            )
            Spacer(Modifier.height(10.dp))
            SettingLabel("Dead Zone: ${settings.joystickDeadZonePct}%  — ignores stick movement smaller than this threshold, preventing drift when the thumb doesn't return exactly to center")
            Slider(
                value         = settings.joystickDeadZonePct.toFloat(),
                onValueChange = { vm.saveSettings(settings.copy(joystickDeadZonePct = it.toInt())) },
                valueRange    = 5f..20f,
                steps         = 14,
                colors        = SliderDefaults.colors(
                    thumbColor       = AccentCyan,
                    activeTrackColor = AccentCyan
                )
            )
            Spacer(Modifier.height(8.dp))
            SettingLabel("Send Rate")
            SegmentedRow(
                options  = listOf("10", "20", "30"),
                selected = settings.commandRateHz.toString(),
                onSelect = { vm.saveSettings(settings.copy(commandRateHz = it.toInt())) }
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label          = "Haptic Feedback — vibration on joystick drag and button presses",
                checked        = settings.hapticEnabled,
                onCheckedChange = { vm.saveSettings(settings.copy(hapticEnabled = it)) }
            )
        }

        // ── Safety ──────────────────────────────────────────────────────────
        ConfigCard("SAFETY") {
            SwitchRow(
                label          = "Touch Sensor Safety",
                checked        = settings.touchSafetyEnabled,
                onCheckedChange = { vm.saveSettings(settings.copy(touchSafetyEnabled = it)) }
            )
            SwitchRow(
                label          = "Beginner Mode (cap to SLOW)",
                checked        = settings.beginnerMode,
                onCheckedChange = { vm.saveSettings(settings.copy(beginnerMode = it)) }
            )
        }

        // ── About ───────────────────────────────────────────────────────────
        ConfigCard("ABOUT") {
            Text("Hexapod Client  v1.0", color = ValueColor, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("github.com/PedroFerrei/Hexapod_Client", color = AccentCyan, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text("Server app", color = LabelColor, style = MaterialTheme.typography.labelSmall)
            Text("github.com/PedroFerrei/Hexapod_Server", color = AccentCyan, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text("Created by Pedro Ferreira & Claude (Anthropic)", color = LabelColor, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(2.dp))
            Text("Apache License 2.0", color = LabelColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun ConfigCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = PanelColor),
        border   = BorderStroke(1.dp, BorderDim),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = LabelColor, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = LabelColor, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SegmentedRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            val active = opt == selected
            OutlinedButton(
                onClick        = { onSelect(opt) },
                modifier       = Modifier.height(36.dp),
                colors         = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (active) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor   = if (active) AccentCyan else LabelColor
                ),
                border         = BorderStroke(1.dp, if (active) AccentCyan else BorderDim),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(opt, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ValueColor, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor = BgColor,
                checkedTrackColor = AccentCyan
            )
        )
    }
}

@Composable
private fun ScanRow(
    state:    NsdDiscovery.State,
    onScan:   () -> Unit,
    onCancel: () -> Unit,
    onReset:  () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (state) {
            is NsdDiscovery.State.Idle -> {
                OutlinedButton(
                    onClick = onScan,
                    border  = BorderStroke(1.dp, AccentCyan),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                ) { Text("SCAN", style = MaterialTheme.typography.labelLarge) }
                Text("Auto-discover server", color = LabelColor,
                    style = MaterialTheme.typography.labelMedium)
            }
            is NsdDiscovery.State.Scanning -> {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    color     = AccentCyan,
                    strokeWidth = 2.dp
                )
                Text("Scanning…", color = AccentCyan,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onCancel,
                    border  = BorderStroke(1.dp, BorderDim),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = LabelColor)
                ) { Text("CANCEL", style = MaterialTheme.typography.labelLarge) }
            }
            is NsdDiscovery.State.Found -> {
                Text(
                    "Found: ${state.name}",
                    color  = AccentGreen,
                    style  = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onReset,
                    border  = BorderStroke(1.dp, BorderDim),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = LabelColor)
                ) { Text("CLEAR", style = MaterialTheme.typography.labelLarge) }
            }
            is NsdDiscovery.State.Error -> {
                Text(
                    state.message,
                    color    = RedHalt,
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onScan,
                    border  = BorderStroke(1.dp, AccentCyan),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                ) { Text("RETRY", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentCyan,
    unfocusedBorderColor = BorderDim,
    focusedTextColor     = ValueColor,
    unfocusedTextColor   = ValueColor,
    cursorColor          = AccentCyan
)
