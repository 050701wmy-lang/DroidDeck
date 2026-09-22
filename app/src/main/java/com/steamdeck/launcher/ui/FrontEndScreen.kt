package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.steamdeck.launcher.R
import com.steamdeck.launcher.frontend.Library

/** Everything the front end shows; the activity owns the values and the work behind them. */
class FrontEndState(
    val installed: String?,
    val ready: Boolean,
    val available: String?,
    val busy: Boolean,
    val stage: String,
    val percent: Int,
    val desktopInstalled: Boolean,
    val offlineAccount: String?,
    val offline: Boolean,
    val frameGenLabel: String,
    val romsDir: String?,
    val logsEnabled: Boolean,
    val steamGames: List<Library.SteamGame>,
    val emulators: List<Library.Emulator>,
    /** A session alive in the background: what it is, or null. */
    val running: String?,
)

class FrontEndActions(
    val onPlay: () -> Unit,
    val onPlayDesktopUi: () -> Unit,
    val onSteamGame: (Library.SteamGame) -> Unit,
    val onDesktop: () -> Unit,
    val onEmulator: (Library.Emulator) -> Unit,
    val onRom: (Library.Rom) -> Unit,
    val onResume: () -> Unit,
    val onSteamSettings: () -> Unit,
    val onDesktopSettings: () -> Unit,
    val onApps: () -> Unit,
    val onRuntime: () -> Unit,
    val onFrameGen: () -> Unit,
    val onProtons: () -> Unit,
    val onPerformance: () -> Unit,
    val onRoms: () -> Unit,
    val onFiles: () -> Unit,
    val onLogs: () -> Unit,
    val onOffline: () -> Unit,
    val onEmulatorHelp: () -> Unit,
    val onCredits: () -> Unit,
)

/**
 * The front end: a rail of what can be launched - Steam and its games, the desktop and its
 * emulators and their games - and the chosen thing on the right with its launch button. A
 * session running in the background is the first thing on the rail, and tapping it goes back
 * to it. Landscape puts the rail beside the content; a narrow screen puts it above.
 */
@Composable
fun FrontEndScreen(s: FrontEndState, a: FrontEndActions) {
    var selected by rememberSaveable { mutableStateOf("steam") }
    var openDesktop by rememberSaveable { mutableStateOf(true) }
    var openSteam by rememberSaveable { mutableStateOf(true) }
    var openEmu by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.background).systemBarsPadding()) {
        val wide = maxWidth >= 640.dp
        val rail: @Composable () -> Unit = {
            Rail(
                s, selected, openSteam, openDesktop, openEmu,
                onSelect = { key ->
                    selected = key
                    if (key == "steam") openSteam = !openSteam || selected != "steam"
                    if (key == "desktop") openDesktop = !openDesktop || selected != "desktop"
                    if (key.startsWith("emu:")) openEmu = if (openEmu == key) "" else key
                },
                a,
                modifier = if (wide) Modifier.width(236.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(maxHeight * 0.42f),
            )
        }
        val content: @Composable (Modifier) -> Unit = { m -> Content(s, selected, a, m) }
        if (wide) Row(modifier = Modifier.fillMaxSize()) { rail(); content(Modifier.weight(1f).fillMaxHeight()) }
        else Column(modifier = Modifier.fillMaxSize()) { rail(); content(Modifier.weight(1f).fillMaxWidth()) }
    }
}

