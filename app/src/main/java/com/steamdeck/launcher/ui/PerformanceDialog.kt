package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One core as the dialog labels it: its number and, where known, its ceiling. */
class CoreRow(val core: Int, val label: String)

/**
 * The two core masks a Steam session carries, kept separate because they are wanted at the same
 * time and suit different things: the client's menus and a game each get their own. Both apply
 * at the next session start; the game's is applied by the Proton wrapper at each launch.
 */
@Composable
fun PerformanceDialog(
    cores: List<CoreRow>,
    clientOverride: Boolean,
    clientCores: Set<Int>,
    gameCores: Set<Int>,
    tuSysmem: Boolean,
    zinkLazy: Boolean,
    noXalia: Boolean,
    onClientOverride: (Boolean) -> Unit,
    onTuSysmem: (Boolean) -> Unit,
    onZinkLazy: (Boolean) -> Unit,
    onNoXalia: (Boolean) -> Unit,
    onClientCore: (Int, Boolean) -> Unit,
    onGameCore: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Performance") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Steam client cores", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Steam pins its own interface to a subset of cores it chooses — on one device 5 of 8, leaving out the fastest — " +
                        "which suits a running game and makes the menus sluggish when the client is all there is. Turning this on " +
                        "pins the client, its UI helper and gamescope to the cores ticked below instead, re-applied every few seconds " +
                        "because the UI keeps spawning helpers that inherit Steam's choice. Every core ticked is the usual fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Override Steam's own core choice", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = clientOverride, onCheckedChange = onClientOverride)
                }
                CoreGrid(cores, clientCores, enabled = clientOverride, onToggle = onClientCore)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Text("Game cores", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The cores a game the client launches may run on, applied by exec'ing it through taskset so every thread " +
                        "inherits the mask from its first instruction. Leaving every core ticked sends nothing — that is what the " +
                        "scheduler does unaided. Untick the small cores to keep a heavy game off them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                CoreGrid(cores, gameCores, enabled = true, onToggle = onGameCore)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Text("Session fixes", style = MaterialTheme.typography.titleSmall)
                Text(
                    "For a device the runtime does not sit well on, tried one at a time. All apply at the next session start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Turnip: sysmem rendering", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Renders without the GPU's tile memory (TU_DEBUG=sysmem). Banners-Turnip's own advice for an Adreno 8xx that looks glitchy or slow, and required on a 710/720/722 (set on its own there).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = tuSysmem, onCheckedChange = onTuSysmem)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Zink: lazy descriptors", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "The Steam client's menus are drawn through Zink (GL on Vulkan). Lazy descriptor updates (ZINK_DESCRIPTORS=lazy) is the mode Zink recommends on drivers without descriptor buffers, and the first thing to try when the menus are slow while games are fast.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = zinkLazy, onCheckedChange = onZinkLazy)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip Steam's xalia helper", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "xalia is a Windows program Proton starts to give Windows programs gamepad navigation. On some devices its system calls are refused in a way it cannot cope with, and the session dies seconds after Big Picture appears — 89 refusals then a broken pipe, in the session log. Turn this on if a session will not stay up. Costs gamepad navigation in Windows programs that are not games.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = noXalia, onCheckedChange = onNoXalia)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun CoreGrid(cores: List<CoreRow>, selected: Set<Int>, enabled: Boolean, onToggle: (Int, Boolean) -> Unit) {
    for (row in cores.chunked(2)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (c in row) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(checked = c.core in selected, enabled = enabled, onCheckedChange = { onToggle(c.core, it) })
                    Text(c.label, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
