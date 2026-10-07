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

package com.lambda.interaction.container

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mapper
import com.lambda.interaction.container.containers.external.EnderChestContainer
import com.lambda.util.FolderRegistry
import net.minecraft.util.math.BlockPos
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.notExists

object ContainerSerializer {
	private const val ENDER_CHEST_FILE = "ender_chest.json"

	val serializedContainers: Sequence<PlacedContainer> =
		sequence {
			val dir = FolderRegistry.containers
			if (dir.notExists()) return@sequence

			dir.toFile().walkTopDown().forEach { file ->
				if (file.isFile && file.name.endsWith(".json") && file.name != ENDER_CHEST_FILE) {
					yield(readContainer(file.toPath()) ?: return@forEach)
				}
			}
		}

	@Synchronized
	fun saveContainer(container: PlacedContainer) {
		val dir = ensureDirectory()
		val file = dir.resolve(containerFileName(container.pos)).toFile()
		mapper.writeValue(file, container)
	}

	private fun readContainer(file: Path): PlacedContainer? =
		runCatching {
			mapper.readValue(file.toFile(), PlacedContainer::class.java)
		}.onFailure {
			LOG.warn("Failed to read container from ${file.fileName}", it)
		}.getOrNull()

	@Synchronized
	fun removeContainer(pos: BlockPos) {
		val dir = FolderRegistry.containers
		dir.resolve(containerFileName(pos)).deleteIfExists()
	}

	fun saveEnderChest() {
		val dir = ensureDirectory()
		val file = dir.resolve(ENDER_CHEST_FILE).toFile()
		mapper.writeValue(file, EnderChestContainer)
	}

	fun loadEnderChest() {
		val file = FolderRegistry.containers.resolve(ENDER_CHEST_FILE)
		if (!file.exists()) return
		runCatching {
			mapper.readValue(file.toFile(), EnderChestContainer::class.java)
		}.onFailure {
			LOG.warn("Failed to load ender chest from $file", it)
		}
	}

	private fun ensureDirectory() =
		FolderRegistry.containers.also { if (it.notExists()) it.createDirectories() }

	private fun containerFileName(pos: BlockPos): String {
		val chunkX = pos.x shr 4
		val chunkZ = pos.z shr 4
		return "${chunkX}_${chunkZ}_${pos.x}_${pos.y}_${pos.z}.json"
	}
}
