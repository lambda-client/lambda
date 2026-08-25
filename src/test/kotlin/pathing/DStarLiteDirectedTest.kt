/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.graph.CoarseRouteCandidate
import com.lambda.pathing.graph.DStarLite
import com.lambda.pathing.graph.LazyGraph
import com.lambda.pathing.graph.TailCost
import java.util.PriorityQueue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class DStarLiteDirectedTest {
    @Test
    fun `goal is exact even before its queue entry is expanded`() {
        val planner = planner(mutableDirectedGraph(0 to emptyMap()), start = 0, goal = 0)

        assertExactCost(0.0, planner.tailCost())
        assertTrue(assertNotNull(planner.routeCandidate()).exactFromStart)
    }

    @Test
    fun `directed asymmetric graph uses the true inverse predecessor relation`() {
        val edges = mutableDirectedGraph(
            0 to mapOf(1 to 1.0, 2 to 10.0),
            1 to mapOf(2 to 1.0),
            2 to emptyMap(),
        )
        val planner = planner(edges, start = 0, goal = 2)

        val result = planner.computeShortestPath(timeBudget = Duration.INFINITE)

        assertTrue(result.converged)
        assertFalse(result.timedOut)
        assertFalse(result.expansionLimitReached)
        assertEquals(listOf(0, 1, 2), planner.path())
        assertExactCost(2.0, planner.tailCost())
    }

    @Test
    fun `expansion limit returns bounds rather than certifying a raw g value`() {
        val edges = mutableDirectedGraph(
            0 to mapOf(1 to 1.0),
            1 to mapOf(2 to 1.0),
            2 to mapOf(3 to 1.0),
            3 to emptyMap(),
        )
        val planner = planner(edges, start = 0, goal = 3)

        val partial = planner.computeShortestPath(timeBudget = Duration.INFINITE, maxExpansions = 1)

        assertFalse(partial.converged)
        assertFalse(partial.timedOut)
        assertTrue(partial.expansionLimitReached)
        assertIs<TailCost.Bounds>(planner.tailCost())

        val repaired = planner.computeShortestPath(timeBudget = Duration.INFINITE)
        assertTrue(repaired.converged)
        assertExactCost(3.0, planner.tailCost())

        // D* Lite's normal termination certifies only its active start. A
        // finite internal g-value for another node is not a public proof.
        assertTrue(planner.g(1).isFinite())
        assertIs<TailCost.Bounds>(planner.tailCost(1))
    }

    @Test
    fun `cost increase invalidates exactness until incremental repair finishes`() {
        val edges = mutableDirectedGraph(
            0 to mapOf(1 to 1.0, 2 to 4.0),
            1 to mapOf(2 to 1.0),
            2 to emptyMap(),
        )
        val graph = lazyGraph(edges)
        val planner = DStarLite(graph, 0, 2, heuristic = { _, _ -> 0.0 })
        planner.computeShortestPath(timeBudget = Duration.INFINITE)
        assertExactCost(2.0, planner.tailCost())

        // Initialize before mutating the provider so updateEdge can compare
        // the old cached edge with the declared replacement.
        graph.successors(1)
        edges.getValue(1)[2] = 8.0
        planner.updateEdge(1, 2, 8.0)

        assertIs<TailCost.Bounds>(planner.tailCost())
        val result = planner.computeShortestPath(timeBudget = Duration.INFINITE)
        assertTrue(result.converged)
        assertExactCost(4.0, planner.tailCost())
        assertEquals(listOf(0, 2), planner.path())
    }

    @Test
    fun `unreachable is reported only after convergence`() {
        val edges = mutableDirectedGraph(
            0 to mapOf(1 to 1.0),
            1 to emptyMap(),
            2 to emptyMap(),
        )
        val planner = planner(edges, start = 0, goal = 2)

        assertIs<TailCost.Bounds>(planner.tailCost())
        val result = planner.computeShortestPath(timeBudget = Duration.INFINITE)

        assertTrue(result.converged)
        assertIs<TailCost.Unreachable>(planner.tailCost())
        assertNull(planner.routeCandidate())
    }

    @Test
    fun `random directed graphs match a Dijkstra oracle`() {
        repeat(80) { seed ->
            val random = Random(seed)
            val nodeCount = 12
            val edges = randomGraph(random, nodeCount)
            val start = random.nextInt(nodeCount)
            val goal = random.nextInt(nodeCount)
            val planner = planner(edges, start, goal)

            val result = planner.computeShortestPath(timeBudget = Duration.INFINITE)
            val expected = dijkstra(edges, start, goal)

            assertTrue(result.converged, "seed=$seed")
            assertPlannerMatches(expected, planner.tailCost(), "seed=$seed")
            assertRouteMatches(expected, planner.routeCandidate(), start, goal, edges, "seed=$seed")
        }
    }

    @Test
    fun `incremental edge repairs keep matching Dijkstra after mixed mutations`() {
        repeat(30) { seed ->
            val random = Random(seed * 7919 + 17)
            val nodeCount = 14
            val edges = randomGraph(random, nodeCount)
            val graph = lazyGraph(edges)
            var start = random.nextInt(nodeCount)
            val goal = random.nextInt(nodeCount)
            val planner = DStarLite(graph, start, goal, heuristic = { _, _ -> 0.0 })

            planner.computeShortestPath(timeBudget = Duration.INFINITE)
            assertPlannerMatches(dijkstra(edges, start, goal), planner.tailCost(), "seed=$seed initial")

            repeat(35) { mutation ->
                val from = random.nextInt(nodeCount)
                var to = random.nextInt(nodeCount - 1)
                if (to >= from) to++

                // Capture the old graph value before changing the provider.
                graph.successors(from)
                val newCost = when (random.nextInt(3)) {
                    0 -> Double.POSITIVE_INFINITY
                    else -> random.nextInt(1, 25).toDouble()
                }
                if (newCost.isFinite()) edges.getValue(from)[to] = newCost
                else edges.getValue(from).remove(to)
                planner.updateEdge(from, to, newCost)

                if (mutation % 9 == 8) {
                    start = random.nextInt(nodeCount)
                    planner.updateStart(start)
                }

                val result = planner.computeShortestPath(timeBudget = Duration.INFINITE)
                val expected = dijkstra(edges, start, goal)
                val context = "seed=$seed mutation=$mutation edge=$from->$to cost=$newCost start=$start goal=$goal"
                assertTrue(result.converged, context)
                assertPlannerMatches(expected, planner.tailCost(), context)
                assertRouteMatches(expected, planner.routeCandidate(), start, goal, edges, context)
            }
        }
    }

    @Test
    fun `invalid costs and heuristics fail at the boundary`() {
        assertFailsWith<IllegalArgumentException> {
            LazyGraph<Int>({ mapOf(1 to -1.0) }).successors(0)
        }
        assertFailsWith<IllegalArgumentException> {
            LazyGraph<Int>({ mapOf(1 to Double.NaN) }).successors(0)
        }
        assertFailsWith<IllegalArgumentException> {
            DStarLite(LazyGraph({ _: Int -> emptyMap() }), 0, 1, heuristic = { _, _ -> Double.NaN })
        }

        val graph = LazyGraph<Int>({ mapOf(1 to Double.POSITIVE_INFINITY) })
        assertTrue(graph.successors(0).isEmpty())
    }

    @Test
    fun `lazy provider failure leaves node retryable and unpublished`() {
        var attempts = 0
        val graph = LazyGraph<Int>({ node ->
            if (node == 0 && attempts++ == 0) error("section not ready")
            if (node == 0) mapOf(1 to 2.0) else emptyMap()
        })

        assertFailsWith<IllegalStateException> { graph.successors(0) }
        assertFalse(0 in graph)
        assertEquals(mapOf(1 to 2.0), graph.successors(0))
        assertTrue(0 in graph)
    }

    @Test
    fun `moving start materializes a lazy vertex after the old queue emptied`() {
        val graph = LazyGraph<Int>(
            successorProvider = { node -> if (node == 3) mapOf(2 to 1.0) else emptyMap() },
            // Node 3 models a stance reached by continuous execution which was not
            // part of the predecessor field when the old start was solved.
            predecessorProvider = { emptyMap() },
        )
        val planner = DStarLite(graph, start = 2, goal = 2, heuristic = { _, _ -> 0.0 })
        assertTrue(planner.computeShortestPath(Duration.INFINITE).converged)
        assertTrue(planner.queue.isEmpty())

        planner.updateStart(3)
        val repaired = planner.computeShortestPath(Duration.INFINITE)

        assertTrue(repaired.converged)
        assertEquals(listOf(3, 2), planner.path())
        assertExactCost(1.0, planner.tailCost())
    }

    private fun planner(edges: DirectedEdges, start: Int, goal: Int) =
        DStarLite(lazyGraph(edges), start, goal, heuristic = { _, _ -> 0.0 }, nodeTieBreaker = naturalOrder())

    private fun lazyGraph(edges: DirectedEdges) = LazyGraph<Int>(
        successorProvider = { node -> edges[node]?.toMap().orEmpty() },
        predecessorProvider = { node ->
            buildMap {
                for ((from, outgoing) in edges) outgoing[node]?.let { put(from, it) }
            }
        },
    )

    private fun randomGraph(random: Random, nodeCount: Int): DirectedEdges {
        val edges = (0 until nodeCount).associateWith { mutableMapOf<Int, Double>() }.toMutableMap()
        for (from in 0 until nodeCount) {
            for (to in 0 until nodeCount) {
                if (from != to && random.nextDouble() < 0.19) {
                    edges.getValue(from)[to] = random.nextInt(1, 25).toDouble()
                }
            }
        }
        return edges
    }

    private fun dijkstra(edges: DirectedEdges, start: Int, goal: Int): Double {
        val distances = HashMap<Int, Double>()
        val queue = PriorityQueue(compareBy<Pair<Int, Double>> { it.second })
        distances[start] = 0.0
        queue += start to 0.0

        while (queue.isNotEmpty()) {
            val (node, distance) = queue.remove()
            if (distance != distances[node]) continue
            if (node == goal) return distance
            for ((next, cost) in edges[node].orEmpty()) {
                val candidate = distance + cost
                if (candidate < (distances[next] ?: Double.POSITIVE_INFINITY)) {
                    distances[next] = candidate
                    queue += next to candidate
                }
            }
        }
        return Double.POSITIVE_INFINITY
    }

    private fun assertPlannerMatches(expected: Double, actual: TailCost, context: String) {
        if (expected.isFinite()) {
            val exact = assertIs<TailCost.Exact>(actual, context)
            assertEquals(expected, exact.ticks, 1e-9, context)
        } else {
            assertIs<TailCost.Unreachable>(actual, context)
        }
    }

    private fun assertRouteMatches(
        expected: Double,
        route: CoarseRouteCandidate<Int>?,
        start: Int,
        goal: Int,
        edges: DirectedEdges,
        context: String,
    ) {
        if (!expected.isFinite()) {
            assertNull(route, context)
            return
        }
        requireNotNull(route)
        assertTrue(route.exactFromStart, context)
        assertEquals(start, route.nodes.first(), context)
        assertEquals(goal, route.nodes.last(), context)
        val actual = route.nodes.zipWithNext { from, to -> edges.getValue(from).getValue(to) }.sum()
        assertEquals(expected, actual, 1e-9, context)
        assertEquals(actual, route.ticks, 1e-9, context)
    }

    private fun assertExactCost(expected: Double, cost: TailCost) {
        val exact = assertIs<TailCost.Exact>(cost)
        assertEquals(expected, exact.ticks, 1e-9)
    }

    private fun mutableDirectedGraph(vararg entries: Pair<Int, Map<Int, Double>>): DirectedEdges =
        entries.associateTo(mutableMapOf()) { (node, outgoing) -> node to outgoing.toMutableMap() }

    private typealias DirectedEdges = MutableMap<Int, MutableMap<Int, Double>>
}
