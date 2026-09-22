package com.steamdeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.steamdeck.launcher.session.SessionService

/** One driver as the dialog shows it. [removable] is false for the runtime's own and the bundled builds. */
class DriverRow(val id: String, val name: String, val detail: String, val removable: Boolean)

/** Everything one mode's settings dialog shows; the activity owns the values. */
class ModeSettings(
    val mode: String,
    val resolutionCap: Int,
    val shapeMode: String,
    val hdr: Boolean,
    /** Why HDR cannot be offered on this display, or null when it can. */
    val hdrReason: String?,
    val linuxRows: List<DriverRow>,
    val linuxSelected: String,
    val androidRows: List<DriverRow>,
    val androidSelected: String,
    val touchMode: String,
    /** Steam only. */
    val oscMode: String?,
    val directAudio: Boolean?,
    val mic: Boolean?,
    /** Desktop only. */
    val renderer: String?,
)

class ModeSettingsActions(
    val onResolution: (Int) -> Unit,
    val onShape: (String) -> Unit,
    val onHdr: (Boolean) -> Unit,
    val onSelectLinux: (String) -> Unit,
    val onImportLinux: () -> Unit,
    val onRemoveLinux: (String) -> Unit,
    val onSelectAndroid: (String) -> Unit,
    val onImportAndroid: () -> Unit,
    val onRemoveAndroid: (String) -> Unit,
    val onTouch: (String) -> Unit,
    val onOsc: (String) -> Unit,
    val onDirectAudio: (Boolean) -> Unit,
    val onMic: (Boolean) -> Unit,
    val onRenderer: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/** One entry of the settings window's rail. */
private class SettingsSection(val key: String, val label: String)

/**
 * The cog beside Play / Desktop: a settings window with a rail of sections down the left and the
 * chosen section on the right, so each setting is found by name rather than by scrolling past
 * every other one. What only matters for that one mode lives here - the display the session is
 * sized to, HDR, the driver inside the runtime, the display driver, and the input and audio
 * choices - so the main screen is left with what applies to both.
 */
@Composable
fun ModeSettingsDialog(s: ModeSettings, a: ModeSettingsActions) {
    val steam = s.mode == SessionService.MODE_STEAM
    val sections = remember(steam) {
        buildList {
            add(SettingsSection("display", "Display"))
            add(SettingsSection("hdr", "HDR"))
            add(SettingsSection("runtime", "Runtime driver"))
            add(SettingsSection("panel", "Display driver"))
            add(SettingsSection("touch", if (steam) "Touch & controls" else "Touch"))
            if (steam) add(SettingsSection("audio", "Audio"))
            if (!steam) add(SettingsSection("renderer", "Renderer"))
        }
    }
    var selected by rememberSaveable(s.mode) { mutableStateOf("display") }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = a.onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                ) {
                    Text(if (steam) "Steam session" else "Desktop session", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = a.onDismiss) { Text("Done") }
                }
                HorizontalDivider()
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // The rail: one entry per section, the chosen one filled.
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .fillMaxHeight()
                            .background(colors.surfaceVariant.copy(alpha = 0.35f))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        sections.forEach { section ->
                            val on = section.key == selected
                            Text(
                                section.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (on) colors.onPrimary else colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) colors.primary else Color.Transparent)
                                    .clickable { selected = section.key }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                            )
                        }
                    }
                    VerticalDivider()
                    // The chosen section alone, scrolling only if it must.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        when (selected) {
                            "display" -> {
                                Section("Display", "Takes effect at the next session: gamescope sizes its display once, when it starts.")
                                Text("Resolution", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                listOf(
                                    1080 to "Up to 1080p — the default",
                                    900 to "Up to 900p",
                                    720 to "Up to 720p — lighter on the GPU",
                                    0 to "The panel's own — above 1080p costs frames for nothing a handheld can show",
                                ).forEach { (cap, label) ->
                                    Choice(label, s.resolutionCap == cap) { a.onResolution(cap) }
                                }
                                Text("Shape", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                                Choice("The panel's shape (never narrower than 16:9)", s.shapeMode == "auto") { a.onShape("auto") }
                                Choice("16:9 — for a foldable, with bars on either panel", s.shapeMode == "16:9") { a.onShape("16:9") }
                            }
                            "hdr" -> {
                                Section("HDR10 output", "Decided once, when the compositor starts: a change applies after the app is fully closed and opened again.")
                                Spacer(Modifier.height(6.dp))
                                SwitchRow(
                                    "HDR10 output",
                                    s.hdrReason?.let { "Not available: $it." }
                                        ?: (if (steam) "The compositor offers games HDR10 and gamescope passes it on; a game that renders HDR shows as HDR on the panel."
                                            else "Offered to the desktop's programs; labwc itself composites in SDR, so only a program that presents HDR directly shows it."),
                                    checked = s.hdr && s.hdrReason == null, enabled = s.hdrReason == null, onChange = a.onHdr,
                                )
                            }
                            "runtime" -> {
                                Section(
                                    "Linux runtime driver",
                                    "What " + (if (steam) "the Steam client and its games" else "the desktop's programs") +
                                        " render on, inside the runtime. \"-Linux\" Turnip zips only. Applies at the next session start.",
                                )
                                Spacer(Modifier.height(6.dp))
                                for (row in s.linuxRows) DriverChoice(row, row.id == s.linuxSelected, { a.onSelectLinux(row.id) }, { a.onRemoveLinux(row.id) })
                                OutlinedButton(onClick = a.onImportLinux, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text("Import \"-Linux\" Turnip zip…")
                                }
                            }
                            "panel" -> {
                                Section(
                                    "Display driver (Android)",
                                    "What the app's compositor puts frames on the screen with, the last step of every session; shared by both modes. " +
                                        "AdrenoTools zips only. Applies after the app is fully closed and opened again.",
                                )
                                Spacer(Modifier.height(6.dp))
                                for (row in s.androidRows) DriverChoice(row, row.id == s.androidSelected, { a.onSelectAndroid(row.id) }, { a.onRemoveAndroid(row.id) })
                                OutlinedButton(onClick = a.onImportAndroid, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text("Import AdrenoTools zip…")
                                }
                            }
                            "touch" -> {
                                Section("Touch", "How a finger drives the pointer. Also in the session's drawer.")
                                Spacer(Modifier.height(4.dp))
                                Choice("Auto — touchpad on the desktop, direct in Steam", s.touchMode == "auto") { a.onTouch("auto") }
                                Choice("Touchpad — drag moves the pointer, tap clicks", s.touchMode == "touchpad") { a.onTouch("touchpad") }
                                Choice("Direct — the pointer jumps under the finger", s.touchMode == "direct") { a.onTouch("direct") }
                                if (steam && s.oscMode != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Section("On-screen controls", "The virtual pad drawn over a game.")
                                    Spacer(Modifier.height(4.dp))
                                    Choice("Auto — shown when no controller is attached", s.oscMode == "auto") { a.onOsc("auto") }
                                    Choice("Always", s.oscMode == "always") { a.onOsc("always") }
                                    Choice("Never", s.oscMode == "never") { a.onOsc("never") }
                                }
                            }
                            "audio" -> if (s.directAudio != null && s.mic != null) {
                                Section("Audio", "Both apply at the next session start.")
                                Spacer(Modifier.height(4.dp))
                                SwitchRow(
                                    "DirectAudio for games",
                                    "Games play straight to the device, bypassing PulseAudio: lower latency. Off = PulseAudio for everything.",
                                    s.directAudio, true, a.onDirectAudio,
                                )
                                SwitchRow(
                                    "Microphone",
                                    "The device's microphone for voice chat, as the client's input device. Asks for the permission once.",
                                    s.mic, true, a.onMic,
                                )
                            }
                            "renderer" -> if (s.renderer != null) {
                                Section(
                                    "Desktop renderer",
                                    "How labwc composites the desktop. pixman is software and works everywhere; gles2 / vulkan need a real DRM render node, which the Adreno stand-in is not on most devices.",
                                )
                                Spacer(Modifier.height(4.dp))
                                Choice("pixman — software, the default", s.renderer == "pixman") { a.onRenderer("pixman") }
                                Choice("gles2", s.renderer == "gles2") { a.onRenderer("gles2") }
                                Choice("vulkan", s.renderer == "vulkan") { a.onRenderer("vulkan") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, detail: String?) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SwitchRow(label: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun DriverChoice(row: DriverRow, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium)
            if (row.detail.isNotEmpty()) Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.removable) TextButton(onClick = onRemove, contentPadding = PaddingValues(0.dp)) {
                Text("Remove", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
