package com.example.hexapod_client.ui.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hexapod_client.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------------
// Parsed metadata — extracted from the first ~30 lines of the TXT header
// without reading the whole (potentially large) data section.
// ---------------------------------------------------------------------------
private class MissionFileInfo(val file: File) {

    val sizeKb: Float  = file.length() / 1024f
    val modified: Long = file.lastModified()

    var date         = ""
    var totalPoints  = ""
    var duration     = ""
    var fixedPoints  = ""
    var avgAccuracy  = ""
    var minAccuracy  = ""
    var maxAccuracy  = ""

    init {
        try {
            file.bufferedReader(Charsets.UTF_8).use { r ->
                repeat(35) {
                    val line = r.readLine() ?: return@use
                    when {
                        line.startsWith("Date: ")          -> date        = line.removePrefix("Date: ").trim()
                        line.startsWith("Total points: ")  -> totalPoints = line.removePrefix("Total points: ").trim()
                        line.startsWith("Duration: ")      -> duration    = line.removePrefix("Duration: ").trim()
                        line.contains("Fixed points :")    -> fixedPoints = line.substringAfter("Fixed points : ").trim()
                        line.contains("Accuracy avg :")    -> avgAccuracy = line.substringAfter("Accuracy avg : ").trim()
                        line.contains("Accuracy min :")    -> minAccuracy = line.substringAfter("Accuracy min : ").trim()
                        line.contains("Accuracy max :")    -> maxAccuracy = line.substringAfter("Accuracy max : ").trim()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val displaySize: String get() = if (sizeKb < 1024f)
        "%.1f KB".format(sizeKb) else "%.2f MB".format(sizeKb / 1024f)
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@Composable
fun MissionFilesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var files         by remember { mutableStateOf<List<MissionFileInfo>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var viewingFile   by remember { mutableStateOf<MissionFileInfo?>(null) }
    var deletingFile  by remember { mutableStateOf<MissionFileInfo?>(null) }
    var deletingAll   by remember { mutableStateOf(false) }

    fun loadFiles() {
        isLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val dir = context.getExternalFilesDir(null)
                dir?.listFiles { f ->
                    f.name.startsWith("hexapod_path_") && f.name.endsWith(".txt")
                }?.sortedByDescending { it.lastModified() }
                  ?.map { MissionFileInfo(it) }
                  ?: emptyList()
            }
            files = list
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgColor)
    ) {

        // ---- Header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "MISSION FILES",
                    color     = AccentCyan,
                    fontSize  = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    if (isLoading) "Loading…"
                    else if (files.isEmpty()) "No files saved yet"
                    else "${files.size} file${if (files.size == 1) "" else "s"}  •  " +
                         "%.1f KB total".format(files.sumOf { it.sizeKb.toDouble() }.toFloat()),
                    color    = LabelColor,
                    fontSize = 10.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (files.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { deletingAll = true },
                        modifier = Modifier.height(30.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedHalt),
                        border   = BorderStroke(1.dp, RedHalt),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("DELETE ALL", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = { loadFiles() },
                    modifier = Modifier.height(30.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    border   = BorderStroke(1.dp, AccentCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("REFRESH", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = BorderDim)

        // ---- Content ----
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = AccentCyan
                    )
                }

                files.isEmpty() -> {
                    Column(
                        modifier              = Modifier.align(Alignment.Center),
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        verticalArrangement   = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("No mission files", color = LabelColor, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "Path files are saved automatically\nwhen a navigation mission ends.",
                            color     = LabelColor.copy(alpha = 0.6f),
                            fontSize  = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files, key = { it.file.absolutePath }) { info ->
                            FileCard(
                                info     = info,
                                onView   = { viewingFile  = info },
                                onDelete = { deletingFile = info }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- File viewer ----
    viewingFile?.let { info ->
        FileViewerDialog(info = info, onDismiss = { viewingFile = null })
    }

    // ---- Delete one file ----
    deletingFile?.let { info ->
        AlertDialog(
            onDismissRequest  = { deletingFile = null },
            containerColor    = PanelColor,
            titleContentColor = RedHalt,
            textContentColor  = ValueColor,
            title = { Text("Delete file?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.file.name, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        color = ValueColor)
                    Spacer(Modifier.height(2.dp))
                    Text("${info.totalPoints} pts  •  ${info.displaySize}  •  ${info.date}",
                        color = LabelColor, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("This action cannot be undone.",
                        color = RedHalt.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    info.file.delete()
                    deletingFile = null
                    loadFiles()
                }) { Text("DELETE", color = RedHalt, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingFile = null }) {
                    Text("CANCEL", color = LabelColor)
                }
            }
        )
    }

    // ---- Delete ALL files ----
    if (deletingAll) {
        AlertDialog(
            onDismissRequest  = { deletingAll = false },
            containerColor    = PanelColor,
            titleContentColor = RedHalt,
            textContentColor  = ValueColor,
            title = { Text("Delete ALL files?") },
            text  = {
                Text(
                    "This will permanently delete all ${files.size} mission file${if (files.size == 1) "" else "s"}.\nThis cannot be undone.",
                    color = ValueColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    files.forEach { it.file.delete() }
                    deletingAll = false
                    loadFiles()
                }) { Text("DELETE ALL", color = RedHalt, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingAll = false }) {
                    Text("CANCEL", color = LabelColor)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// File card
// ---------------------------------------------------------------------------

@Composable
private fun FileCard(
    info:     MissionFileInfo,
    onView:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = PanelColor),
        border   = BorderStroke(1.dp, BorderDim),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ---- Info block ----
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // File name
                Text(
                    info.file.name,
                    color      = ValueColor,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )

                // Mission overview chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (info.date.isNotEmpty())        InfoChip(info.date,        AccentCyan)
                    if (info.totalPoints.isNotEmpty()) InfoChip("${info.totalPoints} pts", AccentGreen)
                    if (info.duration.isNotEmpty())    InfoChip(info.duration,    Amber)
                    InfoChip(info.displaySize, LabelColor)
                }

                // GPS quality row
                if (info.fixedPoints.isNotEmpty() || info.avgAccuracy.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("GPS", color = LabelColor, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        if (info.fixedPoints.isNotEmpty())
                            Text("fix: ${info.fixedPoints}", color = LabelColor, fontSize = 10.sp)
                        if (info.avgAccuracy.isNotEmpty())
                            Text("avg ±${info.avgAccuracy}", color = LabelColor, fontSize = 10.sp)
                        if (info.minAccuracy.isNotEmpty())
                            Text("best ±${info.minAccuracy}", color = AccentGreen.copy(alpha = 0.7f), fontSize = 10.sp)
                        if (info.maxAccuracy.isNotEmpty())
                            Text("worst ±${info.maxAccuracy}", color = RedHalt.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // ---- Action buttons ----
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = onView,
                    modifier = Modifier.width(80.dp).height(30.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.15f),
                        contentColor   = AccentCyan
                    ),
                    border         = BorderStroke(1.dp, AccentCyan),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    elevation      = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.width(80.dp).height(30.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedHalt),
                    border   = BorderStroke(1.dp, RedHalt),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("DELETE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Text(
        text       = text,
        color      = color,
        fontSize   = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ---------------------------------------------------------------------------
// File viewer dialog — full-screen-ish, monospace, colour-coded sections
// ---------------------------------------------------------------------------

@Composable
private fun FileViewerDialog(info: MissionFileInfo, onDismiss: () -> Unit) {
    var lines     by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(info.file) {
        lines     = withContext(Dispatchers.IO) { info.file.readLines(Charsets.UTF_8) }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = PanelColor),
            shape  = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ---- Dialog header ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgColor)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            info.file.name,
                            color      = AccentCyan,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (!isLoading) {
                            Text(
                                "${lines.size} lines  •  ${info.displaySize}",
                                color    = LabelColor,
                                fontSize = 10.sp
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("✕  CLOSE", color = AccentCyan, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }

                HorizontalDivider(color = BorderDim)

                // ---- Legend row ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgColor.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendDot(LabelColor,  "Header / metadata")
                    LegendDot(AccentCyan,  "Column names")
                    LegendDot(ValueColor,  "Data rows")
                }

                HorizontalDivider(color = BorderDim)

                // ---- File content ----
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state               = listState,
                        modifier            = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(lines.size) { idx ->
                            val line = lines[idx]
                            val isColumnHeader = line.startsWith("ElapsedSec,")
                            val isData = !isColumnHeader &&
                                         line.firstOrNull()?.isDigit() == true &&
                                         line.contains(",")
                            Text(
                                text       = line,
                                color      = when {
                                    isColumnHeader -> AccentCyan
                                    isData         -> ValueColor
                                    else           -> LabelColor
                                },
                                fontSize   = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(label, color = LabelColor, fontSize = 9.sp)
    }
}