@Composable
private fun Rail(
    s: FrontEndState, selected: String, openSteam: Boolean, openDesktop: Boolean, openEmu: String,
    onSelect: (String) -> Unit, a: FrontEndActions, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.background(colors.surface).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)) {
            Image(painterResource(R.drawable.logo), null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SteamDeck", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(
                    when {
                        s.busy -> if (s.percent >= 0) "${s.stage} ${s.percent}%" else s.stage
                        !s.ready -> "runtime not installed"
                        s.available != null && s.available != s.installed -> "runtime ${s.installed} · ${s.available} available"
                        else -> "runtime ${s.installed ?: "?"}"
                    },
                    fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (s.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

        // A session in the background: the way back to it, first.
        if (s.running != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.18f))
                    .clickable(onClick = a.onResume).padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF7FD8A0)))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.running, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("running · tap to go back", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        NavItem("Steam", selected == "steam", caret = openSteam, count = s.steamGames.size) { onSelect("steam") }
        if (openSteam) Sub {
            for (g in s.steamGames) NavItem(g.name, selected == "app:${g.appId}", small = true) { onSelect("app:${g.appId}") }
            if (s.steamGames.isEmpty()) NavItem("no games installed", false, small = true, muted = true) {}
        }
        NavItem("Desktop", selected == "desktop", caret = openDesktop, count = s.emulators.count { it.installed }) { onSelect("desktop") }
        if (openDesktop) Sub {
            for (e in s.emulators.filter { it.installed }) {
                val key = "emu:${e.id}"
                NavItem(e.name, selected == key, small = true, caret = openEmu == key, count = e.games.size) { onSelect(key) }
                if (openEmu == key) Sub {
                    for ((i, g) in e.games.withIndex()) NavItem(g.name, selected == "rom:${e.id}:$i", small = true) { onSelect("rom:${e.id}:$i") }
                    if (e.games.isEmpty()) NavItem(if (s.romsDir == null) "choose a ROMs folder" else "nothing for ${e.system} in ROMs", false, small = true, muted = true) { a.onRoms() }
                }
            }
            if (s.emulators.none { it.installed }) NavItem("install emulators under Desktop & apps", false, small = true, muted = true) { a.onApps() }
        }

        Spacer(Modifier.height(10.dp))
        for ((label, act) in listOf(
            "Files" to a.onFiles, "Desktop & apps" to a.onApps, "Compatibility tools" to a.onProtons,
            "Frame generation: ${s.frameGenLabel}" to a.onFrameGen, "Performance" to a.onPerformance,
            "ROMs: ${s.romsDir?.substringAfterLast('/')?.ifEmpty { s.romsDir } ?: "choose folder"}" to a.onRoms,
            (if (s.logsEnabled) "Session logs: on" else "Session logs: off") to a.onLogs,
            (when {
                s.offlineAccount == null -> "Start offline: sign in first"
                s.offline -> "Start offline: on"
                else -> "Start offline: off"
            }) to a.onOffline,
            (when {
                s.busy -> "Working…"
                !s.ready -> "Install Linux runtime"
                s.available != null && s.available != s.installed -> "Update Linux runtime"
                else -> "Remove Linux runtime"
            }) to a.onRuntime,
        )) NavItem(label, false, small = true, muted = true) { act() }
        Text(
            "credits", fontSize = 11.sp, color = colors.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = a.onCredits).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Sub(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) { content() }
}

