package com.lambda.pathing.world.store

import com.lambda.pathing.core.PLANNER_THREAD_PRIORITY
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.PaletteProvider
import net.minecraft.world.chunk.PalettedContainer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TerrainStore(val directory: Path) : AutoCloseable {
	private val io = ScheduledThreadPoolExecutor(1) { task ->
		Thread(task, "NeoLambda-TerrainStore").apply {
			isDaemon = true
			priority = PLANNER_THREAD_PRIORITY
		}
	}.apply { executeExistingDelayedTasksAfterShutdownPolicy = false }
	private val tiles = object : LinkedHashMap<Long, Tile>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Tile>): Boolean {
			if (size <= CACHED_TILES) return false
			write(eldest.key, eldest.value)
			return true
		}
	}
	private var referencedAt = System.currentTimeMillis()
	private var flushScheduled = false

	fun record(sectionKey: Long, states: PalettedContainer<BlockState>, at: Long) {
		if (!io.isShutdown) io.execute { store(sectionKey, states, at) }
	}

	fun reference(at: Long) {
		io.submit {
			referencedAt = maxOf(referencedAt, at)
			flush()
		}.get()
	}

	fun sync() {
		if (!io.isShutdown) io.submit(::flush).get()
	}

	override fun close() {
		if (io.isShutdown) return
		io.execute(::flush)
		io.shutdown()
	}

	private fun store(sectionKey: Long, states: PalettedContainer<BlockState>, at: Long) {
		val key = tileKey(sectionKey)
		val tile = tiles.getOrPut(key) { read(file(directory, key)) }
		if (!tile.record(sectionKey, encode(states), at, referencedAt) || flushScheduled) return
		flushScheduled = true
		io.schedule(::flush, FLUSH_DELAY_SECONDS, TimeUnit.SECONDS)
	}

	private fun flush() {
		flushScheduled = false
		for ((key, tile) in tiles) write(key, tile)
	}

	private fun write(key: Long, tile: Tile) {
		if (!tile.dirty) return
		val path = file(directory, key)
		Files.createDirectories(directory)
		val temporary = path.resolveSibling("${path.fileName}.tmp")
		NbtIo.writeCompressed(tile.toNbt(), temporary)
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		tile.dirty = false
	}

	private class Version(val at: Long, val states: NbtCompound)

	private class Tile(val sections: Long2ObjectOpenHashMap<MutableList<Version>>) {
		var dirty = false

		fun record(sectionKey: Long, states: NbtCompound, at: Long, referencedAt: Long): Boolean {
			val versions = sections.computeIfAbsent(sectionKey) { ArrayList() }
			val latest = versions.lastOrNull()
			if (latest?.states == states) return false
			if (latest != null && latest.at > referencedAt) versions[versions.lastIndex] = Version(at, states) else versions += Version(at, states)
			dirty = true
			return true
		}

		fun toNbt(): NbtCompound {
			val list = NbtList()
			for ((key, versions) in sections) {
				list += NbtCompound().apply {
					putLong("pos", key)
					put("versions", NbtList().apply {
						for (version in versions) add(NbtCompound().apply {
							putLong("at", version.at)
							put("block_states", version.states)
						})
					})
				}
			}
			return NbtHelper.putDataVersion(NbtCompound()).apply { put("sections", list) }
		}
	}

	class StoredSection internal constructor(private val states: NbtCompound) {
		val blockNames: List<String>
			get() = states.getListOrEmpty("palette").map { (it as NbtCompound).getString("Name", "") }

		fun decode(): PalettedContainer<BlockState> = CODEC.parse(NbtOps.INSTANCE, states).getOrThrow()
	}

	companion object {
		const val TILE_CHUNK_BITS = 3
		private const val CACHED_TILES = 32
		private const val FLUSH_DELAY_SECONDS = 10L

		private val CODEC by lazy {
			PalettedContainer.createPalettedContainerCodec(BlockState.CODEC, PaletteProvider.forBlockStates(Block.STATE_IDS), Blocks.AIR.defaultState)
		}

		fun load(directory: Path, sectionKeys: LongArray, at: Long): Map<Long, PalettedContainer<BlockState>> {
			val loaded = HashMap<Long, PalettedContainer<BlockState>>()
			for ((key, keys) in sectionKeys.groupBy(::tileKey)) {
				val tile = read(file(directory, key))
				for (sectionKey in keys) {
					val version = tile.sections.get(sectionKey)?.lastOrNull { it.at <= at } ?: continue
					loaded[sectionKey] = CODEC.parse(NbtOps.INSTANCE, version.states).getOrThrow()
				}
			}
			return loaded
		}

		fun tiles(directory: Path): List<Long> {
			if (!Files.isDirectory(directory)) return emptyList()
			return Files.list(directory).use { files ->
				files.toList().mapNotNull { TILE_FILE.matchEntire(it.fileName.toString()) }.map { match ->
					ChunkSectionPos.asLong(match.groupValues[1].toInt(), 0, match.groupValues[2].toInt())
				}
			}
		}

		fun latest(directory: Path, tileKey: Long): Map<Long, StoredSection> {
			val tile = read(file(directory, tileKey))
			val latest = HashMap<Long, StoredSection>(tile.sections.size)
			for ((sectionKey, versions) in tile.sections) versions.lastOrNull()?.let { latest[sectionKey] = StoredSection(it.states) }
			return latest
		}

		fun tileOf(sectionKey: Long): Long = tileKey(sectionKey)

		private val TILE_FILE = Regex("""t\.(-?\d+)\.(-?\d+)\.dat""")

		private fun encode(states: PalettedContainer<BlockState>): NbtCompound =
			CODEC.encodeStart(NbtOps.INSTANCE, states).getOrThrow() as NbtCompound

		private fun read(path: Path): Tile {
			val sections = Long2ObjectOpenHashMap<MutableList<Version>>()
			if (Files.exists(path)) {
				for (section in NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes()).getListOrEmpty("sections")) {
					section as NbtCompound
					sections.put(section.getLong("pos", 0L), section.getListOrEmpty("versions").mapTo(ArrayList()) { version ->
						version as NbtCompound
						Version(version.getLong("at", 0L), version.getCompoundOrEmpty("block_states"))
					})
				}
			}
			return Tile(sections)
		}

		private fun tileKey(sectionKey: Long): Long = ChunkSectionPos.asLong(
			ChunkSectionPos.unpackX(sectionKey) shr TILE_CHUNK_BITS, 0, ChunkSectionPos.unpackZ(sectionKey) shr TILE_CHUNK_BITS,
		)

		private fun file(directory: Path, tileKey: Long): Path =
			directory.resolve("t.${ChunkSectionPos.unpackX(tileKey)}.${ChunkSectionPos.unpackZ(tileKey)}.dat")

		private operator fun NbtList.plusAssign(element: NbtElement) {
			add(element)
		}
	}
}
