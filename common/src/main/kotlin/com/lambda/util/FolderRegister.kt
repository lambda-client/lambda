/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister.config
import com.lambda.util.FolderRegister.lambda
import com.lambda.util.FolderRegister.minecraft
import com.lambda.util.FolderRegister.packetLogs
import com.lambda.util.FolderRegister.replay
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

/**
 * The [FolderRegister] object is responsible for managing the directory structure of the application.
 *
 * @property minecraft The root directory of the Minecraft client.
 * @property lambda The directory for the Lambda client, located within the Minecraft directory.
 * @property config The directory for storing configuration files, located within the Lambda directory.
 * @property packetLogs The directory for storing packet logs, located within the Lambda directory.
 * @property replay The directory for storing replay files, located within the Lambda directory.
 */
object FolderRegister : Loadable {
    val minecraft: Path = mc.runDirectory.toPath()
    val lambda: Path = minecraft.resolve("lambda")
    val config: Path = lambda.resolve("config")
    val packetLogs: Path = lambda.resolve("packet-log")
    val replay: Path = lambda.resolve("replay")
    val cache: Path = lambda.resolve("cache")
    val capes: Path = cache.resolve("capes")
    val structure: Path = lambda.resolve("structure")
    val maps: Path = lambda.resolve("maps")

    override fun load(): String {
        val folders = listOf(lambda, config, packetLogs, replay, cache, capes, structure, maps)
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
