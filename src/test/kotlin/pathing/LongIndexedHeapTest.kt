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

package pathing

import com.lambda.pathing.graph.LongIndexedHeap
import com.lambda.pathing.graph.LongIndexedHeap.Companion.compareKeys
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The indexed heap plus the `(k1, k2)` key order the old `Key` class used to carry. */
class LongIndexedHeapTest {
    private lateinit var queue: LongIndexedHeap

    private val a = 1L
    private val b = 2L
    private val c = 3L

    @BeforeTest
    fun setup() {
        queue = LongIndexedHeap()
    }

    private fun LongIndexedHeap.insert(node: Long, key: Int) = insert(node, key.toDouble(), 0.0)

    @Test
    fun `empty queue reports infinity top key and throws on top or pop`() {
        assertTrue(queue.isEmpty())
        assertEquals(Double.POSITIVE_INFINITY, queue.topFirst())
        assertEquals(Double.POSITIVE_INFINITY, queue.topSecond())
        assertFalse(queue.topIsBelow(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))
        assertTrue(queue.topIsAbove(1e300, 1e300))
        assertFailsWith<NoSuchElementException> { queue.top() }
        assertFailsWith<NoSuchElementException> { queue.pop() }
        assertFailsWith<NoSuchElementException> { queue.update(a, 1.0, 1.0) }
    }

    @Test
    fun `insert pop and remove maintain priority order`() {
        queue.insert(a, 10)
        queue.insert(b, 5)
        queue.insert(c, 15)

        assertEquals(b, queue.top())
        assertEquals(b, queue.pop())
        assertFalse(b in queue)
        assertEquals(a, queue.pop())
        assertEquals(c, queue.pop())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `update changes ordering`() {
        queue.insert(a, 10)
        queue.insert(b, 5)
        queue.insert(c, 15)

        queue.update(c, 1.0, 0.0)
        assertEquals(c, queue.top())

        queue.update(c, 20.0, 0.0)
        assertEquals(b, queue.top())
    }

    @Test
    fun `insert existing value updates key`() {
        queue.insert(a, 10)
        queue.insert(a, 2)

        assertEquals(1, queue.size())
        assertEquals(a, queue.top())
        assertEquals(2.0, queue.topFirst())
    }

    @Test
    fun `keys are compared lexicographically`() {
        assertTrue(compareKeys(1.0, 10.0, 2.0, 1.0) < 0)
        assertTrue(compareKeys(5.0, 1.0, 5.0, 2.0) < 0)
        assertTrue(compareKeys(1000.0, 1000.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY) < 0)
        assertEquals(0, compareKeys(1.0, 2.0, 1.0, 2.0))
        assertTrue(compareKeys(1.0, 2.0, 1.0, 3.0) != 0)
        assertTrue(compareKeys(1.0, 2.0, 2.0, 2.0) != 0)

        queue.insert(a, 10.0, 5.0)
        queue.insert(b, 5.0, 1.0)
        queue.insert(c, 5.0, 2.0)

        assertEquals(5.0, queue.topFirst())
        assertEquals(1.0, queue.topSecond())
        assertEquals(b, queue.top())
        assertTrue(queue.topIsBelow(5.0, 2.0))
        assertFalse(queue.topIsBelow(5.0, 1.0))
        assertFalse(queue.topIsAbove(5.0, 1.0))
        assertTrue(queue.topIsAbove(4.0, 100.0))
        assertEquals(listOf(b, c, a), listOf(queue.pop(), queue.pop(), queue.pop()))
    }

    @Test
    fun `removing an interior node restores the heap in the required direction`() {
        // Exercise both replacement cases repeatedly rather than relying on
        // the root-only pop path.
        val expected = (0 until 200).associateWith { (it * 73) % 211 }.toMutableMap()
        for ((value, key) in expected) queue.insert(value.toLong(), key)

        for (value in (0 until 200 step 3)) {
            assertTrue(queue.remove(value.toLong()))
            expected.remove(value)
        }

        val ordered = ArrayList<Int>()
        while (!queue.isEmpty()) ordered += queue.pop().toInt()
        assertEquals(expected.entries.sortedBy { it.value }.map { it.key }, ordered)
    }

    @Test
    fun `indexed heap matches a reference map through random updates and removals`() {
        repeat(40) { seed ->
            val random = Random(seed)
            val heap = LongIndexedHeap(4)
            val reference = mutableMapOf<Long, Pair<Double, Double>>()
            val order = compareBy<Pair<Double, Double>> { it.first }.thenBy { it.second }

            repeat(2_000) {
                val value = random.nextInt(80).toLong()
                when (random.nextInt(4)) {
                    0, 1 -> {
                        val key = random.nextInt(50).toDouble() to random.nextInt(20).toDouble()
                        heap.insert(value, key.first, key.second)
                        reference[value] = key
                    }
                    2 -> {
                        assertEquals(reference.remove(value) != null, heap.remove(value), "seed=$seed")
                    }
                    else -> if (reference.isNotEmpty()) {
                        val expectedMinimum = reference.values.minWith(order)
                        val popped = heap.pop()
                        assertEquals(expectedMinimum, reference.remove(popped), "seed=$seed")
                    }
                }

                assertEquals(reference.size, heap.size(), "seed=$seed")
                if (reference.isEmpty()) {
                    assertTrue(heap.isEmpty(), "seed=$seed")
                } else {
                    assertFalse(heap.isEmpty(), "seed=$seed")
                    val minimum = reference.values.minWith(order)
                    assertEquals(minimum, heap.topFirst() to heap.topSecond(), "seed=$seed")
                    assertEquals(reference.getValue(heap.top()), heap.topFirst() to heap.topSecond(), "seed=$seed")
                }
            }
        }
    }
}
