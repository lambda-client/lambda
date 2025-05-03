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

package com.lambda.util

import com.lambda.util.GraphUtil.n6
import com.lambda.util.GraphUtil.n18
import com.lambda.util.GraphUtil.n26
import com.lambda.util.GraphUtil.neighborhood
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the neighbor functions in GraphUtil.
 */
class GraphUtilTest {
    
    /**
     * Test for n6 function which should return 6-connectivity neighbors
     * (only axis-aligned moves).
     */
    @Test
    fun `test n6 connectivity`() {
        val origin = fastVectorOf(0, 0, 0)
        val neighbors = n6(origin)
        
        // Should have exactly 6 neighbors
        assertEquals(6, neighbors.size, "n6 should return exactly 6 neighbors")
        
        // Expected neighbors (axis-aligned)
        val expectedNeighbors = setOf(
            fastVectorOf(1, 0, 0),
            fastVectorOf(-1, 0, 0),
            fastVectorOf(0, 1, 0),
            fastVectorOf(0, -1, 0),
            fastVectorOf(0, 0, 1),
            fastVectorOf(0, 0, -1)
        )
        
        // Check that all expected neighbors are present
        for (neighbor in expectedNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected neighbor $neighbor not found")
            assertEquals(1.0, neighbors[neighbor], "Cost for axis-aligned neighbor should be 1.0")
        }
    }
    
    /**
     * Test for n18 function which should return 18-connectivity neighbors
     * (axis-aligned + face diagonal moves).
     */
    @Test
    fun `test n18 connectivity`() {
        val origin = fastVectorOf(0, 0, 0)
        val neighbors = n18(origin)
        
        // Should have exactly 18 neighbors
        assertEquals(18, neighbors.size, "n18 should return exactly 18 neighbors")
        
        // Expected axis-aligned neighbors (6)
        val expectedAxisNeighbors = setOf(
            fastVectorOf(1, 0, 0),
            fastVectorOf(-1, 0, 0),
            fastVectorOf(0, 1, 0),
            fastVectorOf(0, -1, 0),
            fastVectorOf(0, 0, 1),
            fastVectorOf(0, 0, -1)
        )
        
        // Expected face diagonal neighbors (12)
        val expectedFaceDiagonalNeighbors = setOf(
            // XY plane diagonals
            fastVectorOf(1, 1, 0),
            fastVectorOf(1, -1, 0),
            fastVectorOf(-1, 1, 0),
            fastVectorOf(-1, -1, 0),
            // XZ plane diagonals
            fastVectorOf(1, 0, 1),
            fastVectorOf(1, 0, -1),
            fastVectorOf(-1, 0, 1),
            fastVectorOf(-1, 0, -1),
            // YZ plane diagonals
            fastVectorOf(0, 1, 1),
            fastVectorOf(0, 1, -1),
            fastVectorOf(0, -1, 1),
            fastVectorOf(0, -1, -1)
        )
        
        // Check that all expected axis-aligned neighbors are present
        for (neighbor in expectedAxisNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected axis-aligned neighbor $neighbor not found")
            assertEquals(1.0, neighbors[neighbor], "Cost for axis-aligned neighbor should be 1.0")
        }
        
        // Check that all expected face diagonal neighbors are present
        for (neighbor in expectedFaceDiagonalNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected face diagonal neighbor $neighbor not found")
            assertEquals(sqrt(2.0), neighbors[neighbor], "Cost for face diagonal neighbor should be sqrt(2.0)")
        }
    }
    
