package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
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
    /** The account the client would sign in as offline, or null if it has never signed in. */
    val offlineAccount: String?,
    val offline: Boolean,
    /** The folder every session shows at /root/ROMs, or null if none is chosen. */
    val romsDir: String?,
    val logsEnabled: Boolean,
)

/**
 * The whole app outside a session, in one column that is sized to the window it has.
 *
 * Panels and densities vary too much for fixed dp: a 7" 1080p handheld in landscape has barely
 * 400dp of height, a foldable's inner screen has 900. So the column is designed at one size and
 * scaled by the height available — everything on it, text included, shrinks together until it
 * fits — with scrolling left as the last resort, and a width cap so the buttons never span a
 * wide panel. The system bars are kept clear of.
 *
 * Play and Desktop span the column; everything else is a tile in two columns, a title with the
 * setting's current value under it, so the menu is read at a glance and fits without scrolling
 * on a handheld.
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
    onSteamSettings: () -> Unit,
    onDesktopSettings: () -> Unit,
    onPerformance: () -> Unit,
    onOffline: () -> Unit,
    onRoms: () -> Unit,
    onFiles: () -> Unit,
    onLogs: () -> Unit,
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
            // Each launch button has its own cog: what only matters for that mode lives there.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp * k), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlay, enabled = state.ready && !state.busy,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text("Play", fontSize = 14.sp * k) }
                CogButton(k, onSteamSettings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp * k), modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k)) {
                OutlinedButton(
                    onClick = onDesktop, enabled = state.ready && !state.busy && state.desktopInstalled,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text(if (state.desktopInstalled) "Desktop" else "Desktop — install it under Desktop & apps", fontSize = 13.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                CogButton(k, onDesktopSettings)
            }
            // Offline is read while the client starts, so it is decided here rather than in the
            // session's drawer, and it needs credentials from an earlier sign-in to be possible.
            OutlinedButton(
                onClick = onOffline, enabled = state.offlineAccount != null,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight),
            ) {
                Text(
                    when {
                        state.offlineAccount == null -> "Start offline — sign in once first"
                        state.offline -> "Start offline: on · ${state.offlineAccount}"
                        else -> "Start offline: off"
                    },
                    fontSize = 13.sp * k,
                )
            }
            Spacer(Modifier.height(8.dp * k))

            val runtimeLabel = when {
                state.busy -> "Working…"
                !state.ready -> "Install Linux runtime"
                state.available != null && state.available != state.installed -> "Update Linux runtime"
                else -> "Remove Linux runtime"
            }
            val tileGap = Arrangement.spacedBy(6.dp * k)
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile(runtimeLabel, null, k, Modifier.weight(1f), enabled = !state.busy, onClick = onRuntime)
                MenuTile("Desktop & apps", null, k, Modifier.weight(1f), enabled = state.ready && !state.busy, onClick = onApps)
            }
            Spacer(Modifier.height(6.dp * k))
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile("Frame generation", state.frameGenLabel, k, Modifier.weight(1f), onClick = onFrameGen)
                MenuTile("Compatibility tools", null, k, Modifier.weight(1f), onClick = onProtons)
            }
            Spacer(Modifier.height(6.dp * k))
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile("Performance", null, k, Modifier.weight(1f), onClick = onPerformance)
                // The folder's own name is the value; the whole path is in the dialog.
                MenuTile(
                    "ROMs folder", state.romsDir?.substringAfterLast('/')?.ifEmpty { state.romsDir } ?: "not chosen",
                    k, Modifier.weight(1f), onClick = onRoms,
                )
            }
            Spacer(Modifier.height(6.dp * k))
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile("Files", null, k, Modifier.weight(1f), onClick = onFiles)
                MenuTile(
                    "Session logs", if (state.logsEnabled) "on · Download/SteamDeck" else "off",
                    k, Modifier.weight(1f), onClick = onLogs,
                )
            }
            Spacer(Modifier.height(10.dp * k))
            Text(
                "by The412Banner and maxjivi05 · credits",
                fontSize = 11.sp * k, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onCredits).padding(4.dp),
            )
        }
    }
}

/** The cog beside a launch button. */
@Composable
private fun CogButton(k: Float, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(44.dp * k).height(40.dp * k),
        contentPadding = PaddingValues(0.dp),
    ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp * k)) }
}

/** One tile of the two-column menu: what it is, and under it what it is set to. */
@Composable
private fun MenuTile(
    title: String,
    value: String?,
    k: Float,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.height(if (value != null) 52.dp * k else 44.dp * k),
        contentPadding = PaddingValues(horizontal = 8.dp * k, vertical = 4.dp * k),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (value != null) {
                Text(
                    value, fontSize = 10.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
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
