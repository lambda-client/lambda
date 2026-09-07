package com.lambda.pathing.world

/**
 * Section keys waiting for capture, one FIFO per [InterestTier], drained DEMAND first.
 * Keys whose chunk is not trusted park in [deferred] until [promoteDeferred] lifts them
 * back to DEMAND. Not thread-safe: the owner serialises calls.
 * See docs/decisions/world-capture.md.
 */
class InterestQueue(
    /** True for a key that is already captured and not awaiting removal; such keys are skipped. */
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

    /** Re-queues every deferred key whose chunk [trusted] now accepts, at DEMAND tier. */
    fun promoteDeferred(trusted: (Long) -> Boolean) {
        if (deferred.isEmpty()) return
        val promoted = deferred.filter(trusted)
        promoted.forEach { key ->
            deferred.remove(key)
            if (interested.add(key)) queues[InterestTier.DEMAND.ordinal].addLast(key)
        }
    }

    /** The next key to capture, or null when nothing capturable is queued. */
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
