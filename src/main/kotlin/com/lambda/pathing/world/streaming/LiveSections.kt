package com.lambda.pathing.world.streaming

import com.lambda.Lambda
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.TerrainJournal
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionSource
import com.lambda.pathing.world.snapshot.SectionStore
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos
import org.apache.logging.log4j.LogManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

internal class LiveSections(
	private val source: TerrainSource,
	private val table: BlockPhysicsTable,
	bounds: SimulationSnapshotBounds,
	private val onDerived: (Long) -> Unit = {},
) : SectionSource {
	private class Region {
		val slots = AtomicReferenceArray<Any?>(SectionStore.REGION_SLOTS)
		val epochs = AtomicIntegerArray(SectionStore.REGION_SLOTS)
	}

	class ChunkReload(val cells: List<VoxelPos>, val whole: Boolean)

	@Volatile
	private var regions = Long2ObjectOpenHashMap<Region>()
	private val derivedSections = AtomicInteger()
	private val deriveNanos = AtomicLong()
	private val failedDerivations = AtomicLong()
	private val flags = AtomicInteger()

	@Volatile
	private var flagsScanned = false

	private val minSectionY = bounds.minY shr 4
	private val maxSectionY = bounds.maxY shr 4

	override val journal = TerrainJournal()

	override val revision: Any = Any()

	override val size: Int get() = derivedSections.get()

	val deriveMillis: Long get() = deriveNanos.get() / 1_000_000L

	val failures: Long get() = failedDerivations.get()

	override operator fun get(key: Long): ImmutableSnapshotSection? {
		val region = regions.get(SectionStore.regionOf(key))
		if (region != null) {
			val slot = region.slots.get(SectionStore.slotOf(key))
			if (slot is ImmutableSnapshotSection) return slot
			if (slot === MISSING) return null
		}
		return derive(key)
	}

	override fun isKnown(key: Long): Boolean {
		val region = regions.get(SectionStore.regionOf(key))
		if (region != null) {
			val slot = region.slots.get(SectionStore.slotOf(key))
			if (slot is ImmutableSnapshotSection) return true
			if (slot === MISSING) return false
		}
		return source.isReadable(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))
	}

	fun isDerived(key: Long): Boolean = regions.get(SectionStore.regionOf(key))?.slots?.get(SectionStore.slotOf(key)) is ImmutableSnapshotSection

	private fun derive(key: Long): ImmutableSnapshotSection? {
		val region = region(key)
		val slot = SectionStore.slotOf(key)
		repeat(DERIVE_ATTEMPTS) {
			val epoch = region.epochs.get(slot)
			val started = System.nanoTime()
			val fresh = try {
				source.derive(key, table)
			} catch (failure: RuntimeException) {
				if (failedDerivations.incrementAndGet() <= LOGGED_FAILURES) {
					LOG.warn("Reading section ({}, {}, {}) failed; retrying", ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key), failure)
				}
				return@repeat
			}
			val installed = synchronized(region) {
				val current = region.slots.get(slot)
				when {
					current is ImmutableSnapshotSection -> return current
					region.epochs.get(slot) != epoch -> false
					fresh == null -> {
						region.slots.set(slot, MISSING)
						return null
					}
					else -> {
						region.slots.set(slot, fresh)
						true
					}
				}
			}
			if (installed) {
				derivedSections.incrementAndGet()
				deriveNanos.addAndGet(System.nanoTime() - started)
				onDerived(key)
				return fresh
			}
		}
		return null
	}

	private fun region(key: Long): Region {
		val regionKey = SectionStore.regionOf(key)
		regions.get(regionKey)?.let { return it }
		synchronized(this) {
			regions.get(regionKey)?.let { return it }
			val created = Region()
			regions = regions.clone().apply { put(regionKey, created) }
			return created
		}
	}

	fun cellChanged(x: Int, y: Int, z: Int, waterOnly: Boolean = false): Boolean {
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val region = regions.get(SectionStore.regionOf(key)) ?: return false
		val slot = SectionStore.slotOf(key)
		val section = synchronized(region) {
			region.epochs.incrementAndGet(slot)
			region.slots.get(slot) as? ImmutableSnapshotSection
		} ?: return false
		if (waterOnly && section[x, y, z].water == null) return false
		val physics = source.physicsAt(x, y, z, table) ?: return false
		if (section[x, y, z] == physics) return false
		synchronized(region) {
			region.slots.set(slot, section.with(x, y, z, physics))
			region.epochs.incrementAndGet(slot)
		}
		mark(BlockPhysicsTable.flagsOf(physics))
		journal.record(x, y, z, x, y, z)
		onDerived(key)
		return true
	}

	fun chunkLoaded(chunkX: Int, chunkZ: Int, known: Boolean): ChunkReload {
		val cells = ArrayList<VoxelPos>()
		var whole = false
		for (sectionY in minSectionY..maxSectionY) {
			val key = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ)
			val region = regions.get(SectionStore.regionOf(key)) ?: continue
			val slot = SectionStore.slotOf(key)
			val old = synchronized(region) {
				region.epochs.incrementAndGet(slot)
				val current = region.slots.get(slot)
				if (current === MISSING) region.slots.set(slot, null)
				current as? ImmutableSnapshotSection
			} ?: continue
			val fresh = source.derive(key, table) ?: continue
			val changed = IntArrayList()
			old.forEachDifference(fresh) { changed.add(it) }
			if (changed.isEmpty) continue
			synchronized(region) {
				region.slots.set(slot, fresh)
				region.epochs.incrementAndGet(slot)
			}
			journal.record(chunkX shl 4, sectionY shl 4, chunkZ shl 4, (chunkX shl 4) + 15, (sectionY shl 4) + 15, (chunkZ shl 4) + 15)
			onDerived(key)
			if (whole || changed.size > MAX_RELOAD_CELLS) {
				whole = true
				continue
			}
			for (index in 0 until changed.size) {
				val cell = changed.getInt(index)
				cells += VoxelPos((chunkX shl 4) + (cell and 15), (sectionY shl 4) + (cell ushr 8), (chunkZ shl 4) + ((cell ushr 4) and 15))
			}
		}
		if (!known) journal.record(chunkX shl 4, minSectionY shl 4, chunkZ shl 4, (chunkX shl 4) + 15, (maxSectionY shl 4) + 15, (chunkZ shl 4) + 15)
		mark(source.chunkFlags(chunkX, chunkZ, table))
		return ChunkReload(cells, whole)
	}

	fun chunkForgotten(chunkX: Int, chunkZ: Int) {
		for (sectionY in minSectionY..maxSectionY) {
			val key = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ)
			val region = regions.get(SectionStore.regionOf(key)) ?: continue
			synchronized(region) { region.epochs.incrementAndGet(SectionStore.slotOf(key)) }
		}
		journal.record(chunkX shl 4, minSectionY shl 4, chunkZ shl 4, (chunkX shl 4) + 15, (maxSectionY shl 4) + 15, (chunkZ shl 4) + 15)
	}

	private fun mark(found: Int) {
		if (found and flags.get().inv() != 0) flags.getAndUpdate { it or found }
	}

	private fun scannedFlags(): Int {
		if (!flagsScanned) synchronized(this) {
			if (!flagsScanned) {
				source.forEachReadableChunk { chunkX, chunkZ -> mark(source.chunkFlags(chunkX, chunkZ, table)) }
				forEachSection { _, section -> mark(flagsOf(section)) }
				flagsScanned = true
			}
		}
		return flags.get()
	}

	override val mayBounce: Boolean get() = scannedFlags() and BlockPhysicsTable.BOUNCY != 0

	override val maySoftLand: Boolean get() = scannedFlags() and BlockPhysicsTable.SOFT != 0

	override fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		mayBounce && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bouncy }

	override fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		scannedFlags() and BlockPhysicsTable.WATER != 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.watery }

	override fun bubblesWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		scannedFlags() and BlockPhysicsTable.BUBBLES != 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bubbly }

	override fun honeyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		scannedFlags() and BlockPhysicsTable.HONEY != 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.honeyed }

	override fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		scannedFlags() and BlockPhysicsTable.HOT != 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.hot }

	override fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		maySoftLand && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.soft }

	private inline fun anyWithin(
		minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,
		test: (ImmutableSnapshotSection) -> Boolean,
	): Boolean {
		val lowY = maxOf(minY shr 4, minSectionY)
		val highY = minOf(maxY shr 4, maxSectionY)
		for (x in (minX shr 4)..(maxX shr 4)) for (z in (minZ shr 4)..(maxZ shr 4)) for (y in lowY..highY) {
			if (get(ChunkSectionPos.asLong(x, y, z))?.let(test) == true) return true
		}
		return false
	}

	override fun forEachKnownKey(action: (Long) -> Unit) {
		val seen = LongOpenHashSet()
		forEachSection { key, _ ->
			seen.add(key)
			action(key)
		}
		source.forEachReadableChunk { chunkX, chunkZ ->
			for (sectionY in minSectionY..maxSectionY) {
				val key = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ)
				if (seen.add(key)) action(key)
			}
		}
	}

	override fun forEachSection(action: (Long, ImmutableSnapshotSection) -> Unit) {
		val entries = regions.long2ObjectEntrySet().fastIterator()
		while (entries.hasNext()) {
			val entry = entries.next()
			val slots = entry.value.slots
			for (slot in 0 until slots.length()) {
				val section = slots.get(slot) as? ImmutableSnapshotSection ?: continue
				action(SectionStore.keyOf(entry.longKey, slot), section)
			}
		}
	}

	private companion object {
		val MISSING = Any()
		const val DERIVE_ATTEMPTS = 4
		const val LOGGED_FAILURES = 8
		const val MAX_RELOAD_CELLS = 256

		fun flagsOf(section: ImmutableSnapshotSection): Int {
			var flags = 0
			if (section.bouncy) flags = flags or BlockPhysicsTable.BOUNCY
			if (section.watery) flags = flags or BlockPhysicsTable.WATER
			if (section.bubbly) flags = flags or BlockPhysicsTable.BUBBLES
			if (section.hot) flags = flags or BlockPhysicsTable.HOT
			if (section.honeyed) flags = flags or BlockPhysicsTable.HONEY
			if (section.soft) flags = flags or BlockPhysicsTable.SOFT
			return flags
		}
	}
}
