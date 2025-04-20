/*
 * Copyright 2025 Lambda
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

package pathing

import com.lambda.pathing.dstar.Key
import com.lambda.pathing.dstar.UpdatablePriorityQueue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/*
 * Copyright 2025 Lambda
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

internal class UpdatablePriorityQueueTest {

    private lateinit var queue: UpdatablePriorityQueue<String, Int>
    private val infinityKey = Int.MAX_VALUE // Use Int.MAX_VALUE as infinity for Int keys

    @BeforeEach
    fun setUp() {
        queue = UpdatablePriorityQueue()
    }

    @Test
    fun `queue is initially empty`() {
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
        assertEquals(infinityKey, queue.topKey(infinityKey))
        assertThrows<NoSuchElementException> { queue.top() }
        assertThrows<NoSuchElementException> { queue.pop() }
    }

    @Test
    fun `insert adds element and updates size`() {
        queue.insert("A", 10)
        assertFalse(queue.isEmpty())
        assertEquals(1, queue.size())
        assertTrue(queue.contains("A"))
    }

    @Test
    fun `insert multiple elements maintains order`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        assertEquals(3, queue.size())
        assertEquals(5, queue.topKey(infinityKey))
        assertEquals("B", queue.top())
    }

    @Test
    fun `pop removes and returns top element maintaining order`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        assertEquals("B", queue.pop())
        assertEquals(2, queue.size())
        assertEquals(10, queue.topKey(infinityKey))
        assertEquals("A", queue.top())
        assertFalse(queue.contains("B"))

        assertEquals("A", queue.pop())
        assertEquals(1, queue.size())
        assertEquals(15, queue.topKey(infinityKey))
        assertEquals("C", queue.top())
        assertFalse(queue.contains("A"))

        assertEquals("C", queue.pop())
        assertEquals(0, queue.size())
        assertTrue(queue.isEmpty())
        assertFalse(queue.contains("C"))
    }

    @Test
    fun `pop on empty queue throws exception`() {
        assertThrows<NoSuchElementException> { queue.pop() }
    }

    @Test
    fun `top on empty queue throws exception`() {
        assertThrows<NoSuchElementException> { queue.top() }
    }

    @Test
    fun `topKey on empty queue returns infinityKey`() {
        assertEquals(infinityKey, queue.topKey(infinityKey))
    }

    @Test
    fun `contains checks for element presence`() {
        queue.insert("A", 10)
        assertTrue(queue.contains("A"))
        assertFalse(queue.contains("B"))
    }

    @Test
    fun `remove existing element works`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        assertTrue(queue.remove("A"))
        assertEquals(2, queue.size())
        assertFalse(queue.contains("A"))
        assertEquals(5, queue.topKey(infinityKey)) // B should be top
        assertEquals("B", queue.top())

        assertTrue(queue.remove("C"))
        assertEquals(1, queue.size())
        assertFalse(queue.contains("C"))
        assertEquals(5, queue.topKey(infinityKey)) // B still top
        assertEquals("B", queue.top())
    }

    @Test
    fun `remove affects top element`() {
        queue.insert("A", 10)
        queue.insert("B", 5)

        assertTrue(queue.remove("B")) // Remove the top element
        assertEquals(1, queue.size())
        assertEquals(10, queue.topKey(infinityKey))
        assertEquals("A", queue.top())
    }

    @Test
    fun `remove non-existing element returns false`() {
        queue.insert("A", 10)
        assertFalse(queue.remove("B"))
        assertEquals(1, queue.size())
    }

    @Test
    fun `update changes key and maintains order - smaller key`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        queue.update("A", 2) // Update A's key to be the smallest
        assertEquals(3, queue.size())
        assertEquals(2, queue.topKey(infinityKey))
        assertEquals("A", queue.top())
    }

    @Test
    fun `update changes key and maintains order - larger key`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)

        queue.update("B", 20) // Update B's key to be the largest
        assertEquals(3, queue.size())
        assertEquals(10, queue.topKey(infinityKey)) // A should be top now
        assertEquals("A", queue.top())

        // Pop A and check again
        assertEquals("A", queue.pop())
        assertEquals(15, queue.topKey(infinityKey)) // C should be top
        assertEquals("C", queue.top())
    }

    @Test
    fun `update with the same key does nothing`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.update("A", 10) // Update A with the same key

        assertEquals(2, queue.size())
        assertEquals(5, queue.topKey(infinityKey)) // B should still be top
        assertEquals("B", queue.top())
    }


    @Test
    fun `update non-existing element throws exception`() {
        queue.insert("A", 10)
        assertThrows<NoSuchElementException> { queue.update("B", 20) }
    }

    @Test
    fun `insert existing element acts as update`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("A", 2) // Re-insert A with a smaller key

        assertEquals(2, queue.size()) // Size should not increase
        assertEquals(2, queue.topKey(infinityKey)) // A should be top now
        assertEquals("A", queue.top())

        queue.insert("A", 20) // Re-insert A with a larger key
        assertEquals(2, queue.size())
        assertEquals(5, queue.topKey(infinityKey)) // B should be top now
        assertEquals("B", queue.top())
    }


    @Test
    fun `clear removes all elements`() {
        queue.insert("A", 10)
        queue.insert("B", 5)
        queue.insert("C", 15)
        assertFalse(queue.isEmpty())

        queue.clear()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
        assertFalse(queue.contains("A"))
        assertFalse(queue.contains("B"))
        assertFalse(queue.contains("C"))
        assertEquals(infinityKey, queue.topKey(infinityKey))
        assertThrows<NoSuchElementException> { queue.top() }
    }

    @Test
    fun `works with DStarLiteKey`() {
        val dsQueue = UpdatablePriorityQueue<String, Key>()
        val key1 = Key(10.0, 5.0)
        val key2 = Key(5.0, 1.0)
        val key3 = Key(5.0, 2.0)
        val infinityDsKey = Key.INFINITY

        dsQueue.insert("A", key1)
        dsQueue.insert("B", key2)
        dsQueue.insert("C", key3)

        assertEquals(3, dsQueue.size())
        assertEquals(key2, dsQueue.topKey(infinityDsKey))
        assertEquals("B", dsQueue.top())

        assertEquals("B", dsQueue.pop())
        assertEquals(key3, dsQueue.topKey(infinityDsKey))
        assertEquals("C", dsQueue.top())

        dsQueue.update("A", Key(1.0, 1.0))
        assertEquals(Key(1.0, 1.0), dsQueue.topKey(infinityDsKey))
        assertEquals("A", dsQueue.top())

        assertTrue(dsQueue.contains("C"))
        dsQueue.remove("C")
        assertFalse(dsQueue.contains("C"))
        assertEquals("A", dsQueue.top())
    }
}