    /**
     * Test for n26 function which should return 26-connectivity neighbors
     * (axis-aligned + face diagonal + cube diagonal moves).
     */
    @Test
    fun `test n26 connectivity`() {
        val origin = fastVectorOf(0, 0, 0)
        val neighbors = n26(origin)
        
        // Should have exactly 26 neighbors
        assertEquals(26, neighbors.size, "n26 should return exactly 26 neighbors")
        
        // Expected axis-aligned neighbors (6)
        val expectedAxisNeighbors = setOf(
            fastVectorOf(1, 0, 0),
            fastVectorOf(-1, 0, 0),
            fastVectorOf(0, 1, 0),
            fastVectorOf(0, -1, 0),
            fastVectorOf(0, 0, 1),
            fastVectorOf(0, 0, -1)
        )
        
        // Expected face diagonal neighbors (12)
        val expectedFaceDiagonalNeighbors = setOf(
            // XY plane diagonals
            fastVectorOf(1, 1, 0),
            fastVectorOf(1, -1, 0),
            fastVectorOf(-1, 1, 0),
            fastVectorOf(-1, -1, 0),
            // XZ plane diagonals
            fastVectorOf(1, 0, 1),
            fastVectorOf(1, 0, -1),
            fastVectorOf(-1, 0, 1),
            fastVectorOf(-1, 0, -1),
            // YZ plane diagonals
            fastVectorOf(0, 1, 1),
            fastVectorOf(0, 1, -1),
            fastVectorOf(0, -1, 1),
            fastVectorOf(0, -1, -1)
        )
        
        // Expected cube diagonal neighbors (8)
        val expectedCubeDiagonalNeighbors = setOf(
            fastVectorOf(1, 1, 1),
            fastVectorOf(1, 1, -1),
            fastVectorOf(1, -1, 1),
            fastVectorOf(1, -1, -1),
            fastVectorOf(-1, 1, 1),
            fastVectorOf(-1, 1, -1),
            fastVectorOf(-1, -1, 1),
            fastVectorOf(-1, -1, -1)
        )
        
        // Check that all expected axis-aligned neighbors are present
        for (neighbor in expectedAxisNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected axis-aligned neighbor $neighbor not found")
            assertEquals(1.0, neighbors[neighbor], "Cost for axis-aligned neighbor should be 1.0")
        }
        
        // Check that all expected face diagonal neighbors are present
        for (neighbor in expectedFaceDiagonalNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected face diagonal neighbor $neighbor not found")
            assertEquals(sqrt(2.0), neighbors[neighbor], "Cost for face diagonal neighbor should be sqrt(2.0)")
        }
        
        // Check that all expected cube diagonal neighbors are present
        for (neighbor in expectedCubeDiagonalNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected cube diagonal neighbor $neighbor not found")
            assertEquals(sqrt(3.0), neighbors[neighbor], "Cost for cube diagonal neighbor should be sqrt(3.0)")
        }
    }
    
    /**
     * Test for the neighborhood function with custom distance parameters.
     */
    @Test
    fun `test neighborhood with custom parameters`() {
        val origin = fastVectorOf(0, 0, 0)
        
        // Test with minDistSq=2, maxDistSq=2 (should only return face diagonals)
        val faceDiagonalNeighbors = neighborhood(origin, minDistSq = 2, maxDistSq = 2)
        assertEquals(12, faceDiagonalNeighbors.size, "Should return exactly 12 face diagonal neighbors")
        
        // Test with minDistSq=3, maxDistSq=3 (should only return cube diagonals)
        val cubeDiagonalNeighbors = neighborhood(origin, minDistSq = 3, maxDistSq = 3)
        assertEquals(8, cubeDiagonalNeighbors.size, "Should return exactly 8 cube diagonal neighbors")
        
        // Test with minDistSq=1, maxDistSq=3 (should return all neighbors, same as n26)
        val allNeighbors = neighborhood(origin, minDistSq = 1, maxDistSq = 3)
        assertEquals(26, allNeighbors.size, "Should return exactly 26 neighbors (same as n26)")
        
        // Test with invalid range (should return empty map)
        val emptyNeighbors = neighborhood(origin, minDistSq = 4, maxDistSq = 5)
        assertEquals(0, emptyNeighbors.size, "Should return empty map for invalid distance range")
    }
    
    /**
     * Test for the neighborhood function with non-origin center point.
     */
    @Test
    fun `test neighborhood with non-origin center`() {
        val center = fastVectorOf(10, 20, 30)
        val neighbors = n6(center)
        
        // Should have exactly 6 neighbors
        assertEquals(6, neighbors.size, "n6 should return exactly 6 neighbors")
        
        // Expected neighbors (axis-aligned)
        val expectedNeighbors = setOf(
            fastVectorOf(11, 20, 30),
            fastVectorOf(9, 20, 30),
            fastVectorOf(10, 21, 30),
            fastVectorOf(10, 19, 30),
            fastVectorOf(10, 20, 31),
            fastVectorOf(10, 20, 29)
        )
        
        // Check that all expected neighbors are present
        for (neighbor in expectedNeighbors) {
            assertTrue(neighbor in neighbors.keys, "Expected neighbor $neighbor not found")
            assertEquals(1.0, neighbors[neighbor], "Cost for axis-aligned neighbor should be 1.0")
        }
    }
}