package com.lambda.pathing.world.snapshot

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection
import net.minecraft.util.math.ChunkSectionPos

internal class SectionStore private constructor(
	private val map: Long2ObjectOpenHashMap<ImmutableSnapshotSection>,
	val bouncySections: Int,
) {
	val size: Int get() = map.size

	operator fun get(key: Long): ImmutableSnapshotSection? = map.get(key)

	fun contains(key: Long): Boolean = map.containsKey(key)

	fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		bouncySections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bouncy }

	fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.watery }

	fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.hot }

	private inline fun anyWithin(
		minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,
		test: (ImmutableSnapshotSection) -> Boolean,
	): Boolean {
		for (x in (minX shr 4)..(maxX shr 4)) for (z in (minZ shr 4)..(maxZ shr 4)) for (y in (minY shr 4)..(maxY shr 4)) {
			if (map.get(ChunkSectionPos.asLong(x, y, z))?.let(test) == true) return true
		}
		return false
	}

	fun with(key: Long, section: ImmutableSnapshotSection): SectionStore {
		val copy = Long2ObjectOpenHashMap(map)
		val replaced = copy.put(key, section)
		return SectionStore(copy, bouncySections - replaced.bouncyCount() + section.bouncyCount())
	}

	fun without(keys: LongCollection): SectionStore {
		if (keys.isEmpty()) return this
		val copy = Long2ObjectOpenHashMap(map)
		var bouncy = bouncySections
		val iterator = keys.iterator()
		while (iterator.hasNext()) bouncy -= copy.remove(iterator.nextLong()).bouncyCount()
		return SectionStore(copy, bouncy)
	}

	inline fun forEach(action: (key: Long, section: ImmutableSnapshotSection) -> Unit) {
		val iterator = entries().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			action(entry.longKey, entry.value)
		}
	}

	@PublishedApi
	internal fun entries() = map.long2ObjectEntrySet()

	companion object {
		val EMPTY = SectionStore(Long2ObjectOpenHashMap(), 0)

		fun of(sections: Map<Long, ImmutableSnapshotSection>): SectionStore = SectionStore(
			Long2ObjectOpenHashMap<ImmutableSnapshotSection>(sections.size).also { it.putAll(sections) },
			sections.values.count { it.bouncy },
		)

		private fun ImmutableSnapshotSection?.bouncyCount(): Int = if (this?.bouncy == true) 1 else 0
	}
}
