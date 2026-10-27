package com.droiddeck.launcher.stores.itch

import java.io.File
import java.nio.file.Files

internal object ItchStaging {
    const val DIR = ".droiddeck-update"

    fun merge(from: File, to: File) {
        for (child in from.listFiles().orEmpty()) {
            val dest = File(to, child.name)
            val destPath = dest.toPath()
            if (child.isDirectory && !Files.isSymbolicLink(child.toPath()) && dest.isDirectory && !Files.isSymbolicLink(destPath)) {
                merge(child, dest)
                continue
            }
            if (Files.isSymbolicLink(destPath) || dest.exists()) {
                if (dest.isDirectory && !Files.isSymbolicLink(destPath)) dest.deleteRecursively() else dest.delete()
            }
            Files.move(child.toPath(), destPath)
        }
    }
}
