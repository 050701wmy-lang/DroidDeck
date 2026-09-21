package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The two audio opt-ins. The client always has PulseAudio; these change what the games get and
 * whether anything can record. Both apply at the next session start.
 */
@Composable
fun AudioDialog(
    directAudio: Boolean,
    mic: Boolean,
    onDirectAudio: (Boolean) -> Unit,
    onMic: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio") },
        text = {
            Column {
                Text(
                    "The Steam client itself always plays through PulseAudio. These two change what happens around it, at the next session start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DirectAudio for games", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Replaces Wine's audio driver inside the games the client launches with one that talks straight to Android — lower latency, no PulseAudio in between. Wine 11 Protons only (Valve's ARM64, GE, cachyos); on anything else it stays off rather than play silence. A game's first launch after turning this on still uses Proton's own audio; the second uses this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = directAudio, onCheckedChange = onDirectAudio)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Microphone", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Gives the session Android's microphone: the Steam client shows an input device named DirectAudioMic, for voice chat. Asks for the recording permission; with it off, nothing in the session can record and Android's indicator stays off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = mic, onCheckedChange = onMic)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
