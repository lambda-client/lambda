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

package com.lambda.pathing.world.snapshot

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection

/**
 * Immutable section table keyed by packed `ChunkSectionPos` longs.
 *
 * Reads never box the key. Mutation produces a new store ([with], [without]) so a
 * reader that captured a reference sees one consistent table for the whole read.
 */
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
