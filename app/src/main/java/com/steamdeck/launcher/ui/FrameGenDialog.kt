package com.steamdeck.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.steamdeck.launcher.gpu.FrameGen

/**
 * Off, or an engine at 2x/3x/4x. The LSFG rows are dimmed and unpickable until Lossless Scaling
 * is installed in the Steam client — the engine cannot run without its DLL, so offering it would
 * only produce a setting that quietly does nothing.
 */
@Composable
fun FrameGenDialog(
    engine: String,
    multiplier: Int,
    lsfgReady: Boolean,
    onPick: (engine: String, multiplier: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Row(val label: String, val engine: String, val multiplier: Int, val enabled: Boolean)
    val note = if (lsfgReady) "" else "  — install Lossless Scaling in Steam"
    val rows = listOf(Row("Off", FrameGen.ENGINE_OFF, 2, true)) +
        (2..4).map { Row("Win-FG ${it}×", FrameGen.ENGINE_WINFG, it, true) } +
        (2..4).map { Row("LSFG ${it}×$note", FrameGen.ENGINE_LSFG, it, lsfgReady) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frame generation") },
        text = {
            Column {
                Text(
                    "Extra frames are generated between the real ones on the way to the screen, so a game running at 30 fps looks like 60. Takes effect at once, mid-game included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for (row in rows) {
                    val selected = row.engine == engine && (row.engine == FrameGen.ENGINE_OFF || row.multiplier == multiplier)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (row.enabled) 1f else 0.38f)
                            .clickable(enabled = row.enabled) { onPick(row.engine, row.multiplier) }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = row.enabled)
                        Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
