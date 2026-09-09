package com.lambda.pathing.world

class InterestQueue(

	private val present: (Long) -> Boolean,
) {
	private val queues = Array(InterestTier.entries.size) { ArrayDeque<Long>() }
	private val interested = HashSet<Long>()
	private val deferred = LinkedHashSet<Long>()

	val size: Int get() = queues.sumOf { it.size }

	val demandSize: Int get() = queues[InterestTier.DEMAND.ordinal].size

	val deferredSize: Int get() = deferred.size

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
		val promoted = deferred.filter(trusted)
		promoted.forEach { key ->
			deferred.remove(key)
			if (interested.add(key)) queues[InterestTier.DEMAND.ordinal].addLast(key)
		}
	}

	fun nextCapturable(trusted: (Long) -> Boolean): Long? {
		for (queue in queues) {
			while (queue.isNotEmpty()) {
				val key = queue.removeFirst()
				interested.remove(key)
				if (present(key)) continue
				if (!trusted(key)) {
					deferred += key
					continue
				}
				return key
			}
		}
		return null
	}
}
