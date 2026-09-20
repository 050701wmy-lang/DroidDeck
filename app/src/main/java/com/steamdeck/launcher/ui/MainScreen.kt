package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.unit.sp
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
    val desktopInstalled: Boolean,
)

/**
 * The whole app outside a session, in one column that is sized to the window it has.
 *
 * Panels and densities vary too much for fixed dp: a 7" 1080p handheld in landscape has barely
 * 400dp of height, a foldable's inner screen has 900. So the column is designed at one size and
 * scaled by the height available — everything on it, text included, shrinks together until it
 * fits — with scrolling left as the last resort, and a width cap so the buttons never span a
 * wide panel. The system bars are kept clear of.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onPlay: () -> Unit,
    onDesktop: () -> Unit,
    onApps: () -> Unit,
    onRuntime: () -> Unit,
    onFrameGen: () -> Unit,
    onProtons: () -> Unit,
    onCredits: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // The column as designed needs about this much height; below it, scale everything.
        val designHeight = 480.dp
        val k = (maxHeight / designHeight).coerceIn(0.55f, 1f)
        val colors = MaterialTheme.colorScheme

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp * k)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp * k, vertical = 12.dp * k),
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp * k),
            )
            Spacer(Modifier.height(6.dp * k))
            Text("SteamDeck", fontSize = 22.sp * k, color = colors.onBackground)
            Text(
                "Valve's native ARM64 Steam client, under gamescope",
                fontSize = 12.sp * k, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp * k))

            val status = when {
                state.busy -> "Working…"
                !state.ready -> "Linux runtime not installed"
                else -> "Ready"
            }
            Text(status, fontSize = 15.sp * k, color = colors.onBackground)
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
                detail, fontSize = 12.sp * k, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp * k),
            )
            if (state.busy) {
                Spacer(Modifier.height(6.dp * k))
                if (state.percent >= 0) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp * k))

            val buttonHeight = 40.dp * k
            Button(
                onClick = onPlay, enabled = state.ready && !state.busy,
                modifier = Modifier.fillMaxWidth().height(buttonHeight),
            ) { Text("Play", fontSize = 14.sp * k) }
            OutlinedButton(
                onClick = onDesktop, enabled = state.ready && !state.busy && state.desktopInstalled,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight + 5.dp * k),
            ) { Text(if (state.desktopInstalled) "Desktop" else "Desktop — install it under Desktop & apps", fontSize = 13.sp * k) }
            val runtimeLabel = when {
                state.busy -> "Working…"
                !state.ready -> "Install Linux runtime"
                state.available != null && state.available != state.installed -> "Update Linux runtime"
                else -> "Remove Linux runtime"
            }
            OutlinedButton(
                onClick = onRuntime, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight + 5.dp * k),
            ) { Text(runtimeLabel, fontSize = 13.sp * k) }
            OutlinedButton(
                onClick = onFrameGen,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight + 5.dp * k),
            ) { Text("Frame generation: ${state.frameGenLabel}", fontSize = 13.sp * k) }
            OutlinedButton(
                onClick = onApps, enabled = state.ready && !state.busy,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight + 5.dp * k),
            ) { Text("Desktop & apps", fontSize = 13.sp * k) }
            OutlinedButton(
                onClick = onProtons,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight + 5.dp * k),
            ) { Text("Compatibility tools", fontSize = 13.sp * k) }
            Spacer(Modifier.height(10.dp * k))
            Text(
                "by The412Banner and maxjivi05 · credits",
                fontSize = 11.sp * k, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onCredits).padding(4.dp),
            )
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
                Text("Winlator (brunodev85) — the audio stack, the gamepad model and the shape of a session's host-side components; WinNative and Bannerlator are both Winlator lineage.", style = MaterialTheme.typography.bodyMedium)
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
