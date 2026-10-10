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

import com.lambda.interaction.manager.managers.inventory.InventoryLedger.SingleDecision.Drop
import kotlin.time.Clock
import kotlin.time.Instant

class InventorySyncFilter<T>(
    private val equal: (a: T, b: T) -> Boolean,
    private val copy: (T) -> T,
    maxAgeMs: Long = 1500L,
    clock: () -> Instant = { Clock.System.now() }
) {
    private val ledger = InventoryLedger(equal, maxAgeMs, clock = clock)
    private val slotSnapshots = mutableMapOf<Int, MutableMap<Int, T>>()
    private val cursorSnapshots = mutableMapOf<Int, T>()

    var maxAgeMs: Long
        get() = ledger.maxAgeMs
        set(value) {
            ledger.maxAgeMs = value
        }

    data class FullResult(val apply: List<Boolean>, val applyCursor: Boolean)

    @Synchronized
    fun snapshot(syncId: Int, slots: Map<Int, T>, cursor: T) {
        slotSnapshots[syncId] = slots.mapValuesTo(HashMap()) { copy(it.value) }
        cursorSnapshots[syncId] = copy(cursor)
    }

    @Synchronized
    fun observeLocal(syncId: Int, slots: Map<Int, T>, cursor: T): Int {
        val snap = slotSnapshots[syncId]
        if (snap == null) {
            slotSnapshots[syncId] = slots.mapValuesTo(HashMap()) { copy(it.value) }
            cursorSnapshots[syncId] = copy(cursor)
            return 0
        }
        var recorded = 0
        slots.forEach { (slotId, live) ->
            val old = snap[slotId]
            if (old == null) snap[slotId] = copy(live)
            else if (!equal(old, live)) {
                ledger.record(syncId, slotId, old, copy(live))
                snap[slotId] = copy(live)
                recorded++
            }
        }
        snap.keys.retainAll(slots.keys)
        val oldCursor = cursorSnapshots[syncId]
        if (oldCursor == null) cursorSnapshots[syncId] = copy(cursor)
        else if (!equal(oldCursor, cursor)) {
            ledger.record(syncId, InventoryLedger.CURSOR_SLOT_ID, oldCursor, copy(cursor))
            cursorSnapshots[syncId] = copy(cursor)
            recorded++
        }
        return recorded
    }

    @Synchronized
    fun onSingle(syncId: Int, slotId: Int, incoming: T, current: T): Boolean {
        val drop = ledger.decideSingle(syncId, slotId, incoming) == Drop
        slotSnapshots[syncId]?.let { snap ->
            if (snap.containsKey(slotId)) snap[slotId] = copy(if (drop) current else incoming)
        }
        return !drop
    }

    /**
     * Decides a cursor-only server update ([SetCursorItemS2CPacket]). These bypass the full
     * packets: the server emits them from content updates on non-click triggers (pickups, tick
     * syncs), carrying cursor truth that lags our predictions by a round trip. Without this,
     * every such packet is applied blindly and resurrects whatever we just placed.
     * Same contract as [onSingle], tracked under [InventoryLedger.CURSOR_SLOT_ID].
     */
    @Synchronized
    fun onCursor(syncId: Int, incoming: T, current: T): Boolean {
        val drop = ledger.decideCursor(syncId, incoming) == Drop
        if (cursorSnapshots.containsKey(syncId)) cursorSnapshots[syncId] = copy(if (drop) current else incoming)
        return !drop
    }

    @Synchronized
    fun onFull(
        syncId: Int,
        incoming: List<T>,
        client: List<T>,
        incomingCursor: T,
        clientCursor: T,
    ): FullResult {
        val apply = ledger.decideFull(syncId, incoming, client)
        val applyCursor = ledger.decideCursor(syncId, incomingCursor) != Drop
        val merged = incoming.indices.map { copy(if (apply[it]) incoming[it] else client[it]) }
        val snap = HashMap<Int, T>(merged.size)
        merged.forEachIndexed { index, stack -> snap[index] = stack }
        slotSnapshots[syncId] = snap
        cursorSnapshots[syncId] = copy(if (applyCursor) incomingCursor else clientCursor)
        return FullResult(apply, applyCursor)
    }

    @Synchronized
    fun clear(syncId: Int) {
        ledger.clear(syncId)
    }

    @Synchronized
    fun pendingCount(syncId: Int, slotId: Int): Int = ledger.pendingCount(syncId, slotId)

    @Synchronized
    fun totalPending(): Int = ledger.totalPending()
}