@Composable
private fun NavItem(
    label: String, current: Boolean, small: Boolean = false, muted: Boolean = false,
    caret: Boolean? = null, count: Int? = null, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (current) colors.primary else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = if (small) 7.dp else 9.dp),
    ) {
        if (caret != null) {
            Text(if (caret) "▾" else "▸", fontSize = 11.sp, color = if (current) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.width(14.dp))
        }
        Text(
            label, fontSize = if (small) 13.sp else 15.sp, fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold,
            color = if (current) colors.onPrimary else if (muted) colors.onSurfaceVariant else colors.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (count != null) Text(
            count.toString(), fontSize = 11.sp,
            color = if (current) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (current) Color.White.copy(alpha = 0.22f) else colors.background).padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Content(s: FrontEndState, selected: String, a: FrontEndActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
        when {
            selected == "steam" -> {
                Eyebrow("Steam")
                Title("Valve's native client, under gamescope")
                Lede("Big Picture with a controller, or the client's desktop UI. Games launch straight from the list on the left.")
                Actions {
                    Button(onClick = a.onPlay, enabled = s.ready && !s.busy) { Text("▶ Play") }
                    OutlinedButton(onClick = a.onPlayDesktopUi, enabled = s.ready && !s.busy) { Text("Desktop UI") }
                    Cog(a.onSteamSettings)
                }
                SectionTitle("Installed")
                if (s.steamGames.isEmpty()) Note("Nothing installed yet. Press Play, sign in, and install from the store; games appear here and on the left.")
                else ArtGrid(s.steamGames.map { g -> Tile(g.name, g.library, g.art, "steam:${g.appId}") { a.onSteamGame(g) } })
            }
            selected.startsWith("app:") -> {
                val g = s.steamGames.firstOrNull { "app:${it.appId}" == selected }
                if (g == null) Note("That game is no longer installed.") else {
                    Eyebrow("Steam · ${g.library}")
                    Title(g.name)
                    Lede("Starts the Steam session and launches the game straight away (steam://rungameid/${g.appId}).")
                    Actions {
                        Button(onClick = { a.onSteamGame(g) }, enabled = s.ready && !s.busy) { Text("▶ Launch") }
                        Cog(a.onSteamSettings)
                    }
                    Art(g.art, g.name, Modifier.width(240.dp))
                }
            }
            selected == "desktop" -> {
                Eyebrow("Desktop")
                Title("LXQt on labwc")
                Lede("Files, Firefox and the emulators' own windows. Emulators and their games launch from the left, under gamescope, where the GPU is.")
                Actions {
                    Button(onClick = a.onDesktop, enabled = s.ready && !s.busy && s.desktopInstalled) { Text(if (s.desktopInstalled) "▶ Desktop" else "Install the desktop first") }
                    OutlinedButton(onClick = a.onApps) { Text("Desktop & apps") }
                    Cog(a.onDesktopSettings)
                }
                SectionTitle("Emulators")
                ArtGrid(s.emulators.map { e -> Tile(e.name, if (e.installed) "${e.games.size} game${if (e.games.size == 1) "" else "s"}" else "not installed", null, "emu:${e.id}") { if (e.installed) a.onEmulator(e) else a.onApps() } })
                Spacer(Modifier.height(10.dp))
                Text("?  why emulators do not run on the desktop", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.clickable(onClick = a.onEmulatorHelp).padding(4.dp))
            }
            selected.startsWith("emu:") -> {
                val e = s.emulators.firstOrNull { "emu:${it.id}" == selected }
                if (e == null) Note("Not installed.") else {
                    Eyebrow("Desktop · ${e.system}")
                    Title(e.name)
                    Lede("Opens fullscreen under gamescope. Games are the ${e.system} files in the ROMs folder; each launches straight in.")
                    Actions {
                        Button(onClick = { a.onEmulator(e) }, enabled = s.ready && !s.busy) { Text("▶ Open ${e.name}") }
                        OutlinedButton(onClick = a.onRoms) { Text("ROMs folder") }
                    }
                    SectionTitle("Games")
                    if (e.games.isEmpty()) Note(
                        if (s.romsDir == null) "Choose a ROMs folder first (left, or the button above)."
                        else if (e.id == "retroarch") "RetroArch loads its games itself: open it and browse to root › ROMs."
                        else "Put ${e.system} games in ROMs/${e.system.substringBefore(' ')} (or the ROMs folder itself); the list rebuilds when this screen opens.",
                    )
                    else ArtGrid(e.games.map { g -> Tile(g.name, g.hostPath.extension.uppercase(), null, "rom:${g.hostPath}") { a.onRom(g) } }, wide = true)
                }
            }
            selected.startsWith("rom:") -> {
                val parts = selected.split(":")
                val e = s.emulators.firstOrNull { it.id == parts.getOrNull(1) }
                val g = e?.games?.getOrNull(parts.getOrNull(2)?.toIntOrNull() ?: -1)
                if (e == null || g == null) Note("That game is gone from the ROMs folder.") else {
                    Eyebrow("Desktop · ${e.name}")
                    Title(g.name)
                    Lede(g.guestPath)
                    Actions {
                        Button(onClick = { a.onRom(g) }, enabled = s.ready && !s.busy) { Text("▶ Launch in ${e.name}") }
                    }
                }
            }
            else -> Note("Pick something on the left.")
        }
    }
}

private class Tile(val title: String, val sub: String, val art: java.io.File?, val key: String, val onClick: () -> Unit)

@Composable private fun Eyebrow(t: String) = Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
@Composable private fun Title(t: String) = Text(t, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
@Composable private fun Lede(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
@Composable private fun SectionTitle(t: String) = Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
@Composable private fun Note(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp))
@Composable private fun Actions(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
@Composable private fun Cog(onClick: () -> Unit) = OutlinedButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Settings, "Settings", modifier = Modifier.size(18.dp)) }

@Composable
private fun Art(art: java.io.File?, label: String, modifier: Modifier, wide: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.aspectRatio(if (wide) 16f / 9f else 2f / 3f).clip(shape).background(Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface)))) {
        if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColumnScope.ArtGrid(tiles: List<Tile>, wide: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // The grid takes the rest of the column; each tile is the art with its name and one line under it.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (wide) 180.dp else 132.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        items(tiles, key = { it.key }) { t ->
            Column(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colors.surface)
                    .clickable(onClick = t.onClick),
            ) {
                Art(t.art, t.title, Modifier.fillMaxWidth(), wide)
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.sub, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
