package com.steamdeck.launcher.frontend

import android.content.Context
import com.steamdeck.launcher.runtime.LinuxRuntime
import com.steamdeck.launcher.session.GameStorage
import com.steamdeck.launcher.session.SessionPrefs
import java.io.File

/**
 * What the front end lists: the Steam client's installed games (from its own appmanifests, both
 * libraries) and each emulator's games (files in the ROMs folder, by extension, with a system
 * folder as a hint). Read on a worker thread; nothing here is cached beyond one screen refresh.
 */
object Library {
    class SteamGame(val appId: Int, val name: String, val art: File?, val library: String)
    class Rom(val name: String, val hostPath: File, val guestPath: String, val emulatorId: String)
    class Emulator(val id: String, val name: String, val system: String, val program: String, val installed: Boolean, val games: List<Rom>)

    /** The client's own tools and runtimes live in steamapps beside the games; they are not titles. */
    private val NOT_GAMES = setOf(228980, 1493710, 3127680, 4183110, 4427310, 4185400)
    private val NAME = Regex("^\\s*\"name\"\\s*\"([^\"]*)\"", RegexOption.MULTILINE)
    private val STATE = Regex("^\\s*\"StateFlags\"\\s*\"(\\d+)\"", RegexOption.MULTILINE)

    fun steamGames(context: Context): List<SteamGame> {
        val root = File(LinuxRuntime.rootDir(context), "root/.local/share/Steam")
        val cache = File(root, "appcache/librarycache")
        val libraries = listOfNotNull(
            root to "internal",
            GameStorage.effective(context)?.let { File(it.path) to it.label },
        )
        val out = LinkedHashMap<Int, SteamGame>()
        for ((library, label) in libraries) {
            val steamapps = File(library, "steamapps")
            steamapps.listFiles { f -> f.isFile && f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }
                ?.sortedBy { it.name }?.forEach { manifest ->
                    val appId = manifest.name.removePrefix("appmanifest_").removeSuffix(".acf").toIntOrNull() ?: return@forEach
                    if (appId in NOT_GAMES || out.containsKey(appId)) return@forEach
                    val text = try { manifest.readText() } catch (e: Exception) { return@forEach }
                    val name = NAME.find(text)?.groupValues?.get(1)?.trim().orEmpty()
                    val flags = STATE.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    // StateFlags 4 = fully installed; anything else is downloading, updating or broken.
                    if (name.isEmpty() || flags and 4 == 0) return@forEach
                    val dir = File(cache, appId.toString())
                    val art = listOf("library_600x900.jpg", "logo.png", "library_header.jpg", "header.jpg")
                        .map { File(dir, it) }.firstOrNull { it.isFile }
                    out[appId] = SteamGame(appId, name, art, label)
                }
        }
        return out.values.toList()
    }

    private class Spec(val id: String, val name: String, val system: String, val program: String, val folders: List<String>, val exts: Set<String>)
    private val specs = listOf(
        Spec("rpcs3", "RPCS3", "PS3", "/opt/appimages/rpcs3.AppImage", listOf("ps3"), setOf("iso")),
        Spec("pcsx2", "PCSX2", "PS2", "/opt/appimages/pcsx2.AppImage", listOf("ps2"), setOf("iso", "chd", "cso", "gz")),
        Spec("dolphin", "Dolphin", "GameCube / Wii", "/opt/appimages/dolphin.AppImage", listOf("gc", "gamecube", "wii"), setOf("iso", "rvz", "gcz", "wbfs", "ciso")),
        Spec("duckstation", "DuckStation", "PS1", "/opt/appimages/duckstation.AppImage", listOf("ps1", "psx"), setOf("cue", "chd", "pbp", "iso", "bin", "img", "ecm", "m3u")),
        Spec("melonds", "melonDS", "DS", "/opt/appimages/melonds.AppImage", listOf("ds", "nds"), setOf("nds", "dsi")),
        Spec("cemu", "Cemu", "Wii U", "/opt/appimages/cemu.AppImage", listOf("wiiu", "wii u"), setOf("wua", "wud", "wux", "rpx")),
        Spec("ppsspp", "PPSSPP", "PSP", "/usr/bin/PPSSPPSDL", listOf("psp"), setOf("iso", "cso", "pbp", "chd")),
        Spec("retroarch", "RetroArch", "many systems", "/usr/bin/retroarch", emptyList(), emptySet()),
    )
    private val installedIds = mapOf(
        "rpcs3" to "rpcs3", "pcsx2" to "pcsx2", "dolphin" to "dolphin", "duckstation" to "duckstation",
        "melonds" to "melonds", "cemu" to "cemu", "ppsspp" to "emulators", "retroarch" to "emulators",
    )

    /** Every emulator the app knows, installed or not, with the games its system folder holds. */
    fun emulators(context: Context, installedPackage: (String) -> Boolean): List<Emulator> {
        val romsRoot = SessionPrefs.romsDir(context).takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.isDirectory }
        return specs.map { spec ->
            val games = ArrayList<Rom>()
            if (romsRoot != null && spec.exts.isNotEmpty()) {
                // The system's folder(s), matched without regard to case, then the root itself for
                // a file left loose there.
                val dirs = romsRoot.listFiles { f -> f.isDirectory && f.name.lowercase() in spec.folders }.orEmpty().toList() + romsRoot
                for (dir in dirs) {
                    dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
                        val ext = f.extension.lowercase()
                        // A .bin beside a .cue is a track, not a game.
                        if (f.isFile && ext in spec.exts && !(ext == "bin" && File(dir, f.nameWithoutExtension + ".cue").isFile)) {
                            val rel = f.relativeTo(romsRoot).path
                            games.add(Rom(f.nameWithoutExtension, f, "/root/ROMs/$rel", spec.id))
                        }
                    }
                }
            }
            Emulator(spec.id, spec.name, spec.system, spec.program, installedPackage(installedIds.getValue(spec.id)), games)
        }
    }

    /** How the emulator is told which game to boot, on its command line. */
    fun launchArgs(emulatorId: String, guestPath: String): List<String> = when (emulatorId) {
        "rpcs3" -> listOf("--no-gui", guestPath)
        "dolphin" -> listOf("-e", guestPath)
        "cemu" -> listOf("-g", guestPath)
        else -> listOf(guestPath)
    }
}
