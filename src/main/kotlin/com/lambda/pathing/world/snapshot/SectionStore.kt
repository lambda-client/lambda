package com.lambda.pathing.world.snapshot

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection
import net.minecraft.util.math.ChunkSectionPos

internal class SectionStore private constructor(
	private val map: Long2ObjectOpenHashMap<ImmutableSnapshotSection>,
	val bouncySections: Int,
	val softSections: Int,
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

	fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		softSections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.soft }

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
		return SectionStore(
			copy,
			bouncySections - replaced.bouncyCount() + section.bouncyCount(),
			softSections - replaced.softCount() + section.softCount(),
		)
	}

	fun without(keys: LongCollection): SectionStore {
		if (keys.isEmpty()) return this
		val copy = Long2ObjectOpenHashMap(map)
		var bouncy = bouncySections
		var soft = softSections
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val removed = copy.remove(iterator.nextLong())
			bouncy -= removed.bouncyCount()
			soft -= removed.softCount()
		}
		return SectionStore(copy, bouncy, soft)
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
		val EMPTY = SectionStore(Long2ObjectOpenHashMap(), 0, 0)

		fun of(sections: Map<Long, ImmutableSnapshotSection>): SectionStore = SectionStore(
			Long2ObjectOpenHashMap<ImmutableSnapshotSection>(sections.size).also { it.putAll(sections) },
			sections.values.count { it.bouncy },
			sections.values.count { it.soft },
		)

		private fun ImmutableSnapshotSection?.bouncyCount(): Int = if (this?.bouncy == true) 1 else 0

		private fun ImmutableSnapshotSection?.softCount(): Int = if (this?.soft == true) 1 else 0
	}
}
