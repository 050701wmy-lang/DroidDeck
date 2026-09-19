package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.steamdeck.launcher.R

/** Everything the main screen shows; the activity owns the values and the work behind them. */
class MainUiState(
    val installed: String?,
    val ready: Boolean,
    val available: String?,
    val availableSize: String?,
    val busy: Boolean,
    val stage: String,
    val percent: Int,
    val failed: Boolean,
    val frameGenLabel: String,
)

/**
 * The whole app outside a session, in one scrolling column that stays a sensible width on a wide
 * panel: is the runtime installed, is there a newer one, frame generation, and Play.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onPlay: () -> Unit,
    onRuntime: () -> Unit,
    onFrameGen: () -> Unit,
    onCredits: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth > 600.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            // The column never spans a landscape panel: the buttons stay a readable width.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(if (wide) 88.dp else 72.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("SteamDeck", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Valve's native ARM64 Steam client, under gamescope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))

                val status = when {
                    state.busy -> "Working…"
                    !state.ready -> "Linux runtime not installed"
                    else -> "Ready"
                }
                Text(status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                val detail = when {
                    state.busy -> if (state.percent >= 0) "${state.stage} ${state.percent}%" else state.stage
                    state.failed -> "Install failed — nothing was changed"
                    !state.ready -> state.available?.let { "$it · ${state.availableSize} download, one time" }
                        ?: "Could not reach the runtime catalog"
                    state.available != null && state.available != state.installed ->
                        "Runtime ${state.installed ?: "?"} installed · ${state.available} available"
                    else -> "Runtime ${state.installed ?: "?"} installed"
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.busy) {
                    Spacer(Modifier.height(8.dp))
                    if (state.percent >= 0) {
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(18.dp))

                Button(onClick = onPlay, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Play")
                }
                val runtimeLabel = when {
                    state.busy -> "Working…"
                    !state.ready -> "Install Linux runtime"
                    state.available != null && state.available != state.installed -> "Update Linux runtime"
                    else -> "Remove Linux runtime"
                }
                OutlinedButton(onClick = onRuntime, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(runtimeLabel)
                }
                OutlinedButton(onClick = onFrameGen, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Frame generation: ${state.frameGenLabel}")
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "by The412Banner and maxjivi05 · credits",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCredits).padding(6.dp),
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credits") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("The412Banner — the app, the Wayland compositor, the runtime and the Steam session.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("maxjivi05 (Max) — the gamescope runtime this is built on: the proot session, the session shim, the fake-evdev interposer and the controller work, from WinNative.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "GPL-3.0. Valve, Steam, Steam Deck and Proton are trademarks of Valve Corporation; this project is not affiliated with Valve. gamescope, Mesa, Turnip, Xwayland, PulseAudio, proot and Arch Linux ARM are their authors' own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
