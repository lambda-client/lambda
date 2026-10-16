package com.lambda.pathing.find

import com.lambda.pathing.core.PLANNER_THREAD_PRIORITY
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.store.BaritoneCache
import com.lambda.pathing.world.store.RememberedRegion
import com.lambda.pathing.world.store.StoredTerrain
import com.lambda.pathing.world.store.TerrainStore
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.EmptyBlockView
import net.minecraft.world.chunk.PalettedContainer
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class CachedBlocks(
	private val store: Path?,
	private val baritone: BaritoneCache?,
	private val loaded: LongOpenHashSet,
	private val minY: Int,
	private val maxY: Int,
) {
	private class Area(val stored: Boolean, val key: Long, val distance: Double)

	private val storedTiles = store?.let { TerrainStore.tiles(it).toHashSet() }.orEmpty()
	private val storedKeys = HashMap<Long, LongOpenHashSet>()
	private val tiles = lru<Long, Map<Long, TerrainStore.StoredSection>>(CACHED_TILES)
	private val decoded = lru<Long, PalettedContainer<BlockState>>(CACHED_SECTIONS)
	private val regions = lru<Long, RememberedRegion?>(CACHED_REGIONS)
	private val exposed: (Int, Int, Int) -> Boolean = ::open

	fun scan(query: BlockQuery, nearest: NearestBlocks, cancelled: () -> Boolean) {
		val tileSpan = 16 shl TerrainStore.TILE_CHUNK_BITS
		val areas = ArrayList<Area>()
		for (tile in storedTiles) {
			val x = ChunkSectionPos.unpackX(tile) * tileSpan
			val z = ChunkSectionPos.unpackZ(tile) * tileSpan
			areas += Area(true, tile, nearest.boxDistance(x, minY, z, x + tileSpan - 1, maxY, z + tileSpan - 1))
		}
		for (region in baritone?.regions().orEmpty()) {
			val x = ChunkSectionPos.unpackX(region) * REGION_SPAN
			val z = ChunkSectionPos.unpackZ(region) * REGION_SPAN
			areas += Area(false, region, nearest.boxDistance(x, minY, z, x + REGION_SPAN - 1, maxY, z + REGION_SPAN - 1))
		}
		areas.sortBy { it.distance }
		val readers = Executors.newFixedThreadPool(READERS) { task ->
			Thread(task, "NeoLambda-BlockFinder").apply {
				isDaemon = true
				priority = PLANNER_THREAD_PRIORITY
			}
		}
		try {
			val reads = ArrayDeque<Pair<Area, Future<Map<Long, TerrainStore.StoredSection>>?>>()
			var next = 0
			while (true) {
				while (reads.size < PREFETCHED_AREAS && next < areas.size) {
					val area = areas[next++]
					val directory = store
					reads += area to if (area.stored && directory != null && area.key !in tiles) readers.submit<Map<Long, TerrainStore.StoredSection>> { read(directory, area.key) } else null
				}
				val (area, read) = reads.removeFirstOrNull() ?: break
				if (cancelled() || area.distance >= nearest.farthest) break
				if (area.stored) {
					read?.get()?.let { install(area.key, it) }
					scanTile(area.key, query, nearest)
				} else {
					scanRegion(area.key, query, nearest)
				}
			}
		} finally {
			readers.shutdownNow()
		}
	}

	fun section(sectionKey: Long, ignoreHazards: Boolean): ImmutableSnapshotSection? {
		if (stored(sectionKey)) {
			val states = decoded(sectionKey) ?: return null
			val terrain = StoredTerrain(Long2ObjectOpenHashMap<PalettedContainer<BlockState>>().apply { put(sectionKey, states) }, minY, maxY)
			return terrain.physics(ignoreHazards)[sectionKey]
		}
		return baritone?.section(sectionKey)
	}

	private fun scanTile(tile: Long, query: BlockQuery, nearest: NearestBlocks) {
		for ((sectionKey, section) in tile(tile)) {
			if (ChunkPos.toLong(ChunkSectionPos.unpackX(sectionKey), ChunkSectionPos.unpackZ(sectionKey)) in loaded) continue
			if (!nearest.reaches(sectionKey) || section.blockNames.none(query::mayMatchName)) continue
			nearest.scan(sectionKey, decoded(sectionKey) ?: continue, query, BlockSource.STORE, exposed)
		}
	}

	private fun scanRegion(region: Long, query: BlockQuery, nearest: NearestBlocks) {
		val remembered = region(region) ?: return
		remembered.forEachChunk { chunkX, chunkZ, chunk ->
			if (ChunkPos.toLong(chunkX, chunkZ) in loaded) return@forEachChunk
			val baseX = chunkX shl 4
			val baseZ = chunkZ shl 4
			if (nearest.boxDistance(baseX, minY, baseZ, baseX + 15, maxY, baseZ + 15) >= nearest.farthest) return@forEachChunk
			for ((name, positions) in chunk.tracked) {
				if (!query.mayMatchName(name)) continue
				for (packed in positions) {
					val y = remembered.trackedY(packed)
					if (!stored(ChunkSectionPos.asLong(chunkX, y shr 4, chunkZ))) {
						nearest.offer(baseX + RememberedRegion.trackedX(packed), y, baseZ + RememberedRegion.trackedZ(packed), BlockSource.BARITONE, exposed)
					}
				}
			}
			for (column in chunk.overview.indices) {
				if (!query.mayMatchName(chunk.overview[column])) continue
				val x = column and 15
				val z = column ushr 4
				val y = remembered.surfaceY(chunk, x, z) ?: continue
				if (!stored(ChunkSectionPos.asLong(chunkX, y shr 4, chunkZ))) nearest.offer(baseX + x, y, baseZ + z, BlockSource.BARITONE, exposed)
			}
		}
	}

	private fun open(x: Int, y: Int, z: Int): Boolean {
		if (y < minY || y > maxY) return true
		val sectionKey = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		if (stored(sectionKey)) return decoded(sectionKey)?.get(x and 15, y and 15, z and 15)?.let { !it.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) } ?: true
		val chunk = region(ChunkSectionPos.asLong(x shr 9, 0, z shr 9))?.chunk(x shr 4, z shr 4) ?: return true
		return chunk.type(x and 15, y - minY, z and 15) != RememberedRegion.SOLID
	}

	private fun region(region: Long): RememberedRegion? =
		if (baritone == null) null else regions.getOrPut(region) { baritone.region(ChunkSectionPos.unpackX(region), ChunkSectionPos.unpackZ(region)) }

	private fun install(tile: Long, sections: Map<Long, TerrainStore.StoredSection>) {
		tiles[tile] = sections
		storedKeys[tile] = LongOpenHashSet(sections.keys)
	}

	private fun tile(tile: Long): Map<Long, TerrainStore.StoredSection> =
		tiles[tile] ?: store?.let { read(it, tile) }.orEmpty().also { install(tile, it) }

	private fun read(directory: Path, tile: Long): Map<Long, TerrainStore.StoredSection> =
		runCatching { TerrainStore.latest(directory, tile) }.getOrDefault(emptyMap())

	private fun stored(sectionKey: Long): Boolean {
		val tile = TerrainStore.tileOf(sectionKey)
		if (tile !in storedTiles) return false
		val keys = storedKeys[tile] ?: tile(tile).let { storedKeys.getValue(tile) }
		return sectionKey in keys
	}

	private fun decoded(sectionKey: Long): PalettedContainer<BlockState>? {
		decoded[sectionKey]?.let { return it }
		val section = tile(TerrainStore.tileOf(sectionKey))[sectionKey] ?: return null
		return runCatching { section.decode() }.getOrNull()?.also { decoded[sectionKey] = it }
	}

	private fun NearestBlocks.reaches(sectionKey: Long): Boolean {
		val x = ChunkSectionPos.unpackX(sectionKey) shl 4
		val y = ChunkSectionPos.unpackY(sectionKey) shl 4
		val z = ChunkSectionPos.unpackZ(sectionKey) shl 4
		return boxDistance(x, y, z, x + 15, y + 15, z + 15) < farthest
	}

	private companion object {
		const val REGION_SPAN = 16 * 32

		const val CACHED_TILES = 32

		const val CACHED_SECTIONS = 1024

		const val CACHED_REGIONS = 2

		const val READERS = 4

		const val PREFETCHED_AREAS = 2 * READERS

		fun <K, V> lru(capacity: Int): LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
			override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
		}
	}
}
