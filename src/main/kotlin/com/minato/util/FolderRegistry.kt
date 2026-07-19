
package com.minato.util

import com.minato.Minato.mc
import com.minato.core.Loadable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

/**
 * Object responsible for managing the directory structure of the application.
 */
object FolderRegistry : Loadable {
    val minecraft: Path = mc.runDirectory.toPath()
    val minato: Path = minecraft.resolve("minato")
    val config: Path = minato.resolve("config")
    val packetLogs: Path = minato.resolve("packet-log")
    val replay: Path = minato.resolve("replay")
    val cache: Path = minato.resolve("cache")
    val capes: Path = cache.resolve("capes")
    val structure: Path = minato.resolve("structure")
    val maps: Path = minato.resolve("maps")
    val fonts: Path = minato.resolve("fonts")
    val hudPresets: Path = minato.resolve("hud-presets")

    val File.relativeMCPath: Path get() = minecraft.relativize(toPath())

    override fun load(): String {
        val folders = listOf(minato, config, packetLogs, replay, cache, capes, structure, maps, fonts, hudPresets)
        val createdFolders = folders.mapNotNull {
            if (it.notExists()) {
                it.createDirectories()
            } else null
        }
        return if (createdFolders.isNotEmpty()) {
            "Created directories: ${createdFolders.joinToString { minecraft.parent.relativize(it).toString() }}"
        } else "Loaded ${folders.size} directories"
    }
}
