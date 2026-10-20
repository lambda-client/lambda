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
import com.lambda.Lambda.mc
import com.lambda.interaction.container.containers.external.EnderChestContainer
import com.lambda.util.FolderRegistry
import com.lambda.util.StringUtils.sanitizeForFilename
import com.lambda.util.extension.dim
import com.lambda.util.world.Dimension
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.notExists

object ContainerSerializer {
	private const val ENDER_CHEST_FILE = "ender_chest.json"

	private val index = mutableMapOf<ChunkPos, MutableMap<BlockPos, ContainerType>>()

	@Volatile
	private var indexed = false
	private var indexedDirectory: Path? = null

	fun prepareIndex() {
		synchronized(this) {
			index.clear()
			indexed = false
			indexedDirectory = null
		}
		ensureIndex()
	}

	fun reset() {
		synchronized(this) {
			index.clear()
			indexed = false
			indexedDirectory = null
		}
		clearEnderChest()
	}

	fun savedIn(chunk: ChunkPos): Map<BlockPos, ContainerType> {
		ensureIndex()
		val positions = index[chunk] ?: return emptyMap()
		return positions.toMap()
	}

	val serializedContainers =
		sequence {
			val worldDir = currentWorldDirectory() ?: return@sequence
			if (worldDir.notExists()) return@sequence

			val dimensionDirectories =
				runCatching {
					Files.newDirectoryStream(worldDir).use { stream ->
						stream.filter { Files.isDirectory(it) }
					}
				}.onFailure {
					LOG.warn("Failed to list saved container dimensions", it)
				}.getOrDefault(emptyList())

			dimensionDirectories.forEach { dir ->
				dir.toFile().walkTopDown().forEach { file ->
					if (file.isFile && file.name.endsWith(".json") && file.name != ENDER_CHEST_FILE) {
						yield(readContainer(file.toPath()) ?: return@forEach)
					}
				}
			}
		}

	@Synchronized
	fun saveContainer(container: PlacedContainer) {
		ensureIndex()
		val dir = ensureDirectory(currentContainerDirectory()) ?: return
		val file = dir.resolve(containerFileName(container.pos, container.type))
		mapper.writeValue(file, container)
		record(container.pos, container.type)
	}

	@Synchronized
	fun removeContainer(pos: BlockPos) {
		val dir = currentContainerDirectory() ?: return
		ensureIndex()
		val chunk = ChunkPos(pos.x shr 4, pos.z shr 4)
		val type = index[chunk]?.remove(pos) ?: return
		index[chunk]?.let { if (it.isEmpty()) index.remove(chunk) }
		dir.resolve(containerFileName(pos, type)).deleteIfExists()
	}

	fun saveEnderChest() {
		val dir = ensureDirectory(currentWorldDirectory()) ?: return
		val file = dir.resolve(ENDER_CHEST_FILE).toFile()
		mapper.writeValue(file, EnderChestContainer)
	}

	fun loadEnderChest() {
		clearEnderChest()
		val dir = currentWorldDirectory() ?: return
		val file = dir.resolve(ENDER_CHEST_FILE)
		if (!file.exists()) return
		runCatching {
			mapper.readValue(file.toFile(), EnderChestContainer::class.java)
		}.onFailure {
			LOG.warn("Failed to load ender chest from $file", it)
		}
	}

	private fun clearEnderChest() {
		EnderChestContainer.update(emptyList())
		EnderChestContainer.storedContainers.clear()
	}

	private fun ensureDirectory(dir: Path?): Path? {
		if (dir == null) return null
		if (dir.notExists()) dir.createDirectories()
		return dir
	}

	private fun currentWorldDirectory(): Path? {
		if (mc.world == null) return null
		val name =
			mc.server
				?.getSavePath(WorldSavePath.ROOT)
				?.normalize()
				?.fileName
				?.toString()
				?: mc.server?.saveProperties?.levelName
				?: mc.currentServerEntry?.address
				?: (mc.networkHandler?.connection?.address as? InetSocketAddress)?.hostName
				?: "unknown"
		val folder = name
			.sanitizeForFilename()
			.takeUnless {
				it.isBlank() ||
						it == "." ||
						it == ".."
			}
			?: "unknown"
		return FolderRegistry.containers.resolve(folder)
	}

	private fun currentContainerDirectory(): Path? {
		val dir = currentWorldDirectory() ?: return null
		val dimension = mc.world?.dim ?: return null
		return dir.resolve(dimension.name)
	}

	private fun ensureIndex() {
		val dir = currentContainerDirectory() ?: return
		if (indexed && indexedDirectory == dir) return
		synchronized(this) {
			if (indexed && indexedDirectory == dir) return
			index.clear()
			indexed = false
			indexedDirectory = dir

			if (dir.notExists()) {
				indexed = true
				return
			}
			val files =
				runCatching {
					Files.newDirectoryStream(dir, "*.json").use { stream -> stream.toList() }
				}.onFailure {
					LOG.warn("Failed to list saved containers", it)
				}.getOrDefault(emptyList())

			var migrated = 0
			files.forEach { path ->
				runCatching {
					val name = path.fileName.toString()
					if (name == ENDER_CHEST_FILE) return@runCatching
					val parts = name.removeSuffix(".json").split('_')
					val chunkX = parts[0].toIntOrNull() ?: return@runCatching
					val chunkZ = parts[1].toIntOrNull() ?: return@runCatching
					val pos = BlockPos(
						parts.getOrNull(2)?.toIntOrNull() ?: return@runCatching,
						parts.getOrNull(3)?.toIntOrNull() ?: return@runCatching,
						parts.getOrNull(4)?.toIntOrNull() ?: return@runCatching
					)
					if (chunkX != pos.x shr 4 || chunkZ != pos.z shr 4) return@runCatching

					val named = parts.getOrNull(5)?.let { token -> ContainerType.entries.firstOrNull { it.name == token } }
					val type = named
						?: run {
							val stored = readContainer(path)
							if (stored == null) {
								LOG.warn("Dropping unreadable container file $name")
								Files.deleteIfExists(path)
								return@runCatching
							}
							Files.move(path, dir.resolve(containerFileName(pos, stored.type)))
							migrated++
							stored.type
						}
					record(pos, type)
				}.onFailure {
					LOG.warn("Failed to index saved container $path", it)
				}
			}
			if (migrated > 0) LOG.info("Named $migrated saved container(s) with their container type")
			indexed = true
		}
	}

	private fun readContainer(file: Path) =
		runCatching {
			mapper.readValue(file.toFile(), PlacedContainer::class.java)
		}.onFailure {
			LOG.warn("Failed to read container from ${file.fileName}", it)
		}.getOrNull()

	private fun record(pos: BlockPos, type: ContainerType) {
		index.getOrPut(ChunkPos(pos.x shr 4, pos.z shr 4)) { HashMap() }[pos] = type
	}

	private fun containerFileName(pos: BlockPos, type: ContainerType): String {
		val chunkX = pos.x shr 4
		val chunkZ = pos.z shr 4
		return "${chunkX}_${chunkZ}_${pos.x}_${pos.y}_${pos.z}_${type.name}.json"
	}
}
