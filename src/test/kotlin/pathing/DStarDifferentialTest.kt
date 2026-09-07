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

import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.TailCost
import it.unimi.dsi.fastutil.longs.LongArrayList
import pathing.GridGraphTestUtil.Connectivity
import pathing.GridGraphTestUtil.affectedNodes
import pathing.GridGraphTestUtil.euclidean
import pathing.GridGraphTestUtil.graph
import pathing.GridGraphTestUtil.manhattan
import pathing.GridGraphTestUtil.node
import pathing.GridGraphTestUtil.referenceGraph
import pathing.reference.DStarLite
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * The packed-long engine against the generic reference engine it replaced, over random
 * grid graphs with random blocked cells, start moves and edge cost updates.
 *
 * Two regimes:
 * - Step-identical: every call both engines receive is the same, so their heaps see the
 *   same insertions in the same order and the WHOLE state (g, rhs, queue top, route) must
 *   agree after every call, including partial expansions.
 * - Synchronisation: `synchronizeAffected` visits affected nodes in a hash order in the
 *   reference and in sorted order in the packed engine, so only the converged, fully
 *   drained state is comparable -- and there g, rhs and the route must agree everywhere.
 */
class DStarDifferentialTest {
    private class Pair(
        val blocked: MutableSet<Long>,
        val connectivity: Connectivity,
        start: Long,
        val goal: Long,
        private val heuristic: (Long, Long) -> Double,
    ) {
        val referenceGraph = referenceGraph(blocked, connectivity)
        val reference = DStarLite(referenceGraph, start, goal, heuristic, nodeTieBreaker = naturalOrder())
        val packedGraph = graph(blocked, connectivity)
        val packed = LongDStarLite(packedGraph, start, goal, { a, b -> heuristic(a, b) })

        fun assertFullStateEqual(context: String) {
            assertEquals(reference.km, packed.km, "km $context")
            assertEquals(reference.start, packed.start, "start $context")
            assertEquals(referenceGraph.nodes, packedGraph.nodes.toSet(), "graph nodes $context")
            assertEquals(reference.queue.size(), packed.queue.size(), "queue size $context")
            if (!reference.queue.isEmpty()) {
                assertEquals(reference.queue.top(), packed.queue.top(), "queue top $context")
                val key = reference.queue.topKey(pathing.reference.Key.INFINITY)
                assertEquals(key.first, packed.queue.topFirst(), "queue top k1 $context")
                assertEquals(key.second, packed.queue.topSecond(), "queue top k2 $context")
            }
            assertLabelsEqual(context, adjacency = true)
            assertEquals(reference.isStartCostExact(), packed.isStartCostExact(), "exactness $context")
            assertRouteEqual(context)
        }

        /**
         * [adjacency] compares the materialised edge lists too. Only the step-identical
         * regime can ask for that: which nodes `synchronizeAffected` skips as "not in the
         * graph" depends on which affected neighbour was visited before them, so the
         * materialised adjacency (not the labels) is visiting-order dependent.
         */
        fun assertLabelsEqual(context: String, adjacency: Boolean) {
            val union = HashSet<Long>(referenceGraph.nodes)
            union += packedGraph.nodes
            union += reference.start
            union += goal
            for (node in union) {
                assertEquals(reference.g(node), packed.g(node), "g($node) $context")
                assertEquals(reference.rhs(node), packed.rhs(node), "rhs($node) $context")
                assertEquals(node in reference.queue, node in packed.queue, "queued($node) $context")
                if (adjacency) assertEquals(
                    referenceGraph.knownSuccessors(node).toList(),
                    packedGraph.knownSuccessors(node).toMap().toList(),
                    "successors($node) $context",
                )
            }
        }

        fun assertRouteEqual(context: String) {
            val expected = reference.routeCandidate()
            val actual = packed.routeCandidate()
            assertEquals(expected?.nodes, actual?.nodes?.toList(), "route nodes $context")
            assertEquals(expected?.ticks, actual?.ticks, "route ticks $context")
            assertEquals(expected?.exactFromStart, actual?.exactFromStart, "route exactness $context")
            assertEquals(describe(reference.tailCost()), describe(packed.tailCost()), "tail cost $context")
            // The descent report is deliberately NOT compared: the reference walker had no
            // tie-break, the unified walker uses natural order -- the one intended change.
        }

        private fun describe(cost: TailCost): String = when (cost) {
            is TailCost.Exact -> "exact ${cost.ticks}"
            is TailCost.Bounds -> "bounds ${cost.lowerBound} ${cost.upperBound}"
            is TailCost.Unreachable -> "unreachable"
        }
    }

    /**
     * The grid is otherwise unbounded; draining the queue in the synchronisation regime
     * needs a finite graph, so everything outside the box reads as blocked.
     */
    private class BoundedBlocked(private val blocked: HashSet<Long> = HashSet()) : AbstractMutableSet<Long>() {
        private fun inBox(node: Long): Boolean {
            val x = PackedStance.unpackX(node)
            val y = PackedStance.unpackY(node)
            val z = PackedStance.unpackZ(node)
            return x in -4..4 && y in -1..3 && z in -4..4
        }

        override val size: Int get() = blocked.size
        override fun iterator(): MutableIterator<Long> = blocked.iterator()
        override fun add(element: Long): Boolean = blocked.add(element)
        override fun remove(element: Long): Boolean = blocked.remove(element)
        override fun contains(element: Long): Boolean = !inBox(element) || element in blocked
    }

