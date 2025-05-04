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

import com.lambda.pathing.incremental.DStarLite
import com.lambda.util.GraphUtil.createGridGraph6Conn
import com.lambda.util.GraphUtil.createGridGraph18Conn
import com.lambda.util.GraphUtil.createGridGraph26Conn
import com.lambda.util.GraphUtil.euclideanHeuristic
import com.lambda.util.GraphUtil.length
import com.lambda.util.GraphUtil.string
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for path consistency in the D* Lite algorithm.
 * These tests verify that the paths produced after invalidation
 * match the paths produced by a freshly created graph with the same blocked nodes.
 */
class PathConsistencyTest {

    /**
     * Test path consistency with a single blocked node in a 6-connectivity graph.
     */
    @Test
    fun `path consistency with single blocked node N6`() {
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
        val path1 = dStar1.path()

        assertTrue(!path1.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${path1.string()}")

        // Verify that the path changed after blocking
        assertTrue(initialPath.length() < path1.length(),
            "Initial path length (${initialPath.length()}) should be less than blocked path length (${path1.length()})")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency with multiple blocked nodes in a 6-connectivity graph.
     */
    @Test
    fun `path consistency with multiple blocked nodes N6`() {
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
        val path1 = dStar1.path()

        blockedNodes.forEach { blockedNode ->
            assertTrue(!path1.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${path1.string()}")
        }

        val initialPathLength = initialPath.length()
        val length1 = path1.length()
        // Verify that the path changed after blocking
        assertTrue(initialPathLength < length1,
            "Initial path ${initialPath.string()} length ($initialPathLength) should be less than blocked path ${path1.string()} size ($length1)")

        // Create a fresh graph with all blocked nodes and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency with a more complex graph structure (18-connectivity).
     */
    @Test
    fun `path consistency with 18-connectivity`() {
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
        val path1 = dStar1.path()

        assertTrue(!path1.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${path1.string()}")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph18Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency with a more complex graph structure (26-connectivity).
     */
    @Test
    fun `path consistency with 26-connectivity`() {
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
        val path1 = dStar1.path()

        assertTrue(!path1.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${path1.string()}")

        // Create a fresh graph with the blocked node and compute path
        val graph2 = createGridGraph26Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency when unblocking a node.
     */
    @Test
    fun `path consistency when unblocking a node`() {
        val startNode = fastVectorOf(0, 0, 10)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create a 6x6 wall to force a detour
        (-3..3).forEach { x ->
            (-3..3).forEach { y ->
                blockedNodes.add(fastVectorOf(x, y, 5))
            }
        }

        // Create initial graph with blocked nodes
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        blockedNodes.forEach { blockedNode ->
            assertTrue(!initialPath.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${initialPath.string()}")
        }

        // Unblock one node in the middle
        val nodeToToggle = fastVectorOf(0, 0, 5)
        blockedNodes.remove(nodeToToggle)

        // Now it should path through the hole in the wall
        dStar1.invalidate(nodeToToggle)
        dStar1.computeShortestPath()
        val path1 = dStar1.path()

        // Create a fresh graph with the updated blocked nodes and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency with a complex scenario involving multiple invalidations.
     */
    @Test
    fun `path consistency with complex scenario`() {
        val startNode = fastVectorOf(0, 0, 10)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        // Block multiple nodes to create a complex scenario
        (-3..3).forEach { x ->
            (-3..3).forEach { y ->
                val blockedNode = fastVectorOf(x, y, 5)
                blockedNodes.add(blockedNode)
                dStar1.invalidate(blockedNode)
            }
        }

        dStar1.computeShortestPath()
        val path1 = dStar1.path()

        blockedNodes.forEach { blockedNode ->
            assertTrue(!path1.contains(blockedNode), "Blocked node ${blockedNode.string} should not be in path ${path1.string()}")
        }

        // Verify that the path changed after blocking
        assertTrue(initialPath.length() < path1.length(),
            "Initial path length (${initialPath.length()}) should be less than blocked path length (${path1.length()})")

        // Create a fresh graph with all blocked nodes and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical
        assertEquals(path1, path2, 
            "Paths should be identical after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }

    /**
     * Test path consistency with a disconnected graph scenario.
     */
    @Test
    fun `path consistency with disconnected graph`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()

        // Create initial graph and compute path
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()

        // Block nodes to completely disconnect start from goal
        // Block all nodes at z=3
        for (x in -2..2) {
            for (y in -2..2) {
                val blockedNode = fastVectorOf(x, y, 3)
                blockedNodes.add(blockedNode)
                dStar1.invalidate(blockedNode)
            }
        }

        dStar1.computeShortestPath()
        val path1 = dStar1.path()

        // Create a fresh graph with all blocked nodes and compute path
        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        // Verify paths are identical (both should be empty or contain only the start node)
        assertEquals(path1.length(), path2.length(),
            "Paths should have identical length after invalidation.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")
    }
}
