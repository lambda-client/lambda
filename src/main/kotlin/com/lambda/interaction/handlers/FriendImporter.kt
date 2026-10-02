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
import com.lambda.interaction.handlers.FriendHandler.befriend
import com.lambda.interaction.handlers.FriendHandler.isFriend
import com.lambda.network.mojang.getProfiles
import com.lambda.threading.runGameScheduled
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.CommunicationUtils.warn
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val UNRESOLVED_LIMIT = 10

class UnsupportedFriendFileException(message: String) : Exception(message)

object FriendFileParser {
    private val NAME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun parse(text: String, mapper: JsonMapper): List<String> {
        val body = text.removePrefix("\uFEFF").trim()
        val raw = if (body.startsWith("{") || body.startsWith("[")) {
            namesIn(
                runCatching { mapper.readTree(body) }.getOrNull()
                    ?: throw UnsupportedFriendFileException("The file is not valid JSON.")
            )
        } else {
            body.lines().map { it.substringBefore('#').trim() }
        }
        return raw.filter { NAME.matches(it) }
            .distinctBy { it.lowercase() }
            .ifEmpty { throw UnsupportedFriendFileException("No friends found in this file.") }
    }

    private fun namesIn(node: JsonNode): List<String> = when {
        node.isString -> listOf(node.stringValue())
        node.isArray -> node.flatMap { namesIn(it) }
        node.isObject && node.has("name") -> {
            val name = node.get("name").takeIf { it.isString }?.stringValue()
            val role = node.get("role")?.takeIf { it.isString }?.stringValue()
            if (name != null && (role == null || role.equals("friend", true))) {
                listOf(name)
            } else {
                emptyList()
            }
        }
        node.isObject -> node.properties().filter { it.value.isArray }.flatMap { namesIn(it.value) }
        else -> emptyList()
    }
}

object FriendImporter {
    fun pickFile(): Path? = MemoryStack.stackPush().use { stack ->
        TinyFileDialogs.tinyfd_openFileDialog(
            "Select a friend list file",
            Lambda.mc.runDirectory.absolutePath,
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

        val names = runCatching { FriendFileParser.parse(text, Lambda.mapper) }.getOrElse { error ->
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