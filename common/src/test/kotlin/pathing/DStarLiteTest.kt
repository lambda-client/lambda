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
import com.lambda.pathing.incremental.Key
import com.lambda.pathing.incremental.LazyGraph
import com.lambda.util.GraphUtil.createGridGraph18Conn
import com.lambda.util.GraphUtil.createGridGraph26Conn
import com.lambda.util.GraphUtil.createGridGraph6Conn
import com.lambda.util.GraphUtil.euclideanHeuristic
import com.lambda.util.GraphUtil.length
import com.lambda.util.GraphUtil.manhattanHeuristic
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import org.junit.jupiter.api.BeforeEach
import kotlin.math.sqrt
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

internal class DStarLiteTest {
    private lateinit var graph6: LazyGraph
    private lateinit var graph18: LazyGraph
    private lateinit var graph26: LazyGraph

    @BeforeEach
    fun setup() {
        graph6 = createGridGraph6Conn()
        graph18 = createGridGraph18Conn()
        graph26 = createGridGraph26Conn()
    }

    @Test
    fun `initialize sets goal rhs to 0 and adds to queue (grid graph)`() {
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(5, 0, 0)
        val dStar = DStarLite(graph6, startNode, goalNode, ::manhattanHeuristic) // Use any graph type

        assertEquals(0.0, dStar.rhs(goalNode))
        assertEquals(Double.POSITIVE_INFINITY, dStar.g(goalNode))
        assertEquals(1, dStar.U.size()) // Access internal U for test verification
        assertEquals(goalNode, dStar.U.top())
        // Initial key uses heuristic + km (0) + min(g=inf, rhs=0) = h(start, goal) + 0
        assertEquals(Key(manhattanHeuristic(startNode, goalNode), 0.0), dStar.U.topKey(Key.INFINITY))
    }

    @Test
    fun `computeShortestPath finds straight path on 6-conn graph`() {
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(5, 0, 0) // Straight line along X
        val dStar = DStarLite(graph6, startNode, goalNode, ::manhattanHeuristic)

        dStar.computeShortestPath()

        // Check g values (should be Manhattan distance)
        assertEquals(5.0, dStar.g(fastVectorOf(0, 0, 0)), 0.001)
        assertEquals(4.0, dStar.g(fastVectorOf(1, 0, 0)), 0.001)
        assertEquals(1.0, dStar.g(fastVectorOf(4, 0, 0)), 0.001)
        assertEquals(0.0, dStar.g(goalNode), 0.001)

        // Check path
        val path = dStar.path()
        assertEquals(6, path.size) // 0, 1, 2, 3, 4, 5
        assertEquals(startNode, path.first())
        assertEquals(goalNode, path.last())
        // Check intermediate node
        assertEquals(fastVectorOf(1, 0, 0), path[1])
    }

    @Test
    fun `computeShortestPath finds diagonal path on 26-conn graph`() {
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(2, 2, 2) // Cube diagonal
        // Use Euclidean heuristic for diagonal graphs
        val dStar = DStarLite(graph26, startNode, goalNode, ::euclideanHeuristic)

        dStar.computeShortestPath()

        // Expected g value is Euclidean distance * cost multiplier (which is 1 here)
        // Path: (0,0,0) -> (1,1,1) -> (2,2,2). Cost = 2 * sqrt(3)
        val expectedG = 2.0 * sqrt(3.0)
        assertEquals(expectedG, dStar.g(startNode), 0.001)
        assertEquals(sqrt(3.0), dStar.g(fastVectorOf(1, 1, 1)), 0.001)
        assertEquals(0.0, dStar.g(goalNode), 0.001)

        // Check path
        val path = dStar.path()
        // Optimal path is direct diagonal steps
        assertEquals(listOf(
            fastVectorOf(0, 0, 0),
            fastVectorOf(1, 1, 1),
            fastVectorOf(2, 2, 2)
        ), path)
    }

    @Test
    fun `computeShortestPath finds mixed path on 18-conn graph`() {
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(2, 1, 0) // Requires axis + diagonal
        val dStar = DStarLite(graph18, startNode, goalNode, ::euclideanHeuristic)

        dStar.computeShortestPath()

        // Optimal path likely (0,0,0) -> (1,0,0) -> (2,1,0)
        // Cost = sqrt(2) + 1.0
        val expectedG = sqrt(2.0) + 1.0
        assertEquals(expectedG, dStar.g(startNode), 0.001)
        assertEquals(1.0, dStar.g(fastVectorOf(1, 1, 0)), 0.001)
        assertEquals(0.0, dStar.g(goalNode), 0.001)

        // Check path
        val path = dStar.path()
        assertEquals(listOf(
            fastVectorOf(0, 0, 0),
            fastVectorOf(1, 0, 0),
            fastVectorOf(2, 1, 0)  // Axis move
        ), path)
    }

