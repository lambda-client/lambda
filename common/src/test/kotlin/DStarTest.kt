import com.lambda.pathing.dstar.DStarLite
import com.lambda.pathing.dstar.Graph
import kotlin.math.abs
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

class DStarTest {
    /**
     * Simple consistent heuristic for a grid: the Manhattan distance
     * (or Euclidean if you prefer). We'll do Manhattan here.
     */
    private fun manhattan(a: Pair<Int, Int>, b: Pair<Int, Int>): Double {
        return (abs(a.first - b.first) + abs(a.second - b.second)).toDouble()
    }

    /**
     * Convert (row,col) to a single vertex index if we want.
     */
    private fun idx(row: Int, col: Int, cols: Int): Int {
        return row * cols + col
    }

    /**
     * Build a 2x2 grid with all edges cost=1 (4-connected).
     *   0 = top-left, 1 = top-right
     *   2 = bottom-left, 3 = bottom-right
     */
    @Test
    fun test2x2Grid() {
        // We'll build adjacency for each of the 4 cells
        val adjMap = mutableMapOf<Int, List<Pair<Int, Double>>>()

        // For a 2x2, we have cells 0..3
        // We'll do 4-connected edges
        // 0 neighbors: 1,2
        adjMap[0] = listOf(Pair(1, 1.0), Pair(2, 1.0))
        // 1 neighbors: 0,3
        adjMap[1] = listOf(Pair(0, 1.0), Pair(3, 1.0))
        // 2 neighbors: 0,3
        adjMap[2] = listOf(Pair(0, 1.0), Pair(3, 1.0))
        // 3 neighbors: 1,2
        adjMap[3] = listOf(Pair(1, 1.0), Pair(2, 1.0))

        val graph = Graph(adjMap)

        // We'll define a heuristic that interprets each index as (row, col):
        // 0->(0,0), 1->(0,1), 2->(1,0), 3->(1,1)
        fun h(u: Int, v: Int): Double {
            val coords = mapOf(0 to Pair(0,0), 1 to Pair(0,1), 2 to Pair(1,0), 3 to Pair(1,1))
            return manhattan(coords[u]!!, coords[v]!!)
        }

        val start = 0
        val goal = 3
        val dstar = DStarLite(graph, ::h, start, goal)

        dstar.computeShortestPath()
        val path = dstar.getPath()
        // Possible path is 0 -> 1 -> 3 or 0 -> 2 -> 3
        // We'll just check that the path length is 3 and ends at 3
        assertEquals(3, path.size, "Path should have 3 vertices (0->1->3 or 0->2->3)")
        assertEquals(3, path.last(), "Goal should be vertex 3")
    }

    /**
     * Build a 3x3 grid, block the middle cell, test path correctness.
     */
    @Test
    fun test3x3GridWithBlock() {
        // We'll index cells row-major: row*3 + col
        // So top-left=0, top-middle=1, top-right=2, middle-left=3, ...
        val adjMap = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        val rows = 3
        val cols = 3

        // Helper to add edges
        fun addEdge(u: Int, v: Int, cost: Double) {
            adjMap.getOrPut(u) { mutableListOf() }.add(Pair(v, cost))
        }

        // Build a 4-connected grid
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val u = idx(r, c, cols)
                // For each neighbor (r+dr, c+dc) if in range
                val deltas = listOf(Pair(0,1), Pair(1,0), Pair(0,-1), Pair(-1,0))
                for ((dr, dc) in deltas) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        val v = idx(nr, nc, cols)
                        // We'll assign cost=1.0 by default
                        addEdge(u, v, 1.0)
                    }
                }
            }
        }

        // Now let's "block" the middle cell (1,1) = index 4
        // We'll set the cost of edges to or from 4 to ∞ by removing them from adjacency
        adjMap[4] = mutableListOf()  // no successors
        // Also remove any edges that go into 4
        for ((u, edges) in adjMap) {
            adjMap[u] = edges.filter { (v, _) -> v != 4 }.toMutableList()
        }

        // Create the graph
        val graph = Graph(adjMap.mapValues { it.value.toList() })

        // Heuristic: manhattan distance on (r,c)
        fun h(u: Int, v: Int): Double {
            val r1 = u / cols
            val c1 = u % cols
            val r2 = v / cols
            val c2 = v % cols
            return (abs(r1 - r2) + abs(c1 - c2)).toDouble()
        }

        val start = 0  // top-left
        val goal = 8   // bottom-right
        val dstar = DStarLite(graph, ::h, start, goal)

        dstar.computeShortestPath()
        val path = dstar.getPath()

        // Because cell 4 is blocked, the path must go around it:
        // One possible route is 0->1->2->5->8 or 0->3->6->7->8, etc.
        // Let's just check it ends at 8 and the path length is at least 5
        // (the minimal path is 4 steps => 5 vertices).
        assertFalse(path.isEmpty(), "Path should not be empty.")
        assertEquals(8, path.last(), "Goal should be vertex 8")
        assertTrue(path.size >= 5, "Path must circumvent the blocked cell (at least 5 vertices).")
    }

    /**
     * Test how the path updates if we move the start after computing a path.
     * We'll use the same 2x2 from the first test but demonstrate how updateStart works.
     */
    @Test
    fun testUpdateStart() {
        // 2x2 adjacency (same as first test)
        val adjMap = mapOf(
            0 to listOf(Pair(1, 1.0), Pair(2, 1.0)),
            1 to listOf(Pair(0, 1.0), Pair(3, 1.0)),
            2 to listOf(Pair(0, 1.0), Pair(3, 1.0)),
            3 to listOf(Pair(1, 1.0), Pair(2, 1.0))
        )
        val graph = Graph(adjMap)

        fun h(u: Int, v: Int): Double {
            val coords = mapOf(0 to Pair(0,0), 1 to Pair(0,1), 2 to Pair(1,0), 3 to Pair(1,1))
            val (r1, c1) = coords[u]!!
            val (r2, c2) = coords[v]!!
            return (abs(r1 - r2) + abs(c1 - c2)).toDouble()
        }

        val dstar = DStarLite(graph, ::h, start = 0, goal = 3)
        dstar.computeShortestPath()
        val path1 = dstar.getPath()
        assertEquals(3, path1.size)

        // Suppose the robot "moves" from 0 to 1. We update the start in D* Lite:
        dstar.updateStart(1)
        // We might discover changes in costs or not. For now, assume no changes => no updateVertex calls.
        dstar.computeShortestPath()
        val path2 = dstar.getPath()
        // Now the path should be from 1 to 3, presumably [1, 3] or [1, 0, 2, 3], etc.
        assertEquals(3, path2.last(), "Goal remains 3")
        // Usually the direct path is [1,3], so length=2
        assertTrue(path2.size <= 3, "Likely path is shorter now from 1->3 directly.")
    }
}