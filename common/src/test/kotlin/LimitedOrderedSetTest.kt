import com.lambda.util.collections.LimitedOrderedSet
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

class LimitedOrderedSetTest {

    private lateinit var set: LimitedOrderedSet<String>

    @BeforeTest
    fun setUp() {
        // Initialize the set with a max size of 3
        set = LimitedOrderedSet(3)
    }

    @Test
    fun `test adding elements to the set`() {
        val added = set.add("Element1")
        assertTrue(added)
        assertEquals(2, set.size)
    }

    @Test
    fun `test adding more elements than maxSize`() {
        set.add("Element1")
        set.add("Element2")
        set.add("Element3")

        // Adding a fourth element when the max size is 3
        val added = set.add("Element4")
        assertTrue(added)
        assertEquals(3, set.size)  // The size should not exceed maxSize

        // The first element ("Element1") should be removed, as the set is maintaining order
        assertFalse(set.contains("Element1"))
        assertTrue(set.contains("Element2"))
        assertTrue(set.contains("Element3"))
        assertTrue(set.contains("Element4"))
    }

    @Test
    fun `test maintaining the order of elements`() {
        set.add("Element1")
        set.add("Element2")
        set.add("Element3")

        // Add a fourth element, and the first one should be removed
        set.add("Element4")

        // The order should now be Element2, Element3, Element4
        val expectedOrder = listOf("Element2", "Element3", "Element4")
        assertEquals(expectedOrder, set.toList())
    }

    @Test
    fun `test addAll method`() {
        // Initially, the set is empty
        set.add("Element1")
        set.add("Element2")

        // Adding multiple elements
        val added = set.addAll(listOf("Element3", "Element4"))

        assertTrue(added)
        assertEquals(3, set.size)  // Set should contain up to maxSize elements
        assertFalse(set.contains("Element1"))  // Element1 should be removed because we are over the max size
        assertTrue(set.contains("Element2"))
        assertTrue(set.contains("Element3"))
        assertTrue(set.contains("Element4"))
    }

    @Test
    fun `test adding more elements than maxSize with addAll`() {
        set.addAll(listOf("Element1", "Element2", "Element3"))

        // Add more elements, exceeding the max size
        val added = set.addAll(listOf("Element4", "Element5"))

        assertTrue(added)
        assertEquals(3, set.size)  // The set should maintain the max size
        assertFalse(set.contains("Element1"))
        assertFalse(set.contains("Element2"))
        assertTrue(set.contains("Element3"))
        assertTrue(set.contains("Element4"))
        assertTrue(set.contains("Element5"))
    }

    @Test
    fun `test the set size does not exceed maxSize`() {
        set.add("Element1")
        set.add("Element2")
        set.add("Element3")

        set.add("Element4") // This should push out "Element1"
        assertEquals(3, set.size)
        assertFalse(set.contains("Element1"))
        assertTrue(set.contains("Element2"))
        assertTrue(set.contains("Element3"))
        assertTrue(set.contains("Element4"))
    }
}
