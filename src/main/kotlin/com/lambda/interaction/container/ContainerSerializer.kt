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
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.notExists

object ContainerSerializer {
	private const val ENDER_CHEST_FILE = "ender_chest.json"

	private val index =
		mutableMapOf<Dimension, MutableMap<ChunkPos, MutableMap<ContainerType, MutableSet<BlockPos>>>>()

	val serializedContainers =
		sequence {
			val worldDir = currentWorldDirectory() ?: return@sequence
			ensureIndex()

			index.forEach { (dimension, chunks) ->
				val dimensionDir = worldDir.resolve(dimension.name)
				chunks.forEach { (_, byType) ->
					byType.forEach { (type, positions) ->
						positions.forEach { pos ->
							val file = dimensionDir.resolve(containerFileName(pos, type))
							readContainer(file)?.let { yield(it) }
						}
					}
				}
			}
		}

	@Volatile
	private var indexed = false
	private var indexedDirectory: Path? = null

	fun serializedByDimensions(vararg dimensions: Dimension) =
		sequence {
			val worldDir = currentWorldDirectory() ?: return@sequence
			ensureIndex()

			index.forEach { (dimension, chunks) ->
				if (dimension !in dimensions) return@forEach
				val dimensionDir = worldDir.resolve(dimension.name)
				chunks.forEach { (_, byType) ->
					byType.forEach { (type, positions) ->
						positions.forEach { pos ->
							val file = dimensionDir.resolve(containerFileName(pos, type))
							readContainer(file)?.let { yield(it) }
						}
					}
				}
			}
		}

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
		val dimension = mc.world?.dim ?: return emptyMap()
		return savedIn(dimension, chunk)
	}

	fun savedIn(dimension: Dimension, chunk: ChunkPos): Map<BlockPos, ContainerType> {
		ensureIndex()
		val byType = index[dimension]?.get(chunk) ?: return emptyMap()
		return buildMap {
			byType.forEach { (type, positions) ->
				positions.forEach { put(it, type) }
			}
		}
	}

	@Synchronized
	fun saveContainer(container: PlacedContainer) {
		ensureIndex()
		val worldDir = currentWorldDirectory() ?: return
		val dir = ensureDirectory(worldDir.resolve(container.dimension.name)) ?: return
		val file = dir.resolve(containerFileName(container.pos, container.type))
		mapper.writeValue(file, container)
		record(container.dimension, container.pos, container.type)
	}

	@Synchronized
	fun removeContainer(pos: BlockPos) {
		val worldDir = currentWorldDirectory() ?: return
		val dimension = mc.world?.dim ?: return
		ensureIndex()
		val type = removeRecord(dimension, pos) ?: return
		worldDir.resolve(dimension.name).resolve(containerFileName(pos, type)).deleteIfExists()
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

	private fun ensureIndex() {
		val worldDir = currentWorldDirectory() ?: return
		if (indexed && indexedDirectory == worldDir) return
		synchronized(this) {
			if (indexed && indexedDirectory == worldDir) return
			index.clear()
			indexed = false
			indexedDirectory = worldDir

			if (worldDir.notExists()) {
				indexed = true
				return
			}
			val dimensionDirectories =
				runCatching {
					Files.newDirectoryStream(worldDir).use { stream ->
						stream.filter { Files.isDirectory(it) }
					}
				}.onFailure {
					LOG.warn("Failed to list saved container dimensions", it)
				}.getOrDefault(emptyList())

			var migrated = 0
			dimensionDirectories.forEach dimensionLoop@{ dir ->
				val dimension = Dimension.entries.firstOrNull { it.name == dir.fileName.toString() }
					?: return@dimensionLoop
				val files =
					runCatching {
						Files.newDirectoryStream(dir, "*.json").use { stream -> stream.toList() }
					}.onFailure {
						LOG.warn("Failed to list saved containers in ${dir.fileName}", it)
					}.getOrDefault(emptyList())

				files.forEach fileLoop@{ path ->
					runCatching {
						val name = path.fileName.toString()
						if (name == ENDER_CHEST_FILE) return@fileLoop
						val parts = name.removeSuffix(".json").split('_')
						val chunkX = parts[0].toIntOrNull() ?: return@fileLoop
						val chunkZ = parts[1].toIntOrNull() ?: return@fileLoop
						val pos =
							BlockPos(
								parts.getOrNull(2)?.toIntOrNull() ?: return@fileLoop,
								parts.getOrNull(3)?.toIntOrNull() ?: return@fileLoop,
								parts.getOrNull(4)?.toIntOrNull() ?: return@fileLoop
							)
						if (chunkX != pos.x shr 4 || chunkZ != pos.z shr 4) return@fileLoop

						val named = parts
							.getOrNull(5)
							?.let { token ->
								ContainerType.entries.firstOrNull { it.name == token }
							}
						val type = named
							?: run {
								val stored = readContainer(path)
								if (stored == null) {
									LOG.warn("Dropping unreadable container file $name")
									Files.deleteIfExists(path)
									return@fileLoop
								}
								Files.move(path, dir.resolve(containerFileName(pos, stored.type)))
								migrated++
								stored.type
							}
						record(dimension, pos, type)
					}.onFailure {
						LOG.warn("Failed to index saved container $path", it)
					}
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

	private fun record(dimension: Dimension, pos: BlockPos, type: ContainerType) {
		index.getOrPut(dimension) { HashMap() }
			.getOrPut(ChunkPos(pos.x shr 4, pos.z shr 4)) { EnumMap(ContainerType::class.java) }
			.getOrPut(type) { HashSet() }
			.add(pos)
	}

	private fun removeRecord(dimension: Dimension, pos: BlockPos): ContainerType? {
		val chunk = ChunkPos(pos.x shr 4, pos.z shr 4)
		val chunks = index[dimension] ?: return null
		val byType = chunks[chunk] ?: return null
		val entry = byType.entries.firstOrNull { pos in it.value } ?: return null
		entry.value.remove(pos)
		if (entry.value.isEmpty()) byType.remove(entry.key)
		if (byType.isEmpty()) chunks.remove(chunk)
		if (chunks.isEmpty()) index.remove(dimension)
		return entry.key
	}

	private fun containerFileName(pos: BlockPos, type: ContainerType): String {
		val chunkX = pos.x shr 4
		val chunkZ = pos.z shr 4
		return "${chunkX}_${chunkZ}_${pos.x}_${pos.y}_${pos.z}_${type.name}.json"
	}
}
