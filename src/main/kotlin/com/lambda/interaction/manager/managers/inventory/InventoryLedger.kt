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

package com.lambda.interaction.manager.managers.inventory

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class InventoryLedger<T>(
    private val equal: (a: T, b: T) -> Boolean,
    var maxAgeMs: Long = 1500L,
    private val maxDepthPerSlot: Int = 64,
    private val clock: () -> Instant = { Clock.System.now() }
) {
    data class Pending<T>(
        val before: T,
        val after: T,
        val recordedAt: Instant,
        val click: Int = NO_CLICK
    )

    enum class SingleDecision {
        Drop,
        Apply
    }

    companion object {
        const val CURSOR_SLOT_ID = -1
        const val NO_CLICK = 0
    }

    private val queues = mutableMapOf<Int, MutableMap<Int, ArrayDeque<Pending<T>>>>()

    private class Clicks(var sent: Int = 0, var answered: Int = 0)

    private val clicks = mutableMapOf<Int, Clicks>()

    @Synchronized
    fun clickSent(syncId: Int): Int = ++clicks.getOrPut(syncId) { Clicks() }.sent

    private fun lastAnswered(syncId: Int) = clicks[syncId]?.answered ?: 0
    private fun Pending<T>.unanswered(syncId: Int) = click > lastAnswered(syncId)
    private fun Pending<T>.answered(syncId: Int) = click != NO_CLICK && click <= lastAnswered(syncId)

    @Synchronized
    fun record(syncId: Int, slotId: Int, before: T, after: T, click: Int = NO_CLICK) {
        if (equal(before, after)) return
        cleanup()
        val queue = queues.getOrPut(syncId) { mutableMapOf() }.getOrPut(slotId) { ArrayDeque() }
        queue.addLast(Pending(before, after, clock(), click))
        while (queue.size > maxDepthPerSlot) queue.removeFirst()
    }

    @Synchronized
    fun decideSingle(syncId: Int, slotId: Int, incoming: T): SingleDecision {
        cleanup()
        val queue = queues[syncId]?.get(slotId) ?: return SingleDecision.Apply
        if (queue.any { it.unanswered(syncId) }) return SingleDecision.Drop
        val match = queue.indexOfFirst { equal(it.after, incoming) }
        return if (match < 0) {
            removeQueue(syncId, slotId)
            SingleDecision.Apply
        } else {
            repeat(match + 1) { queue.removeFirst() }
            if (queue.isEmpty()) removeQueue(syncId, slotId)
            SingleDecision.Drop
        }
    }

    @Synchronized
    fun decideCursor(syncId: Int, incoming: T): SingleDecision {
        cleanup()
        val queue = queues[syncId]?.get(CURSOR_SLOT_ID) ?: return SingleDecision.Apply
        if (queue.any { equal(it.after, incoming) }) {
            consumeFirstAfterMatch(queue, incoming)
            if (queue.isEmpty()) removeQueue(syncId, CURSOR_SLOT_ID)
            return SingleDecision.Drop
        }
        if (queue.any { equal(it.before, incoming) }) return SingleDecision.Drop
        removeQueue(syncId, CURSOR_SLOT_ID)
        return SingleDecision.Apply
    }

    @Synchronized
    fun decideFull(syncId: Int, incoming: List<T>, client: List<T>): List<Boolean> {
        if (incoming.size != client.size) return List(incoming.size) { true }
        cleanup()
        val clicks = clicks[syncId]
        if (clicks != null && clicks.answered < clicks.sent) clicks.answered++
        return decideSlots(syncId, incoming, client).also {
            if (queues[syncId].isNullOrEmpty()) clicks?.run { answered = sent }
        }
    }

    private fun decideSlots(syncId: Int, incoming: List<T>, client: List<T>): List<Boolean> =
        incoming.indices.map { index ->
            val queue = queues[syncId]?.get(index)
	        queue.isNullOrEmpty() ||
                    if (equal(incoming[index], client[index])) {
                        consumeFirstAfterMatch(queue, incoming[index])
                        if (queue.isEmpty()) removeQueue(syncId, index)
                        true
                    } else {
                        val match = queue.indexOfFirst { equal(it.after, incoming[index]) }
                        when {
                            match >= 0 -> {
                                consumeFirstAfterMatch(queue, incoming[index])
                                if (queue.isEmpty()) removeQueue(syncId, index)
                                false
                            }
                            queue.any { equal(it.before, incoming[index]) } && !queue.all { it.answered(syncId) } -> false
                            else -> {
                                removeQueue(syncId, index)
                                true
                            }
                        }
                    }
        }

    @Synchronized
    fun clear(syncId: Int) {
        queues.remove(syncId)
        clicks.remove(syncId)
    }

    @Synchronized
    fun pendingCount(syncId: Int, slotId: Int): Int = queues[syncId]?.get(slotId)?.size ?: 0

    @Synchronized
    fun totalPending(): Int = queues.values.sumOf { perSync -> perSync.values.sumOf { it.size } }

    private fun consumeFirstAfterMatch(queue: ArrayDeque<Pending<T>>, value: T) {
        val match = queue.indexOfFirst { equal(it.after, value) }
        if (match >= 0) repeat(match + 1) { queue.removeFirst() }
    }

    private fun removeQueue(syncId: Int, slotId: Int) {
        val perSync = queues[syncId] ?: return
        perSync.remove(slotId)
        if (perSync.isEmpty()) queues.remove(syncId)
    }

    private fun cleanup() {
        if (maxAgeMs <= 0) {
            queues.clear()
            return
        }
        val cutoff = clock().minus(maxAgeMs.milliseconds)
        val emptySyncs = mutableListOf<Int>()
        queues.forEach { (syncId, perSync) ->
            val emptySlots = mutableListOf<Int>()
            perSync.forEach { (slotId, queue) ->
                while (queue.isNotEmpty() && queue.first().recordedAt < cutoff) queue.removeFirst()
                if (queue.isEmpty()) emptySlots += slotId
            }
            emptySlots.forEach { perSync.remove(it) }
            if (perSync.isEmpty()) emptySyncs += syncId
        }
        emptySyncs.forEach { queues.remove(it) }
    }
}
