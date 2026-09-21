package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steamdeck.launcher.session.SessionService

/** One driver as the dialog shows it. [removable] is false for the runtime's own and the bundled builds. */
class DriverRow(val id: String, val name: String, val detail: String, val removable: Boolean)

/**
 * The two driver choices a session has, each its own list, because a zip for one cannot serve the
 * other: the glibc Turnip the runtime draws with (chosen per mode — Steam and the desktop can
 * differ), and the bionic Turnip the app's compositor puts the frame on the panel with. Imports
 * are validated where they land, so a "-Linux" zip dropped in the Android list is refused with
 * the reason rather than becoming a black session.
 */
@Composable
fun DriverDialog(
    linuxRows: List<DriverRow>,
    linuxSteam: String,
    linuxDesktop: String,
    androidRows: List<DriverRow>,
    androidSelected: String,
    onSelectLinux: (mode: String, id: String) -> Unit,
    onImportLinux: () -> Unit,
    onRemoveLinux: (String) -> Unit,
    onSelectAndroid: (String) -> Unit,
    onImportAndroid: () -> Unit,
    onRemoveAndroid: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Graphics drivers") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Linux runtime driver", style = MaterialTheme.typography.titleSmall)
                Text(
                    "What the Steam client, its games and the desktop's programs render on, inside the runtime. " +
                        "\"-Linux\" Turnip zips only — these are Linux processes and cannot load an Android driver. " +
                        "Applies at the next session start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text("Steam", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp), fontWeight = FontWeight.Bold)
                    Text("Desktop", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold)
                }
                for (row in linuxRows) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium)
                            if (row.detail.isNotEmpty()) Text(
                                row.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (row.removable) TextButton(
                                onClick = { onRemoveLinux(row.id) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                        }
                        RadioButton(
                            selected = row.id == linuxSteam,
                            onClick = { onSelectLinux(SessionService.MODE_STEAM, row.id) },
                            modifier = Modifier.width(52.dp),
                        )
                        RadioButton(
                            selected = row.id == linuxDesktop,
                            onClick = { onSelectLinux(SessionService.MODE_DESKTOP, row.id) },
                            modifier = Modifier.width(60.dp),
                        )
                    }
                }
                OutlinedButton(onClick = onImportLinux, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Import \"-Linux\" Turnip zip…")
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Text("Display driver (Android)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "What the app's compositor puts frames on the screen with — the last step of every session. " +
                        "AdrenoTools zips only. The compositor loads its driver once per app process, so a change " +
                        "applies after the app is fully closed and opened again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                for (row in androidRows) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium)
                            if (row.detail.isNotEmpty()) Text(
                                row.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (row.removable) TextButton(
                                onClick = { onRemoveAndroid(row.id) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                        }
                        RadioButton(
                            selected = row.id == androidSelected,
                            onClick = { onSelectAndroid(row.id) },
                        )
                    }
                }
                OutlinedButton(onClick = onImportAndroid, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Import AdrenoTools zip…")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
