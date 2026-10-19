package com.droiddeck.launcher.runtime

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

object DesktopApps {
    data class App(val id: String, val name: String, val files: List<File>)
    private val images = mapOf("rpcs3" to "RPCS3", "armsx2" to "ARMSX2", "dolphin" to "Dolphin",
        "duckstation" to "DuckStation", "melonds" to "melonDS", "cemu" to "Cemu")
    private val binaries = setOf("PPSSPPHeadless", "PPSSPPSDL", "retroarch", "retroarch-cg2glsl",
        "scummvm", "dosbox", "mednafen", "snes9x")
    private val dataDirs = setOf("retroarch", "libretro", "ppsspp", "scummvm", "dosbox", "mednafen", "snes9x")

    internal fun owned(path: String): Boolean =
        (path.startsWith("usr/bin/") && path.removePrefix("usr/bin/") in binaries) ||
            dataDirs.any { path.startsWith("usr/share/$it/") } || path.startsWith("usr/lib/libretro/") || path.startsWith("usr/lib/retroarch/") ||
            path in dataDirs.map { "usr/share/applications/$it.desktop" }

    fun installed(context: Context): List<App> {
        val root = LinuxRuntime.rootDir(context)
        val native = GZIPInputStream(context.assets.open("desktop-removal/emulators-keep.txt.gzip"))
            .bufferedReader().use { it.readLines() }.filter(::owned)
            .mapNotNull { DesktopFileRemoval.safeFile(root, it) }.filter { Files.exists(it.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) }
        return buildList {
            if (native.isNotEmpty()) add(App("emulators", "RetroArch / PPSSPP / ScummVM / DOSBox / Mednafen / Snes9x", native))
            for ((id, name) in images) {
                val dir = File(root, "opt/appimages")
                if (dir.canonicalFile != dir.absoluteFile) continue
                val image = File(dir, "$id.AppImage")
                if (Files.exists(image.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) add(App(id, name, listOf(image)))
            }
        }
    }

    fun remove(context: Context, id: String) {
        check(!com.droiddeck.launcher.session.SessionState.running)
        val root = LinuxRuntime.rootDir(context)
        val app = installed(context).firstOrNull { it.id == id } ?: return
        if (id == "emulators") {
            val entries = app.files.map { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (Files.isSymbolicLink(file.toPath())) DesktopFileRemoval.Entry("L", 0, Files.readSymbolicLink(file.toPath()).toString(), path)
                else DesktopFileRemoval.Entry("F", file.length(), DesktopFileRemoval.sha(file), path)
            }
            DesktopFileRemoval.remove(root, entries, DesktopFileRemoval.runtimeFiles(root))
            check(app.files.none { it.exists() || Files.isSymbolicLink(it.toPath()) }) { "Some emulator files are protected by the runtime" }
        } else {
            app.files.forEach { Files.delete(it.toPath()) }
            DesktopFileRemoval.safeFile(root, "usr/share/applications/droiddeck-$id.desktop")?.let { Files.deleteIfExists(it.toPath()) }
        }
        Files.deleteIfExists(File(root, ".droiddeck-pkg-$id").toPath())
    }
}
