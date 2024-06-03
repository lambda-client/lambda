package com.lambda.util.collections

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

class LimitedDecayQueue<E>(
    private var sizeLimit: Int,
    private var interval: Long,
) {
    private val queue: ConcurrentLinkedQueue<Pair<E, Instant>> = ConcurrentLinkedQueue()

    val size: Int
        get() = queue.size

    @Synchronized
    fun add(element: E): Boolean {
        cleanUp()
        return if (queue.size < sizeLimit) {
            queue.add(element to Instant.now())
            true
        } else {
            false
        }
    }

    @Synchronized
    fun setMaxSize(newSize: Int) {
        sizeLimit = newSize
        cleanUp()
    }

    @Synchronized
    fun setInterval(newInterval: Long) {
        interval = newInterval
        cleanUp()
    }

    @Synchronized
    private fun cleanUp() {
        val now = Instant.now()
        while (queue.isNotEmpty() && now.minusMillis(interval).isAfter(queue.peek().second)) {
            queue.poll()
        }
    }
}