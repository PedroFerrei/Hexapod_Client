package com.example.hexapod_client.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.hexapod_client.model.Waypoint
import com.example.hexapod_client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointEditorSheet(
    lat:              Double,
    lon:              Double,
    existingWaypoint: Waypoint? = null,
    onConfirm:        (label: String) -> Unit,
    onDelete:         (() -> Unit)? = null,
    onDismiss:        () -> Unit
) {
    val isEdit   = existingWaypoint != null
    var label    by remember(existingWaypoint) { mutableStateOf(existingWaypoint?.label ?: "") }
    var hasError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = PanelColor,
        contentColor     = ValueColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text  = if (isEdit) "EDIT WAYPOINT" else "NEW WAYPOINT",
                style = MaterialTheme.typography.titleMedium,
                color = AccentCyan
            )

            Text(
                text  = "%.6f, %.6f".format(lat, lon),
                style = MaterialTheme.typography.labelSmall,
                color = LabelColor
            )

            OutlinedTextField(
                value         = label,
                onValueChange = { if (it.length <= 20) { label = it; hasError = false } },
                label         = { Text("Label (max 20 chars)", color = LabelColor) },
                isError       = hasError,
                supportingText = if (hasError) { { Text("Label cannot be empty", color = RedHalt) } } else null,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (label.isBlank()) hasError = true
                    else { onConfirm(label.trim()); onDismiss() }
                }),
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentCyan,
                    unfocusedBorderColor = BorderDim,
                    focusedTextColor     = ValueColor,
                    unfocusedTextColor   = ValueColor,
                    cursorColor          = AccentCyan
                )
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEdit && onDelete != null) {
                    OutlinedButton(
                        onClick  = { onDelete(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedHalt),
                        border   = BorderStroke(1.dp, RedHalt)
                    ) { Text("DELETE") }
                }

                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f),
                    border   = BorderStroke(1.dp, BorderDim)
                ) { Text("CANCEL", color = LabelColor) }

                Button(
                    onClick  = {
                        if (label.isBlank()) hasError = true
                        else { onConfirm(label.trim()); onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                    enabled  = label.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan, contentColor = BgColor
                    )
                ) { Text(if (isEdit) "SAVE" else "ADD") }
            }
        }
    }
}
