package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.PathingChunk
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.math.ChunkSectionPos

enum class InterestTier {
	DEMAND,
	BODY,
	CORRIDOR,
}

class InterestQueue(
	private val present: (Long) -> Boolean,
) {
	private val queues = Array(InterestTier.entries.size) { ArrayDeque<Long>() }
	private val interested = HashSet<Long>()
	private val deferred = Long2ObjectLinkedOpenHashMap<LongArrayList>()
	private var deferredKeys = 0

	val size: Int get() = queues.sumOf { it.size }

	val demandSize: Int get() = queues[InterestTier.DEMAND.ordinal].size

	val deferredSize: Int get() = deferredKeys

	fun add(sectionKeys: Iterable<Long>, tier: InterestTier) {
		val queue = queues[tier.ordinal]
		for (key in sectionKeys) {
			if (present(key)) continue
			if (!interested.add(key)) continue
			queue.addLast(key)
		}
	}

	fun promoteDeferred(trusted: (Long) -> Boolean) {
		if (deferred.isEmpty()) return
		val chunks = deferred.long2ObjectEntrySet().fastIterator()
		while (chunks.hasNext()) {
			val keys = chunks.next().value
			if (!trusted(keys.getLong(0))) continue
			chunks.remove()
			deferredKeys -= keys.size
			for (index in 0 until keys.size) {
				val key = keys.getLong(index)
				if (interested.add(key)) queues[InterestTier.DEMAND.ordinal].addLast(key)
			}
		}
	}

	private fun defer(key: Long) {
		val keys = deferred.computeIfAbsent(PathingChunk.key(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))) { LongArrayList(4) }
		if (keys.contains(key)) return
		keys.add(key)
		deferredKeys++
	}

	fun nextCapturable(trusted: (Long) -> Boolean): Long? {
		for (queue in queues) {
			while (queue.isNotEmpty()) {
				val key = queue.removeFirst()
				interested.remove(key)
				if (present(key)) continue
				if (!trusted(key)) {
					defer(key)
					continue
				}
				return key
			}
		}
		return null
	}
}
