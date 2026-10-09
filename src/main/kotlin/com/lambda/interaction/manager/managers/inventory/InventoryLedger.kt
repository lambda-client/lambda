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
    data class Pending<T>(val before: T, val after: T, val recordedAt: Instant)

    enum class SingleDecision {
        Drop,
        Apply
    }

    companion object {
        const val CURSOR_SLOT_ID = -1
    }

    private val queues = mutableMapOf<Int, MutableMap<Int, ArrayDeque<Pending<T>>>>()

    @Synchronized
    fun record(syncId: Int, slotId: Int, before: T, after: T) {
        if (equal(before, after)) return
        cleanup()
        val queue = queues.getOrPut(syncId) { mutableMapOf() }.getOrPut(slotId) { ArrayDeque() }
        queue.addLast(Pending(before, after, clock()))
        while (queue.size > maxDepthPerSlot) queue.removeFirst()
    }

    @Synchronized
    fun decideSingle(syncId: Int, slotId: Int, incoming: T): SingleDecision {
        cleanup()
        val queue = queues[syncId]?.get(slotId) ?: return SingleDecision.Apply
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
    fun decideFull(syncId: Int, incoming: List<T>, client: List<T>): List<Boolean> {
        if (incoming.size != client.size) return List(incoming.size) { true }
        cleanup()
        return incoming.indices.map { index ->
            val queue = queues[syncId]?.get(index)
            if (queue.isNullOrEmpty()) {
                true
            } else if (equal(incoming[index], client[index])) {
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
                    queue.any { equal(it.before, incoming[index]) } -> false
                    else -> {
                        removeQueue(syncId, index)
                        true
                    }
                }
            }
        }
    }

    @Synchronized
    fun clear(syncId: Int) {
        queues.remove(syncId)
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
