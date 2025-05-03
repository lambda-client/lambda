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

import com.lambda.pathing.dstar.DStarLite
import com.lambda.util.GraphUtil.createGridGraph18Conn
import com.lambda.util.GraphUtil.createGridGraph26Conn
import com.lambda.util.GraphUtil.createGridGraph6Conn
import com.lambda.util.GraphUtil.euclideanHeuristic
import com.lambda.util.GraphUtil.length
import com.lambda.util.GraphUtil.string
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for graph maintenance consistency in the D* Lite algorithm.
 * These tests verify that the graph state after invalidation and pruning
 * is consistent with a fresh graph created with the same blocked nodes.
 */
class GraphConsistencyTest {
    /**
     * Simple test with a single blocked node in a 6-connectivity graph.
     */
    @Test
    fun `graph consistency with single blocked node N6`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Block a node and invalidate
        val blockedNode = fastVectorOf(0, 0, 4)
        blockedNodes.add(blockedNode)

        dStar1.invalidate(blockedNode)
        dStar1.computeShortestPath()
        val blocked = dStar1.path()

        // Verify that the path changed after blocking
        val initialLength = initialPath.length()
        val blockedLength = blocked.length()
        assertTrue(initialLength < blockedLength,
            "Initial path length ($initialLength) should be less than blocked path length ($blockedLength)")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(blocked, path2,
            "Paths should be identical after invalidation.\nPath1: ${blocked.string()}\nPath2: ${path2.string()}")

        // Verify the graph structure is consistent
        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences,
            "Graph structures should be identical: $graphDifferences")
        assertFalse(valueDifferences.isNotEmpty(),
            "Node values should be identical: ${valueDifferences.joinToString("\n  ")}")
    }

    /**
     * Test with multiple blocked nodes in a 6-connectivity graph.
     */
    @Test
    fun `graph consistency with multiple blocked nodes N6`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Block multiple nodes and invalidate one by one
        val blockedNode1 = fastVectorOf(0, 0, 4)
        val blockedNode2 = fastVectorOf(1, 0, 4)
        val blockedNode3 = fastVectorOf(-1, 0, 4)

        blockedNodes.add(blockedNode1)
        dStar1.invalidate(blockedNode1)

        blockedNodes.add(blockedNode2)
        dStar1.invalidate(blockedNode2)

        blockedNodes.add(blockedNode3)
        dStar1.invalidate(blockedNode3)

        dStar1.computeShortestPath()
        val blocked = dStar1.path()

        // Verify that the path changed after blocking
        val initialLength = initialPath.length()
        val blockedLength = blocked.length()
        assertTrue(initialLength < blockedLength,
            "Initial path length ($initialLength) should be less than blocked path length ($blockedLength)")

        // Create a fresh graph with all blocked nodes and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(blocked, path2,
            "Paths should be identical after invalidation.\nPath1: ${blocked.string()}\nPath2: ${path2.string()}")

        // Verify the graph structure is consistent
        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences,
            "Graph structures should be identical: $graphDifferences")
        assertFalse(valueDifferences.isNotEmpty(),
            "Node values should be identical: ${valueDifferences.joinToString("\n  ")}")
    }

    /**
     * Test with a more complex graph structure (18-connectivity).
     */
    @Test
    fun `graph consistency with 18-connectivity`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph18Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Block a node and invalidate
        val blockedNode = fastVectorOf(0, 0, 4)
        blockedNodes.add(blockedNode)

        dStar1.invalidate(blockedNode)
        dStar1.computeShortestPath()
        val blocked = dStar1.path()

        // Verify that the path changed after blocking
        val initialLength = initialPath.length()
        val blockedLength = blocked.length()
        assertTrue(initialLength < blockedLength,
            "Initial path length ($initialLength) should be less than blocked path length ($blockedLength)")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph18Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(blocked, path2,
            "Paths should be identical after invalidation.\nPath1: ${blocked.string()}\nPath2: ${path2.string()}")

        // Verify graph structure is consistent
        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences,
            "Graph structures should be identical: $graphDifferences")
        assertFalse(valueDifferences.isNotEmpty(),
            "Node values should be identical: ${valueDifferences.joinToString("\n  ")}")
    }

    /**
     * Test with a more complex graph structure (26-connectivity).
     */
    @Test
    fun `graph consistency with 26-connectivity`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph26Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Block a node and invalidate
        val blockedNode = fastVectorOf(0, 0, 4)
        blockedNodes.add(blockedNode)

        dStar1.invalidate(blockedNode)
        dStar1.computeShortestPath()
        val blocked = dStar1.path()

        // Verify that the path changed after blocking
        val initialLength = initialPath.length()
        val blockedLength = blocked.length()
        assertTrue(initialLength < blockedLength,
            "Initial path length ($initialLength) should be less than blocked path length ($blockedLength)")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph26Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(blocked, path2,
            "Paths should be identical after invalidation.\nPath1: ${blocked.string()}\nPath2: ${path2.string()}")

        // Verify graph structure is consistent
        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences,
            "Graph structures should be identical: $graphDifferences")
        assertFalse(valueDifferences.isNotEmpty(),
            "Node values should be identical: ${valueDifferences.joinToString("\n  ")}")
    }

    /**
     * Test with a node that becomes unblocked (simulating a world update where a block changes).
     */
    @Test
    fun `graph consistency when unblocking a node`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Block a node initially
        val nodeToToggle = fastVectorOf(0, 0, 4)
        blockedNodes.add(nodeToToggle)

        // Create initial graph with the blocked node
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Unblock the node and invalidate
        blockedNodes.remove(nodeToToggle)

        // The nodeInitializer should handle this correctly
        dStar1.invalidate(nodeToToggle)
        dStar1.computeShortestPath()
        val path1 = dStar1.path()

        // Verify that the path changed after blocking
        val initialLength = initialPath.length()
        val unblockedLength = path1.length()
        assertTrue(initialLength > unblockedLength,
            "Initial path length ($initialLength) should be longer than unblocked path length ($unblockedLength)")

        // Create a fresh graph without the blocked node and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2,
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")

        // Verify graph structure is consistent
        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences,
            "Graph structures should be identical: $graphDifferences")
        assertFalse(valueDifferences.isNotEmpty(),
            "Node values should be identical: ${valueDifferences.joinToString("\n  ")}")
    }
}
