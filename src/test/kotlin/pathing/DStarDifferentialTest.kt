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
import com.lambda.pathing.graph.LongGraph
import it.unimi.dsi.fastutil.longs.LongArrayList
import pathing.GridGraphTestUtil.Connectivity
import pathing.GridGraphTestUtil.affectedNodes
import pathing.GridGraphTestUtil.euclidean
import pathing.GridGraphTestUtil.forEachNeighbor
import pathing.GridGraphTestUtil.manhattan
import pathing.GridGraphTestUtil.neighborhood
import pathing.GridGraphTestUtil.node
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The packed engine against Dijkstra, over random grid graphs with blocked cells, start
 * moves, edge cost updates and partial expansions in between. The engine keeps no
 * adjacency: it regenerates edges from the provider and is told which nodes changed, so
 * once drained every label must be the exact distance to the goal and the route must
 * cost exactly that.
 */
class DStarDifferentialTest {
	private class Grid(random: Random) {
		val connectivity = Connectivity.entries[random.nextInt(Connectivity.entries.size)]
		private val blockedCells = HashSet<Long>()
		val overrides = HashMap<Pair<Long, Long>, Double>()
		val goal: Long

		var start: Long
			private set

		val heuristic: (Long, Long) -> Double = when (random.nextInt(3)) {
			0 -> { _, _ -> 0.0 }
			1 -> if (connectivity == Connectivity.N6) ::manhattan else ::euclidean
			else -> ::euclidean
		}

		init {
			repeat(random.nextInt(0, 30)) { blockedCells += randomNode(random) }
			start = randomNode(random)
			goal = randomNode(random)
			blockedCells -= start
			blockedCells -= goal
		}

		val engine = LongDStarLite(
			LongGraph(
				successorProvider = { node, sink -> successors(node).forEach { (to, cost) -> sink.add(to, cost) } },
				predecessorProvider = { node, sink -> predecessors(node).forEach { (from, cost) -> sink.add(from, cost) } },
			),
			start, goal, { a, b -> heuristic(a, b) },
		)

		fun blocked(node: Long): Boolean = !inBox(node) || node in blockedCells

		fun toggle(cell: Long) {
			if (!blockedCells.remove(cell)) blockedCells += cell
		}

		fun moveStart(to: Long) {
			blockedCells -= to
			start = to
			engine.updateStart(to)
		}

		fun successors(node: Long): Map<Long, Double> = edges(node) { to -> node to to }

		fun predecessors(node: Long): Map<Long, Double> = edges(node) { from -> from to node }

		private inline fun edges(node: Long, edge: (Long) -> Pair<Long, Long>): Map<Long, Double> {
			val out = LinkedHashMap<Long, Double>()
			if (blocked(node)) return out
			forEachNeighbor(node, connectivity) { other, cost ->
				if (!blocked(other)) (overrides[edge(other)] ?: cost).takeIf { it.isFinite() }?.let { out[other] = it }
			}
			return out
		}

		fun distances(): Map<Long, Double> {
			val distance = hashMapOf(goal to 0.0)
			val queue = PriorityQueue<Pair<Double, Long>>(compareBy { it.first })
			queue += 0.0 to goal
			while (queue.isNotEmpty()) {
				val (reached, node) = queue.poll()
				if (reached > distance.getValue(node)) continue
				for ((from, cost) in predecessors(node)) {
					val through = reached + cost
					if (through < (distance[from] ?: Double.POSITIVE_INFINITY)) {
						distance[from] = through
						queue += through to from
					}
				}
			}
			return distance
		}

		fun assertExact(context: String) {
			engine.computeShortestPath(Duration.INFINITE)
			engine.expandField(1e9, Duration.INFINITE)
			assertTrue(engine.queue.isEmpty(), "not drained $context")
			val distances = distances()
			for (node in engine.knownNodes() + distances.keys) {
				assertSameCost(distances[node] ?: Double.POSITIVE_INFINITY, engine.g(node), "g(${PackedStance.describe(node)}) $context")
			}
			val expected = distances[start] ?: Double.POSITIVE_INFINITY
			if (!expected.isFinite()) return
			val route = assertNotNull(engine.routeCandidate(), "route $context")
			assertSameCost(expected, route.ticks, "route ticks $context")
			val nodes = route.nodes
			assertEquals(start, nodes.getLong(0), "route start $context")
			assertEquals(goal, nodes.getLong(nodes.size - 1), "route end $context")
			var cost = 0.0
			for (i in 1 until nodes.size) cost += assertNotNull(successors(nodes.getLong(i - 1))[nodes.getLong(i)], "route edge $i $context")
			assertSameCost(expected, cost, "route edges $context")
		}

		private fun inBox(node: Long): Boolean =
			PackedStance.unpackX(node) in -4..4 && PackedStance.unpackY(node) in -1..3 && PackedStance.unpackZ(node) in -4..4

		private fun assertSameCost(expected: Double, actual: Double, context: String) =
			assertTrue(expected == actual || abs(expected - actual) <= 1e-9, "expected $expected but was $actual: $context")
	}

	@Test
	fun `edge cost updates and start moves stay exact`() {
		repeat(60) { seed ->
			val random = Random(seed * 31 + 7)
			val grid = Grid(random)
			grid.assertExact("seed=$seed init")
			repeat(20) { step ->
				val context = "seed=$seed step=$step"
				if (random.nextInt(4) == 0) {
					grid.moveStart(randomNode(random))
				} else {
					val from = randomNode(random)
					val to = neighborhood(from, grid.connectivity).keys.random(random)
					if (!grid.blocked(from) && !grid.blocked(to)) {
						grid.overrides[from to to] = if (random.nextInt(4) == 0) Double.POSITIVE_INFINITY else random.nextInt(1, 9).toDouble()
						grid.engine.refresh(from)
					}
				}
				if (random.nextBoolean()) grid.engine.computeShortestPath(Duration.INFINITE, maxExpansions = random.nextInt(1, 12))
				grid.assertExact(context)
			}
		}
	}

	@Test
	fun `blocked cells and their neighbourhood stay exact`() {
		repeat(60) { seed ->
			val random = Random(seed * 131 + 3)
			val grid = Grid(random)
			grid.assertExact("seed=$seed init")
			repeat(12) { step ->
				val cell = randomNode(random)
				if (cell == grid.goal || cell == grid.start) return@repeat
				grid.toggle(cell)
				grid.engine.synchronizeAffected(LongArrayList(affectedNodes(cell, grid.connectivity)))
				if (random.nextInt(3) == 0) grid.moveStart(randomNode(random))
				grid.assertExact("seed=$seed step=$step")
			}
		}
	}

	private companion object {
		fun randomNode(random: Random): Long = node(random.nextInt(-3, 4), random.nextInt(0, 3), random.nextInt(-3, 4))
	}
}
