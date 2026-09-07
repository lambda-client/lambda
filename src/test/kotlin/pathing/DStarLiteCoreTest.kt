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

import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.LongLazyGraph
import it.unimi.dsi.fastutil.longs.LongArrayList
import pathing.GridGraphTestUtil.Connectivity.N26
import pathing.GridGraphTestUtil.Connectivity.N6
import pathing.GridGraphTestUtil.affectedNodes
import pathing.GridGraphTestUtil.euclidean
import pathing.GridGraphTestUtil.graph
import pathing.GridGraphTestUtil.length
import pathing.GridGraphTestUtil.manhattan
import pathing.GridGraphTestUtil.node
import it.unimi.dsi.fastutil.longs.LongArrayList as Nodes
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DStarLiteCoreTest {
    private fun affected(node: Long, connectivity: GridGraphTestUtil.Connectivity) =
        LongArrayList(affectedNodes(node, connectivity))

    @Test
    fun `initialize sets goal rhs to zero and queues the goal`() {
        val start = node(0, 0, 0)
        val goal = node(5, 0, 0)
        val planner = LongDStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        assertEquals(0.0, planner.rhs(goal))
        assertEquals(Double.POSITIVE_INFINITY, planner.g(goal))
        assertEquals(1, planner.queue.size())
        assertEquals(goal, planner.queue.top())
        assertEquals(manhattan(start, goal), planner.queue.topFirst())
        assertEquals(0.0, planner.queue.topSecond())
    }

    @Test
    fun `computeShortestPath finds straight path on six connected grid`() {
        val start = node(0, 0, 0)
        val goal = node(5, 0, 0)
        val planner = LongDStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        val result = planner.computeShortestPath()
        val path = planner.path()

        assertFalse(result.timedOut)
        assertEquals(5.0, planner.g(start), 0.001)
        assertEquals(0.0, planner.g(goal), 0.001)
        assertEquals(
            Nodes.of(
                node(0, 0, 0),
                node(1, 0, 0),
                node(2, 0, 0),
                node(3, 0, 0),
                node(4, 0, 0),
                node(5, 0, 0),
            ),
            path,
        )
    }

    @Test
    fun `computeShortestPath cooperatively stops before expanding when cancelled`() {
        val start = node(0, 0, 0)
        val goal = node(100, 0, 0)
        val planner = LongDStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        val result = planner.computeShortestPath(cancelled = { true })

        assertTrue(result.cancelled)
        assertEquals(0, result.processedNodes)
        assertFalse(result.converged)
    }

    @Test
    fun `computeShortestPath finds diagonal path on twenty six connected grid`() {
        val start = node(0, 0, 0)
        val goal = node(2, 2, 2)
        val planner = LongDStarLite(graph(connectivity = N26), start, goal, ::euclidean)

        planner.computeShortestPath()

        assertEquals(2.0 * sqrt(3.0), planner.g(start), 0.001)
        assertEquals(
            Nodes.of(
                node(0, 0, 0),
                node(1, 1, 1),
                node(2, 2, 2),
            ),
            planner.path(),
        )
    }

    @Test
    fun `updateStart advances km and reuses the previous search`() {
        val start1 = node(0, 0, 0)
        val start2 = node(1, 0, 0)
        val goal = node(5, 0, 0)
        val planner = LongDStarLite(graph(connectivity = N6), start1, goal, ::manhattan)

        planner.computeShortestPath()
        planner.updateStart(start2)
        planner.computeShortestPath()

        assertEquals(1.0, planner.km, 0.001)
        assertEquals(4.0, planner.g(start2), 0.001)
        assertEquals(start2, planner.path().getLong(0))
        assertEquals(goal, planner.path().let { it.getLong(it.size - 1) })
    }

    @Test
    fun `start equals goal produces a single node path`() {
        val start = node(3, 3, 3)
        val planner = LongDStarLite(graph(connectivity = N26), start, start, ::euclidean)

        planner.computeShortestPath()

        assertEquals(0.0, planner.g(start), 0.001)
        assertEquals(Nodes.of(start), planner.path())
    }

    @Test
    fun `synchronizeAffected repairs path after a known node becomes blocked`() {
        val start = node(0, 0, 0)
        val goal = node(5, 0, 0)
        val blocked = mutableSetOf<Long>()
        val planner = LongDStarLite(graph(blocked, N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val initialPath = planner.path()

        val blockedNode = node(2, 0, 0)
        blocked += blockedNode
        val sync = planner.synchronizeAffected(affected(blockedNode, N6))
        planner.computeShortestPath()
        val repairedPath = planner.path()

        assertTrue(sync.nodesChecked > 0)
        assertTrue(blockedNode !in repairedPath)
        assertEquals(start, repairedPath.getLong(0))
        assertEquals(goal, repairedPath.getLong(repairedPath.size - 1))
        assertTrue(repairedPath.length() > initialPath.length())
    }

    @Test
    fun `synchronizeAffected repairs path after a blocked node becomes passable`() {
        val start = node(0, 0, 0)
        val goal = node(5, 0, 0)
        val blockedNode = node(2, 0, 0)
        val blocked = mutableSetOf(blockedNode)
        val planner = LongDStarLite(graph(blocked, N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val detour = planner.path()

        blocked -= blockedNode
        planner.synchronizeAffected(affected(blockedNode, N6))
        planner.computeShortestPath()
        val repairedPath = planner.path()

        assertTrue(blockedNode in repairedPath)
        assertTrue(repairedPath.length() < detour.length())
    }

    @Test
    fun `synchronizeAffected regenerates a known node whose adjacency was empty`() {
        val start = node(0, 0, 0)
        val goal = node(1, 0, 0)
        var opened = false
        val graph = LongLazyGraph(
            successorProvider = { node, sink ->
                if (opened && node == start) sink.add(goal, 1.0)
            },
            predecessorProvider = { node, sink ->
                if (opened && node == goal) sink.add(start, 1.0)
            },
        )
        val planner = LongDStarLite(graph, start, goal, ::manhattan)

        planner.computeShortestPath()
        assertTrue(planner.path().isEmpty)

        // The search has examined this adjacency and learned that it is a
        // dead end. It remains a known node even though it stores no edges.
        assertTrue(graph.successors(start).isEmpty())
        assertTrue(start in graph)

        opened = true
        val sync = planner.synchronizeAffected(Nodes.of(start))
        planner.computeShortestPath()

        assertEquals(1, sync.nodesChecked)
        assertEquals(1, sync.edgesAdded)
        assertEquals(Nodes.of(start, goal), planner.path())
    }

    @Test
    fun `lazy graph maintains known nodes without rebuilding its node set`() {
        val graph = LongLazyGraph({ node, sink -> if (node == 1L) sink.add(2L, 1.0) })

        assertTrue(2L in graph.successors(1L))
        assertEquals(setOf(1L, 2L), graph.nodes.toSet())
        assertEquals(2, graph.size)
        assertFailsWith<UnsupportedOperationException> { graph.nodes.add(3L) }

        graph.setCost(1L, 2L, Double.POSITIVE_INFINITY)
        // Initialized node 1 remains planner knowledge; endpoint 2 was only
        // known through the removed edge and is pruned.
        assertEquals(setOf(1L), graph.nodes.toSet())
        graph.clear()
        assertTrue(graph.nodes.isEmpty())
    }

    @Test
    fun `updateEdge from an uninitialized node reactivates the search even when the provider already knows the edge`() {
        // The maneuver-improvement pass declares a discovered edge whose
        // takeoff node the search never expanded, AND whose provider (the
        // discovery object) already returns it from its own maps. A naive
        // old-cost read would lazily initialize the takeoff, see the edge as
        // pre-existing, and skip the rhs update -- leaving the shortcut
        // permanently invisible.
        val start = node(0, 0, 0)
        val middle = node(1, 0, 0)
        val takeoff = node(0, 0, 1)
        val goal = node(5, 0, 0)
        var discovered = false
        val graph = LongLazyGraph(
            successorProvider = { node, sink ->
                if (node == start) {
                    sink.add(middle, 1.0)
                    if (discovered) sink.add(takeoff, 1.0)
                }
                if (node == middle) sink.add(goal, 10.0)
                if (discovered && node == takeoff) sink.add(goal, 1.0)
            },
            predecessorProvider = { node, sink ->
                if (node == middle) sink.add(start, 1.0)
                if (node == goal) {
                    sink.add(middle, 10.0)
                    if (discovered) sink.add(takeoff, 1.0)
                }
                if (discovered && node == takeoff) sink.add(start, 1.0)
            },
        )
        val planner = LongDStarLite(graph, start, goal, ::manhattan)

        planner.computeShortestPath()
        assertEquals(Nodes.of(start, middle, goal), planner.path())

        // The discovery provider learns the edge, then the improvement pass
        // declares it -- in that order, exactly as in production.
        discovered = true
        planner.updateEdge(takeoff, goal, 1.0)
        planner.computeShortestPath()

        assertEquals(Nodes.of(start, takeoff, goal), planner.path())
        assertEquals(2.0, planner.g(start), 0.001)
    }

    @Test
    fun `synchronizeAffected ignores unknown far away graph nodes`() {
        val start = node(0, 0, 0)
        val goal = node(5, 0, 0)
        val planner = LongDStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val path = planner.path()
        val beforeKm = planner.km
        val sync = planner.synchronizeAffected(affected(node(100, 0, 100), N6))
        planner.computeShortestPath()

        assertEquals(0, sync.nodesChecked)
        assertEquals(beforeKm, planner.km)
        assertEquals(path, planner.path())
    }

    @Test
    fun `edge list keeps insertion order under put merge and remove`() {
        val edges = com.lambda.pathing.graph.EdgeList()
        edges.add(5L, 1.0)
        edges.add(3L, 2.0)
        edges.add(9L, 3.0)
        edges.add(3L, 0.5)          // put: position kept, cost replaced
        edges.addMin(5L, 4.0)       // merge-min: larger cost ignored
        edges.addMin(9L, 0.25)      // merge-min: smaller cost taken
        edges.addMin(1L, 7.0)       // new target appended
        assertEquals(listOf(5L, 3L, 9L, 1L), (0 until edges.size).map(edges::node))
        assertEquals(listOf(1.0, 0.5, 0.25, 7.0), (0 until edges.size).map(edges::cost))

        assertTrue(edges.remove(3L))
        assertFalse(edges.remove(3L))
        assertEquals(listOf(5L, 9L, 1L), (0 until edges.size).map(edges::node))
        assertEquals(Double.POSITIVE_INFINITY, edges.costOf(3L))
        assertEquals(0.25, edges.costOf(9L))

        edges.add(2L, Double.POSITIVE_INFINITY)
        edges.dropNonFinite()
        assertEquals(listOf(5L, 9L, 1L), (0 until edges.size).map(edges::node))
        assertFailsWith<IllegalArgumentException> { edges.add(4L, -1.0) }
        assertFailsWith<IllegalArgumentException> { edges.addMin(4L, Double.NaN) }
    }
}
