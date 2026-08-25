/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.graph.Key
import com.lambda.pathing.graph.UpdatablePriorityQueue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatablePriorityQueueTest {
    private lateinit var queue: UpdatablePriorityQueue<String, Int>

    @BeforeTest
    fun setup() {
        queue = UpdatablePriorityQueue()
    }

    @Test
    fun `empty queue reports infinity top key and throws on top or pop`() {
        assertTrue(queue.isEmpty())
        assertEquals(Int.MAX_VALUE, queue.topKey(Int.MAX_VALUE))
        assertFailsWith<NoSuchElementException> { queue.top() }
        assertFailsWith<NoSuchElementException> { queue.pop() }
    }

    @Test
    fun `insert pop and remove maintain priority order`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        assertEquals("B", queue.top())
        assertEquals("B", queue.pop())
        assertFalse("B" in queue)
        assertEquals("A", queue.pop())
        assertEquals("C", queue.pop())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `update changes ordering`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        queue.update("C", 1)
        assertEquals("C", queue.top())

        queue.update("C", 20)
        assertEquals("B", queue.top())
    }

    @Test
    fun `insert existing value updates key`() {
        queue.insert("A", 10)
        queue.insert("A", 2)

        assertEquals(1, queue.size())
        assertEquals("A", queue.top())
        assertEquals(2, queue.topKey(Int.MAX_VALUE))
    }

    @Test
    fun `queue works with D Star keys`() {
        val keyQueue = UpdatablePriorityQueue<String, Key>()
        val best = Key(5.0, 1.0)

        keyQueue.insert("A", Key(10.0, 5.0))
        keyQueue.insert("B", best)
        keyQueue.insert("C", Key(5.0, 2.0))

        assertEquals(best, keyQueue.topKey(Key.INFINITY))
        assertEquals("B", keyQueue.top())
    }

    @Test
    fun `removing an interior node restores the heap in the required direction`() {
        // Exercise both replacement cases repeatedly rather than relying on
        // the root-only pop path.
        val expected = (0 until 200).associateWith { (it * 73) % 211 }.toMutableMap()
        for ((value, key) in expected) queue.insert(value.toString(), key)

        for (value in (0 until 200 step 3)) {
            assertTrue(queue.remove(value.toString()))
            expected.remove(value)
        }

        val ordered = ArrayList<Int>()
        while (!queue.isEmpty()) ordered += queue.pop().toInt()
        assertEquals(expected.entries.sortedBy { it.value }.map { it.key }, ordered)
    }
}
