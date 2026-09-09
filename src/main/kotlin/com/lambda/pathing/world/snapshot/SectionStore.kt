package com.lambda.pathing.world.snapshot

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection

internal class SectionStore private constructor(
	private val map: Long2ObjectOpenHashMap<ImmutableSnapshotSection>,
) {
	val size: Int get() = map.size

	operator fun get(key: Long): ImmutableSnapshotSection? = map.get(key)

	fun contains(key: Long): Boolean = map.containsKey(key)

	fun with(key: Long, section: ImmutableSnapshotSection): SectionStore =
		SectionStore(Long2ObjectOpenHashMap(map).also { it.put(key, section) })

	fun without(keys: LongCollection): SectionStore {
		if (keys.isEmpty()) return this
		val copy = Long2ObjectOpenHashMap(map)
		val iterator = keys.iterator()
		while (iterator.hasNext()) copy.remove(iterator.nextLong())
		return SectionStore(copy)
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
		val EMPTY = SectionStore(Long2ObjectOpenHashMap())

		fun of(sections: Map<Long, ImmutableSnapshotSection>): SectionStore =
			SectionStore(Long2ObjectOpenHashMap<ImmutableSnapshotSection>(sections.size).also { it.putAll(sections) })
	}
}
