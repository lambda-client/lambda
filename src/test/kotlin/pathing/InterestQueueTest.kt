/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.world.InterestQueue
import com.lambda.pathing.world.InterestTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterestQueueTest {
    private val always: (Long) -> Boolean = { true }
    private val never: (Long) -> Boolean = { false }

    @Test
    fun `demand drains before body before corridor regardless of arrival order`() {
        val queue = InterestQueue(present = never)
        queue.add(listOf(30L, 31L), InterestTier.CORRIDOR)
        queue.add(listOf(20L), InterestTier.BODY)
        queue.add(listOf(10L), InterestTier.DEMAND)
        assertEquals(4, queue.size)
        assertEquals(1, queue.demandSize)
        assertEquals(listOf(10L, 20L, 30L, 31L), drain(queue))
    }

    @Test
    fun `a key is queued once, in the tier that saw it first`() {
        val queue = InterestQueue(present = never)
        queue.add(listOf(7L, 7L), InterestTier.CORRIDOR)
        queue.add(listOf(7L), InterestTier.DEMAND)
        assertEquals(1, queue.size)
        assertEquals(0, queue.demandSize)
        assertEquals(listOf(7L), drain(queue))
        // Once drained the key may be re-queued.
        queue.add(listOf(7L), InterestTier.DEMAND)
        assertEquals(1, queue.demandSize)
    }

    @Test
    fun `present keys are skipped at add time and again at drain time`() {
        val present = HashSet<Long>()
        val queue = InterestQueue(present = { it in present })
        present += 1L
        queue.add(listOf(1L, 2L, 3L), InterestTier.BODY)
        assertEquals(2, queue.size)
        present += 2L
        assertEquals(listOf(3L), drain(queue))
    }

    @Test
    fun `untrusted keys defer and are promoted to demand once trusted`() {
        val queue = InterestQueue(present = never)
        queue.add(listOf(5L), InterestTier.CORRIDOR)
        assertNull(queue.nextCapturable(never))
        assertEquals(0, queue.size)
        assertEquals(1, queue.deferredSize)

        queue.promoteDeferred(never)
        assertEquals(1, queue.deferredSize)

        queue.promoteDeferred(always)
        assertEquals(0, queue.deferredSize)
        assertEquals(1, queue.demandSize)
        assertEquals(5L, queue.nextCapturable(always))
    }

    @Test
    fun `promotion does not duplicate a key that was re-queued meanwhile`() {
        val queue = InterestQueue(present = never)
        queue.add(listOf(5L), InterestTier.BODY)
        assertNull(queue.nextCapturable(never))
        queue.add(listOf(5L), InterestTier.BODY)
        queue.promoteDeferred(always)
        assertEquals(1, queue.size)
        assertEquals(0, queue.demandSize)
    }

    private fun drain(queue: InterestQueue): List<Long> =
        generateSequence { queue.nextCapturable(always) }.toList()
}
