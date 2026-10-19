package com.droiddeck.launcher.runtime

import org.junit.Assert.*
import org.junit.Test

class DesktopAppsTest {
    @Test fun removalKeepsSharedRuntimeFilesAndUserData() {
        val root = java.nio.file.Files.createTempDirectory("emulator-removal").toFile()
        try {
            val files = listOf("usr/bin/retroarch", "usr/lib/libSDL2.so", "root/ROMs/game.iso", "root/.config/retroarch/save")
                .map { java.io.File(root, it).apply { parentFile.mkdirs(); writeText("data") } }
            val entries = files.filter { DesktopApps.owned(it.relativeTo(root).invariantSeparatorsPath) }.map {
                DesktopFileRemoval.Entry("F", it.length(), DesktopFileRemoval.sha(it), it.relativeTo(root).invariantSeparatorsPath)
            }
            DesktopFileRemoval.remove(root, entries, setOf("usr/lib/libSDL2.so"))
            assertFalse(files[0].exists())
            assertTrue(files.drop(1).all { it.exists() })
        } finally { root.deleteRecursively() }
    }

    @Test fun onlyEmulatorProgramFilesBelongToTheBundle() {
        for (path in listOf("usr/bin/retroarch", "usr/bin/PPSSPPSDL", "usr/share/retroarch/assets/menu.png",
            "usr/lib/libretro/snes9x_libretro.so", "usr/share/ppsspp/PPSSPP/font.pgf")) {
            assertTrue(path, DesktopApps.owned(path))
        }
        for (path in listOf("root/ROMs/game.iso", "root/.config/retroarch/retroarch.cfg", "root/.local/share/ppsspp/save",
            "usr/lib/libSDL2.so", "usr/bin/python", "usr/share/retroarch-other/file", "etc/retroarch.cfg")) {
            assertFalse(path, DesktopApps.owned(path))
        }
    }
}
