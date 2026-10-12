package com.lambda.pathing.world.snapshot

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos

internal class SectionStore private constructor(
	private val regions: Long2ObjectOpenHashMap<Array<ImmutableSnapshotSection?>>,
	val size: Int,
	val bouncySections: Int,
	val softSections: Int,
) {
	operator fun get(key: Long): ImmutableSnapshotSection? = regions.get(regionOf(key))?.get(slotOf(key))

	fun contains(key: Long): Boolean = get(key) != null

	fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		bouncySections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bouncy }

	fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.watery }

	fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.hot }

	fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		softSections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.soft }

	private inline fun anyWithin(
		minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,
		test: (ImmutableSnapshotSection) -> Boolean,
	): Boolean {
		for (x in (minX shr 4)..(maxX shr 4)) for (z in (minZ shr 4)..(maxZ shr 4)) for (y in (minY shr 4)..(maxY shr 4)) {
			if (get(ChunkSectionPos.asLong(x, y, z))?.let(test) == true) return true
		}
		return false
	}

	fun with(key: Long, section: ImmutableSnapshotSection): SectionStore =
		with(Long2ObjectOpenHashMap<ImmutableSnapshotSection>(1).apply { put(key, section) })

	fun with(sections: Long2ObjectMap<ImmutableSnapshotSection>): SectionStore {
		if (sections.isEmpty()) return this
		val copy = regions.clone()
		val copied = LongOpenHashSet()
		var size = size
		var bouncy = bouncySections
		var soft = softSections
		val iterator = sections.long2ObjectEntrySet().iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val regionKey = regionOf(entry.longKey)
			val region = if (copied.add(regionKey)) {
				(copy.get(regionKey)?.copyOf() ?: arrayOfNulls<ImmutableSnapshotSection>(REGION_SLOTS)).also { copy.put(regionKey, it) }
			} else copy.get(regionKey)!!
			val slot = slotOf(entry.longKey)
			val replaced = region[slot]
			region[slot] = entry.value
			if (replaced == null) size++
			bouncy += entry.value.bouncyCount() - replaced.bouncyCount()
			soft += entry.value.softCount() - replaced.softCount()
		}
		return SectionStore(copy, size, bouncy, soft)
	}

	fun without(keys: LongCollection): SectionStore {
		if (keys.isEmpty()) return this
		val copy = regions.clone()
		val copied = LongOpenHashSet()
		var size = size
		var bouncy = bouncySections
		var soft = softSections
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val key = iterator.nextLong()
			val regionKey = regionOf(key)
			val existing = copy.get(regionKey) ?: continue
			val region = if (copied.add(regionKey)) existing.copyOf().also { copy.put(regionKey, it) } else existing
			val slot = slotOf(key)
			val removed = region[slot] ?: continue
			region[slot] = null
			size--
			bouncy -= removed.bouncyCount()
			soft -= removed.softCount()
		}
		val emptied = copied.iterator()
		while (emptied.hasNext()) {
			val regionKey = emptied.nextLong()
			if (copy.get(regionKey)?.all { it == null } == true) copy.remove(regionKey)
		}
		return SectionStore(copy, size, bouncy, soft)
	}

	inline fun forEach(action: (key: Long, section: ImmutableSnapshotSection) -> Unit) {
		val iterator = regionEntries().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val region = entry.value
			for (slot in region.indices) {
				val section = region[slot] ?: continue
				action(keyOf(entry.longKey, slot), section)
			}
		}
	}

	@PublishedApi
	internal fun regionEntries() = regions.long2ObjectEntrySet()

	companion object {
		private const val X_SHIFT = 42

		private const val Z_SHIFT = 20

		private const val REGION_SLOTS = 128

		private const val REGION_MASK = ((3L shl X_SHIFT) or (3L shl Z_SHIFT) or 7L).inv()

		val EMPTY = SectionStore(Long2ObjectOpenHashMap(), 0, 0, 0)

		fun of(sections: Map<Long, ImmutableSnapshotSection>): SectionStore =
			EMPTY.with(Long2ObjectOpenHashMap<ImmutableSnapshotSection>(sections.size).also { it.putAll(sections) })

		private fun regionOf(key: Long): Long = key and REGION_MASK

		private fun slotOf(key: Long): Int = (((key ushr X_SHIFT) and 3L) or (((key ushr Z_SHIFT) and 3L) shl 2) or ((key and 7L) shl 4)).toInt()

		@PublishedApi
		internal fun keyOf(region: Long, slot: Int): Long =
			region or ((slot and 3).toLong() shl X_SHIFT) or (((slot ushr 2) and 3).toLong() shl Z_SHIFT) or (slot ushr 4).toLong()

		private fun ImmutableSnapshotSection?.bouncyCount(): Int = if (this?.bouncy == true) 1 else 0

		private fun ImmutableSnapshotSection?.softCount(): Int = if (this?.soft == true) 1 else 0
	}
}