    private fun randomPair(random: Random): Pair {
        val connectivity = Connectivity.entries[random.nextInt(Connectivity.entries.size)]
        val blocked = BoundedBlocked()
        repeat(random.nextInt(0, 30)) { blocked += randomNode(random) }
        val start = randomNode(random)
        val goal = randomNode(random)
        blocked -= start
        blocked -= goal
        val heuristic: (Long, Long) -> Double = when (random.nextInt(3)) {
            0 -> { _, _ -> 0.0 }
            1 -> ::manhattan.takeIf { connectivity == Connectivity.N6 } ?: ::euclidean
            else -> ::euclidean
        }
        return Pair(blocked, connectivity, start, goal, heuristic)
    }

    private fun randomNode(random: Random): Long =
        node(random.nextInt(-3, 4), random.nextInt(0, 3), random.nextInt(-3, 4))

    @Test
    fun `step identical regimes agree on the whole state after every call`() {
        repeat(60) { seed ->
            val random = Random(seed * 31 + 7)
            val pair = randomPair(random)
            pair.assertFullStateEqual("seed=$seed init")

            repeat(40) { step ->
                val context = "seed=$seed step=$step"
                when (random.nextInt(10)) {
                    in 0..3 -> {
                        val budget = if (random.nextBoolean()) random.nextInt(1, 12) else Int.MAX_VALUE
                        val a = pair.reference.computeShortestPath(Duration.INFINITE, maxExpansions = budget)
                        val b = pair.packed.computeShortestPath(Duration.INFINITE, maxExpansions = budget)
                        assertEquals(a.processedNodes, b.processedNodes, "processed $context")
                        assertEquals(a.converged, b.converged, "converged $context")
                        assertEquals(a.expansionLimitReached, b.expansionLimitReached, "limit $context")
                    }
                    4, 5 -> {
                        val newStart = randomNode(random)
                        pair.blocked -= newStart
                        pair.reference.updateStart(newStart)
                        pair.packed.updateStart(newStart)
                    }
                    6, 7 -> {
                        // A random edge between grid neighbours, priced or removed.
                        val from = randomNode(random)
                        val to = GridGraphTestUtil.neighborhood(from, pair.connectivity).keys.random(random)
                        val cost = if (random.nextInt(4) == 0) Double.POSITIVE_INFINITY
                        else random.nextInt(1, 9).toDouble()
                        pair.reference.updateEdge(from, to, cost)
                        pair.packed.updateEdge(from, to, cost)
                    }
                    8 -> {
                        val extra = random.nextInt(0, 6).toDouble()
                        val budget = random.nextInt(1, 30)
                        val a = pair.reference.expandField(extra, Duration.INFINITE, maxExpansions = budget)
                        val b = pair.packed.expandField(extra, Duration.INFINITE, maxExpansions = budget)
                        assertEquals(a.processedNodes, b.processedNodes, "field processed $context")
                    }
                    else -> {
                        val budget = random.nextInt(0, 40)
                        val a = pair.reference.computeRouteCandidate(maxExpansions = budget)
                        val b = pair.packed.computeRouteCandidate(maxExpansions = budget)
                        assertEquals(a?.nodes, b?.nodes?.toList(), "route candidate nodes $context")
                        assertEquals(a?.ticks, b?.ticks, "route candidate ticks $context")
                        assertEquals(a?.exactFromStart, b?.exactFromStart, "route candidate exactness $context")
                    }
                }
                pair.assertFullStateEqual(context)
            }
        }
    }

    @Test
    fun `synchronisation regimes agree once drained`() {
        repeat(60) { seed ->
            val random = Random(seed * 131 + 3)
            val pair = randomPair(random)
            pair.reference.computeShortestPath(Duration.INFINITE)
            pair.packed.computeShortestPath(Duration.INFINITE)

            repeat(12) { step ->
                val context = "seed=$seed step=$step"
                val cell = randomNode(random)
                if (cell == pair.goal || cell == pair.reference.start) return@repeat
                if (!pair.blocked.remove(cell)) pair.blocked += cell
                val affected = affectedNodes(cell, pair.connectivity)
                // The diff counts are visiting-order dependent for the same reason the
                // adjacency is (see assertLabelsEqual); only "did anything change" is stable.
                val a = pair.reference.synchronizeAffected(affected)
                val b = pair.packed.synchronizeAffected(LongArrayList(affected))
                assertEquals(
                    a.edgesAdded + a.edgesRemoved + a.edgesChanged > 0,
                    b.edgesAdded + b.edgesRemoved + b.edgesChanged > 0,
                    "synchronisation changed anything $context",
                )

                if (random.nextInt(3) == 0) {
                    val newStart = randomNode(random)
                    pair.blocked -= newStart
                    pair.reference.updateStart(newStart)
                    pair.packed.updateStart(newStart)
                }

                pair.reference.computeShortestPath(Duration.INFINITE)
                pair.packed.computeShortestPath(Duration.INFINITE)
                pair.reference.expandField(1e9, Duration.INFINITE)
                pair.packed.expandField(1e9, Duration.INFINITE)
                check(pair.reference.queue.isEmpty() && pair.packed.queue.isEmpty()) { "not drained $context" }

                pair.assertLabelsEqual(context, adjacency = false)
                pair.assertRouteEqual(context)
            }
        }
    }
}