    @Test
    fun `updateStart changes km and path calculation (grid graph)`() {
        val startNode1 = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(5, 0, 0)
        val dStar = DStarLite(graph6, startNode1, goalNode, ::manhattanHeuristic)
        dStar.computeShortestPath()
        assertEquals(5.0, dStar.g(startNode1), 0.001) // g(0) should be 5.0

        val startNode2 = fastVectorOf(1, 0, 0)
        dStar.updateStart(startNode2)
        assertEquals(manhattanHeuristic(startNode1, startNode2), dStar.km, 0.001) // km = h(0,1) = 1.0

        dStar.computeShortestPath()
        assertEquals(4.0, dStar.g(startNode2), 0.001) // g(1) should be 4.0
        val path = dStar.path()
        assertEquals(5, path.size) // 1, 2, 3, 4, 5
        assertEquals(startNode2, path.first())
        assertEquals(goalNode, path.last())
    }

    @Test
    fun `computeShortestPath handles start equals goal (grid graph)`() {
        val startNode = fastVectorOf(3, 3, 3)
        val dStar = DStarLite(graph26, startNode, startNode, ::euclideanHeuristic) // start == goal
        dStar.computeShortestPath()

        assertEquals(0.0, dStar.g(startNode), 0.001)
        assertEquals(0.0, dStar.rhs(startNode), 0.001)
        val path = dStar.path()
        assertEquals(listOf(startNode), path) // Path is just the start/goal node
    }

    @Test
    fun `invalidate node forces path recalculation on 6-conn graph`() {
        // Create a graph with a straight path from (0,0,0) to (5,0,0)
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(5, 0, 0)
        val localBlockedNodes = mutableSetOf<FastVector>()
        val graph = createGridGraph6Conn(localBlockedNodes)
        val dStar = DStarLite(graph, startNode, goalNode, ::manhattanHeuristic)

        // Compute initial path
        dStar.computeShortestPath()
        val initialPath = dStar.path()

        // Verify initial path is straight
        assertEquals(6, initialPath.size)
        assertEquals(startNode, initialPath.first())
        assertEquals(goalNode, initialPath.last())
        assertEquals(fastVectorOf(1, 0, 0), initialPath[1])
        assertEquals(fastVectorOf(2, 0, 0), initialPath[2])

        // Invalidate a node in the middle of the path
        val nodeToInvalidate = fastVectorOf(2, 0, 0)
        localBlockedNodes.add(nodeToInvalidate)
        dStar.invalidate(nodeToInvalidate)

        // Recompute path
        dStar.computeShortestPath()
        val newPath = dStar.path()

        // Verify new path avoids the invalidated node
        assertTrue(nodeToInvalidate !in newPath, "Path should not contain the invalidated node")
        assertEquals(startNode, newPath.first())
        assertEquals(goalNode, newPath.last())

        // The new path should be longer as it has to go around the blocked node
        assertTrue(newPath.size > initialPath.size, "New path should be longer than the initial path")
    }

    @Test
    fun `invalidate multiple nodes forces complex rerouting on 6-conn graph`() {
        // Create a graph with a straight path from (0,0,0) to (5,0,0)
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(5, 0, 0)

        // Pre-block nodes in the blockedNodes set before creating the graph
        val localBlockedNodes = mutableSetOf<FastVector>()
        val nodesToBlock = listOf(
            fastVectorOf(2, 0, 0),  // Block straight path
            fastVectorOf(2, 1, 0),  // Block one alternative
            fastVectorOf(2, -1, 0)  // Block another alternative
        )

        // Add nodes to blocked set before creating the graph
        localBlockedNodes.addAll(nodesToBlock)

        // Create graph with pre-blocked nodes
        val graph = createGridGraph6Conn(localBlockedNodes)
        val dStar = DStarLite(graph, startNode, goalNode, ::manhattanHeuristic)

        // Compute path with pre-blocked nodes
        dStar.computeShortestPath()
        val path = dStar.path()

        // Print debug info about the path
        println("[DEBUG_LOG] Path with pre-blocked nodes:")
        path.forEach { node ->
            println("[DEBUG_LOG] - Node: $node (x=${node.x}, y=${node.y}, z=${node.z})")
        }

        // Verify path avoids all blocked nodes
        nodesToBlock.forEach { node ->
            assertTrue(node !in path, "Path should not contain blocked node $node")
        }

        // Verify path starts and ends at the correct nodes
        assertEquals(startNode, path.first())
        assertEquals(goalNode, path.last())

        // The path should be longer than a straight line (which would be 6 nodes)
        assertTrue(path.size > 6, "Path should be longer than a straight line")
    }

