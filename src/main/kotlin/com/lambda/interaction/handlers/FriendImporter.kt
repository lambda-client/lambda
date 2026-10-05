/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.handlers

import com.lambda.Lambda
import com.lambda.config.categories.FriendCategory
import com.lambda.config.serializers.FriendListDeserializer
import com.lambda.interaction.handlers.FriendHandler.befriend
import com.lambda.interaction.handlers.FriendHandler.isFriend
import com.lambda.network.mojang.getProfiles
import com.lambda.threading.runGameScheduled
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.FolderRegistry
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val UNRESOLVED_LIMIT = 10

object FriendImporter {
    fun pickFile(): Path? = MemoryStack.stackPush().use { stack ->
        TinyFileDialogs.tinyfd_openFileDialog(
            "Select a friend list file",
            FolderRegistry.minecraft.toString(),
            stack.pointers(
                stack.ASCII("*.json"),
                stack.ASCII("*.txt"),
                stack.ASCII("*")
            ),
            "JSON or text files",
            false
        )?.let { Paths.get(it) }
    }

    suspend fun importFile(path: Path) {
        val text = runCatching {
            require(Files.size(path) <= 1_000_000)
            Files.readString(path)
        }.getOrElse {
            logError("Could not read ${path.fileName}.")
            return
        }

        val names = runCatching { FriendListDeserializer.parse(text, Lambda.mapper) }.getOrElse { error ->
            logError(error.message ?: "Could not read this file.")
            return
        }

        val profiles = getProfiles(names)
        val found = profiles.map { it.name.lowercase() }.toSet()
        val unresolved = names.filter { it.lowercase() !in found }

        runGameScheduled {
            val fresh = profiles.filterNot { isFriend(it.id) }
            fresh.forEach { befriend(it) }
            if (fresh.isNotEmpty()) FriendCategory.trySaveToFile()

            info("Added ${fresh.size} friends, ${profiles.size - fresh.size} already in your list")
            if (unresolved.isNotEmpty()) {
                warn("${unresolved.size} unresolved (rerun to retry): ${unresolved.take(UNRESOLVED_LIMIT).joinToString()}")
            }
        }
    }
}