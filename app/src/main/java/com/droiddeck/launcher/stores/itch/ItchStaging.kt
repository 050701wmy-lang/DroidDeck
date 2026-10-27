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
                delete(dest)
            }
            Files.move(child.toPath(), destPath)
        }
    }

    fun delete(file: File) {
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory) file.listFiles().orEmpty().forEach { delete(it) }
        file.delete()
    }
}
