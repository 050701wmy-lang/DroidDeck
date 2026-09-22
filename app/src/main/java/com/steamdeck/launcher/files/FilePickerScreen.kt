package com.steamdeck.launcher.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steamdeck.launcher.core.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The file picker, as Bannerlator's File Manager behaves in pick mode: a drive menu with every
 * mounted volume ([StorageRoots]), the path with an up-arrow that stops at the drive's root so
 * nobody is trapped in a subfolder, folders first, and either a tap on a matching file or "Use
 * this folder". Nothing here edits, runs or copies anything; that is a file manager's job and
 * this app does not have one.
 */
@Composable
fun FilePickerScreen(
    pickDirMode: Boolean,
    pickExtensions: List<String>,
    initialDir: File?,
    title: String,
    onPick: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val lowerExts = remember(pickExtensions) { pickExtensions.map { it.lowercase() } }

    val start = remember { initialDir?.takeIf { it.isDirectory } ?: File(INTERNAL) }
    var currentDir by remember { mutableStateOf(start) }
    // The floor for up/back: the volume the folder is on, never the folder itself.
    var currentRoot by remember { mutableStateOf(volumeRootOf(start)) }
    var entries by remember { mutableStateOf<List<File>?>(null) }
    var storageTick by remember { mutableStateOf(0) }
    val drives = remember(storageTick) { StorageRoots.list(context) }
    var showDrives by remember { mutableStateOf(false) }

    fun matches(file: File): Boolean {
        if (file.isDirectory) return true
        if (pickDirMode) return false
        if (lowerExts.isEmpty()) return true
        val name = file.name.lowercase()
        return lowerExts.any { name.endsWith(".$it") }
    }

    fun reload() {
        entries = currentDir.listFiles()
            ?.filter { !it.name.startsWith(".") && matches(it) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun open(dir: File) {
        currentDir = dir
        reload()
    }

    fun openDrive(root: File) {
        currentRoot = root
        open(root)
    }

    fun goUp() {
        val parent = currentDir.parentFile ?: return
        if (currentDir.absolutePath == currentRoot.absolutePath) return
        open(parent)
    }

    val atRoot = currentDir.absolutePath == currentRoot.absolutePath
    LaunchedEffect(Unit) { reload() }
    BackHandler { if (atRoot) onCancel() else goUp() }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Header: back, title, drive menu, refresh.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel") }
            Text(
                title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box {
                val driveLabel = drives.firstOrNull { currentRoot.absolutePath == it.dir.absolutePath }?.label
                    ?: currentRoot.name.ifEmpty { "/" }
                OutlinedButton(onClick = { showDrives = true }) {
                    Text(driveLabel, fontSize = 12.sp, maxLines = 1)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showDrives, onDismissRequest = { showDrives = false }) {
                    drives.forEach { drive ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(drive.label + if (drive.removable) "  ·  SD" else "")
                                    Text(
                                        if (drive.readable) drive.dir.absolutePath else "mounted, not readable right now",
                                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                                    )
                                }
                            },
                            enabled = drive.readable,
                            onClick = { showDrives = false; openDrive(drive.dir) },
                        )
                    }
                }
            }
            IconButton(onClick = { storageTick++; reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        // The path, with up.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = { goUp() }, enabled = !atRoot) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up")
            }
            Text(
                elideStart(currentDir.absolutePath, 60), fontSize = 12.sp, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = colors.surfaceVariant)

        val list = entries
        when {
            list == null -> Message(
                if (currentDir.canRead()) "Nothing to list here" else "Not readable — allow storage access for SteamDeck and try again",
                Modifier.weight(1f),
            )
            list.isEmpty() -> Message(
                when {
                    pickDirMode -> "No folders here — use this one, or go up"
                    lowerExts.isNotEmpty() -> "No ${lowerExts.joinToString(" / ") { ".$it" }} files here"
                    else -> "Empty folder"
                },
                Modifier.weight(1f),
            )
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(list, key = { it.absolutePath }) { file ->
                    EntryRow(file) { if (file.isDirectory) open(file) else onPick(file) }
                }
            }
        }

        if (pickDirMode) {
            HorizontalDivider(color = colors.surfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onPick(currentDir) }, modifier = Modifier.weight(2f)) {
                    Text("Use this folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun EntryRow(file: File, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isDir = file.isDirectory
    val detail = remember(file.absolutePath) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        if (isDir) stamp else "${FileUtils.sizeToString(file.length())}  ·  $stamp"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Glyph(isDir, if (isDir) colors.primary else colors.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, fontSize = 14.sp, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
    HorizontalDivider(color = colors.surfaceVariant, modifier = Modifier.padding(start = 56.dp))
}

/** A folder or a page, drawn: the app ships no icon set beyond the few in Material's core. */
@Composable
private fun Glyph(folder: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        if (folder) {
            // Tab, then body.
            drawRoundRect(tint, Offset(0f, h * 0.18f), Size(w * 0.42f, h * 0.2f), CornerRadius(w * 0.06f))
            drawRoundRect(tint, Offset(0f, h * 0.28f), Size(w, h * 0.58f), CornerRadius(w * 0.08f))
        } else {
            val left = w * 0.15f
            val top = h * 0.06f
            drawRoundRect(
                tint, Offset(left, top), Size(w * 0.7f, h * 0.88f), CornerRadius(w * 0.06f),
                style = Stroke(width = w * 0.07f),
            )
            // Three lines of "text".
            for (i in 0..2) {
                val y = h * (0.38f + 0.17f * i)
                drawLine(tint, Offset(w * 0.3f, y), Offset(w * 0.7f, y), strokeWidth = w * 0.06f)
            }
        }
    }
}

private const val INTERNAL = "/storage/emulated/0"

/** `/storage/emulated/0/Download/x` -> `/storage/emulated/0`; `/storage/ABCD-1234/y` -> the card. */
private fun volumeRootOf(dir: File): File {
    val abs = dir.absolutePath
    return when {
        abs == INTERNAL || abs.startsWith("$INTERNAL/") -> File(INTERNAL)
        abs.startsWith("/storage/") -> {
            val name = abs.removePrefix("/storage/").substringBefore('/')
            if (name.isNotEmpty() && name != "emulated" && name != "self") File("/storage/$name") else dir
        }
        else -> dir
    }
}

private fun elideStart(path: String, max: Int): String =
    if (path.length <= max) path else "…" + path.takeLast(max - 1)
