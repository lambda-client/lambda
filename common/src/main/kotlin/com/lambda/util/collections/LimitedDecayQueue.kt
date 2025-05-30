/*
 * Copyright 2024 Lambda
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

package com.lambda.util.collections

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A thread-safe collection that limits the number of elements it can hold and automatically removes elements
 * older than a specified time interval. The elements are stored with the timestamp of their addition to the collection.
 *
 * @param E The type of elements held in this collection.
 * @property sizeLimit The maximum number of elements the queue can hold at any given time.
 * @property maxDuration The duration after which elements are considered expired and are removed from the queue.
 * @property onDecay Lambda function that is executed on decay of element [E].
 */
class LimitedDecayQueue<E>(
    private var sizeLimit: Int,
    private var maxDuration: Duration,
    private val onDecay: (E) -> Unit = {}
) : AbstractMutableCollection<E>() {
    private val queue = ConcurrentLinkedQueue<Pair<E, TimeSource.Monotonic.ValueTimeMark>>()

    override val size: Int
        @Synchronized
        get() {
            cleanUp()
            return queue.size
        }

    @Synchronized
    override fun iterator(): MutableIterator<E> {
        cleanUp()
        return object : MutableIterator<E> {
            private val delegate = queue.iterator()

            override fun hasNext(): Boolean = delegate.hasNext()

            override fun next(): E = delegate.next().first

            override fun remove() {
                delegate.remove() // This affects the underlying queue directly
            }
        }
    }

    @Synchronized
    override fun add(element: E): Boolean {
        cleanUp()
        return if (queue.size < sizeLimit) {
            queue.add(element to TimeSource.Monotonic.markNow())
            true
        } else {
            false
        }
    }

    @Synchronized
    override fun addAll(elements: Collection<E>): Boolean {
        cleanUp()
        val spaceAvailable = sizeLimit - queue.size
        val elementsToAdd = elements.take(spaceAvailable)
        val added = elementsToAdd.map { queue.add(it to TimeSource.Monotonic.markNow()) }
        return added.any { it }
    }

    @Synchronized
    override fun remove(element: E): Boolean {
        cleanUp()
        return queue.removeIf { it.first == element }
    }

    @Synchronized
    override fun removeAll(elements: Collection<E>): Boolean {
        cleanUp()
        return queue.removeIf { it.first in elements }
    }

    @Synchronized
    override fun retainAll(elements: Collection<E>): Boolean {
        cleanUp()
        return queue.removeIf { it.first !in elements }
    }

    @Synchronized
    override fun clear() {
        queue.clear()
    }

    /**
     * Updates the maximum allowed size for the queue and triggers a cleanup operation
     * to remove elements exceeding the new size or falling outside the allowed time interval.
     *
     * Elements starting from the head will be removed.
     *
     * @param newSize The new maximum size for the queue. Must be a non-negative integer.
     */
    fun setSizeLimit(newSize: Int) {
        sizeLimit = newSize
        cleanUp()

        while (queue.size > newSize) {
            queue.poll()
        }
    }

    /**
     * Sets the decay duration for the elements in the queue. The decay determines the
     * maximum age that any element in the queue can have before being considered expired
     * and removed. Updates the internal state and triggers a cleanup of expired elements.
     *
     * @param decay The decay time [Duration].
     */
    fun setDecayTime(decay: Duration) {
        maxDuration = decay
        cleanUp()
    }

    private fun cleanUp() {
        while (queue.isNotEmpty()) {
            val (_, time) = queue.peek()

            if (time.elapsedNow() >= maxDuration) onDecay(queue.poll().first)
            else break
        }
    }
}