    @Test
    fun `nodeInitializer correctly omits blocked nodes from graph`() {
        // Create a set of blocked nodes
        val localBlockedNodes = mutableSetOf<FastVector>()
        val nodeToBlock = fastVectorOf(2, 0, 0)
        localBlockedNodes.add(nodeToBlock)

        // Create graph with blocked nodes
        val graph = createGridGraph6Conn(localBlockedNodes)

        // Check that the nodeInitializer correctly omits the blocked node
        val startNode = fastVectorOf(1, 0, 0)  // Node adjacent to blocked node
        val successors = graph.successors(startNode)

        // The blocked node should not be in the successors
        assertFalse(nodeToBlock in successors.keys, "Blocked node should not be in successors")

        // Create a DStarLite instance and compute path
        val goalNode = fastVectorOf(3, 0, 0)  // Goal is on the other side of blocked node
        val dStar = DStarLite(graph, startNode, goalNode, ::manhattanHeuristic)
        dStar.computeShortestPath()
        val path = dStar.path()

        // Verify path avoids the blocked node
        assertTrue(nodeToBlock !in path, "Path should not contain the blocked node")
        assertEquals(startNode, path.first())
        assertEquals(goalNode, path.last())
    }

    @Test
    fun `invalidate node updates connectivity on diagonal graph`() {
        // Create a graph with diagonal connectivity
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(2, 2, 0)
        val localBlockedNodes = mutableSetOf<FastVector>()
        val graph = createGridGraph18Conn(localBlockedNodes)
        val dStar = DStarLite(graph, startNode, goalNode, ::euclideanHeuristic)

        // Compute initial path
        dStar.computeShortestPath()
        val initialPath = dStar.path()

        // Initial path should be diagonal
        assertEquals(3, initialPath.size)
        assertEquals(startNode, initialPath.first())
        assertEquals(fastVectorOf(1, 1, 0), initialPath[1])
        assertEquals(goalNode, initialPath.last())

        // Invalidate the diagonal node
        val nodeToInvalidate = fastVectorOf(1, 1, 0)
        localBlockedNodes.add(nodeToInvalidate)
        dStar.invalidate(nodeToInvalidate)

        // Recompute path
        dStar.computeShortestPath()
        val newPath = dStar.path()

        // Verify new path avoids the invalidated node
        assertTrue(nodeToInvalidate !in newPath, "Path should not contain the invalidated node")
        assertEquals(startNode, newPath.first())
        assertEquals(goalNode, newPath.last())

        // The new path should go around the blocked diagonal
        assertTrue(newPath.size > initialPath.size, "New path should be longer than the initial path")
    }

    @Test
    fun `invalidate node correctly updates rhs values for new nodes`() {
        // Create a straight line path
        val startNode = fastVectorOf(0, 0, 0)
        val goalNode = fastVectorOf(0, 0, 3)
        val localBlockedNodes = mutableSetOf<FastVector>()
        val graph = createGridGraph26Conn(localBlockedNodes)
        val dStar = DStarLite(graph, startNode, goalNode, ::euclideanHeuristic)

        // Compute initial path
        dStar.computeShortestPath()
        val initialPath = dStar.path()

        // Initial path should be straight
        assertEquals(4, initialPath.size)
        assertEquals(startNode, initialPath.first())
        assertEquals(fastVectorOf(0, 0, 1), initialPath[1])
        assertEquals(fastVectorOf(0, 0, 2), initialPath[2])
        assertEquals(goalNode, initialPath.last())

        // Block a node in the middle of the path
        val nodeToInvalidate = fastVectorOf(0, 0, 1)
        localBlockedNodes.add(nodeToInvalidate)
        dStar.invalidate(nodeToInvalidate)

        // Recompute path
        dStar.computeShortestPath()
        val newPath = dStar.path()

        // Verify new path avoids the invalidated node
        assertTrue(nodeToInvalidate !in newPath, "Path should not contain the invalidated node")
        assertEquals(startNode, newPath.first())
        assertEquals(goalNode, newPath.last())

        // Check if any new nodes were created (nodes that weren't in the initial path)
        val newNodes = newPath.filter { it !in initialPath && it != startNode && it != goalNode }

        // Verify that new nodes have correct rhs values
        newNodes.forEach { node ->
            val rhs = dStar.rhs(node)
            val minSuccCost = graph.successors(node)
                .mapNotNull { (succ, cost) ->
                    if (cost == Double.POSITIVE_INFINITY) null else cost + dStar.g(succ)
                }
                .minOrNull() ?: Double.POSITIVE_INFINITY

            assertEquals(minSuccCost, rhs, 0.001, 
                "Node $node should have rhs value equal to minimum successor cost")
        }

        // The new path should go around the blocked node
        assertTrue(newPath.length() >= initialPath.length(), "New path should be at least as long as the initial path")
    }
}
