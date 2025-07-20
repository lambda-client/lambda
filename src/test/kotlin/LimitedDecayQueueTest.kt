import com.lambda.util.collections.LimitedDecayQueue
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

class LimitedDecayQueueTest {

    private lateinit var queue: LimitedDecayQueue<String>
    private lateinit var onDecayCalled: MutableList<String>

    @BeforeTest
    fun setUp() {
        // Initialize the onDecay callback
        onDecayCalled = mutableListOf()
        queue = LimitedDecayQueue(3, 1000) { onDecayCalled.add(it) } // 1 second decay time
    }

    @Test
    fun `test add element to queue`() {
        // Add an element
        val result = queue.add("Element1")

        assertTrue(result)
        assertEquals(1, queue.size)
    }

    @Test
    fun `test add element beyond size limit`() {
        queue.add("Element1")
        queue.add("Element2")
        queue.add("Element3")

        // Try adding a 4th element when size limit is 3
        val result = queue.add("Element4")

        assertFalse(result) // Should fail to add
        assertEquals(3, queue.size) // Size should remain 3
    }

    @Test
    fun `test elements expire after max age`() {
        queue.add("Element1")
        queue.add("Element2")

        // Simulate passage of time (greater than maxAge)
        TimeUnit.MILLISECONDS.sleep(1500)

        // Add new element after expiration
        queue.add("Element3")

        // Ensure expired elements are removed and "onDecay" callback is triggered
        assertEquals(1, queue.size)
        assertTrue(onDecayCalled.contains("Element1"))
        assertTrue(onDecayCalled.contains("Element2"))
    }

    @Test
    fun `test add all elements`() {
        queue.add("Element1")
        queue.add("Element2")

        val result = queue.addAll(listOf("Element3", "Element4"))

        assertTrue(result)
        assertEquals(3, queue.size) // Size limit is 3
    }

    @Test
    fun `test remove element`() {
        queue.add("Element1")
        queue.add("Element2")

        val result = queue.remove("Element1")

        assertTrue(result)
        assertEquals(1, queue.size)
    }

    @Test
    fun `test remove all elements`() {
        queue.add("Element1")
        queue.add("Element2")
        queue.add("Element3")

        val result = queue.removeAll(listOf("Element1", "Element2"))

        assertTrue(result)
        assertEquals(1, queue.size) // Only "Element3" should remain
    }

    @Test
    fun `test retain all elements`() {
        queue.add("Element1")
        queue.add("Element2")
        queue.add("Element3")

        val result = queue.retainAll(listOf("Element2", "Element3"))

        assertTrue(result)
        assertEquals(2, queue.size) // Only "Element2" and "Element3" should remain
    }

    @Test
    fun `test clear the queue`() {
        queue.add("Element1")
        queue.add("Element2")
        queue.clear()

        assertEquals(0, queue.size) // Queue should be empty
    }

    @Test
    fun `test set max size`() {
        queue.add("Element1")
        queue.add("Element2")
        queue.add("Element3")

        queue.setSizeLimit(2) // Reduce size limit to 2

        assertEquals(2, queue.size)
    }

    @Test
    fun `test set decay time`() {
        queue.add("Element1")
        queue.add("Element2")

        queue.setDecayTime(500) // Set a shorter decay time of 500 ms

        // Simulate passage of time (greater than decay time)
        TimeUnit.MILLISECONDS.sleep(600)

        queue.add("Element3")

        // Ensure expired elements are removed and "onDecay" callback is triggered
        assertEquals(1, queue.size)
        assertTrue(onDecayCalled.contains("Element1"))
        assertTrue(onDecayCalled.contains("Element2"))
    }

    @Test
    fun `test clean up function when iterating`() {
        queue.add("Element1")
        queue.add("Element2")

        // Simulate some delay to allow elements to decay
        TimeUnit.MILLISECONDS.sleep(1500)

        queue.add("Element3")

        // Iterator should only return "Element3" because the others are expired
        val elements = queue.toList()
        assertEquals(1, elements.size)
        assertEquals("Element3", elements[0])
    }
}
