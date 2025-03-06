import com.lambda.pathing.dstar.DStarLite
import com.lambda.pathing.dstar.Graph
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
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

class DStarLiteTest {

    /**
     * Helper to create a 3D grid graph with 6-connected neighbors.
     *
     * Each vertex is created using fastVectorOf(x, y, z). Edges have cost 1.0.
     */
    private fun create3DGridGraph(width: Int, height: Int, depth: Int): Graph {
        val adjMap = mutableMapOf<FastVector, MutableList<Pair<FastVector, Double>>>()
        // 6-connected neighbor offsets.
        val offsets = listOf(
            Triple(1, 0, 0), Triple(-1, 0, 0),
            Triple(0, 1, 0), Triple(0, -1, 0),
            Triple(0, 0, 1), Triple(0, 0, -1)
        )
        (0 until width).forEach { x ->
            (0 until height).forEach { y ->
                (0 until depth).forEach { z ->
                    val current = fastVectorOf(x.toLong(), y.toLong(), z.toLong())
                    val neighbors = mutableListOf<Pair<FastVector, Double>>()
                    offsets.forEach { (dx, dy, dz) ->
                        val nx = x + dx
                        val ny = y + dy
                        val nz = z + dz
                        if (nx in 0 until width && ny in 0 until height && nz in 0 until depth) {
                            val neighbor = fastVectorOf(nx.toLong(), ny.toLong(), nz.toLong())
                            neighbors.add(Pair(neighbor, 1.0))
                        }
                    }
                    adjMap[current] = neighbors
                }
            }
        }
        return Graph(adjMap.mapValues { it.value.toList() })
    }

    /** Manhattan distance heuristic */
    private fun heuristic(u: FastVector, v: FastVector): Double {
        val dx = abs(u.x - v.x)
        val dy = abs(u.y - v.y)
        val dz = abs(u.z - v.z)
        return (dx + dy + dz).toDouble()
    }

    @Test
    fun test3x3x3Grid() {
        val width = 3
        val height = 3
        val depth = 3
        val graph = create3DGridGraph(width, height, depth)
        // Start at (0,0,0) and goal at (2,2,2).
        val start = fastVectorOf(0L, 0L, 0L)
        val goal = fastVectorOf(2L, 2L, 2L)
        val dstar = DStarLite(graph, ::heuristic, start, goal)
        dstar.computeShortestPath()
        val path = dstar.getPath()
        // Manhattan distance between (0,0,0) and (2,2,2) is 6; hence the path should have 7 vertices.
        assertFalse(path.isEmpty(), "Path should not be empty")
        assertEquals(goal, path.last(), "Path should end at the goal")
        assertEquals(7, path.size, "Expected path length is 7 vertices (6 steps)")
    }

    @Test
    fun testUpdateStart3D() {
        val width = 3
        val height = 3
        val depth = 3
        val graph = create3DGridGraph(width, height, depth)
        val start = fastVectorOf(0L, 0L, 0L)
        val goal = fastVectorOf(2L, 2L, 2L)
        val dstar = DStarLite(graph, ::heuristic, start, goal)
        dstar.computeShortestPath()
        val path1 = dstar.getPath()
        assertEquals(goal, path1.last(), "Initial path should reach goal")
        // Simulate a move: update the start to the second vertex in the current path.
        val newStart = path1[1]
        dstar.updateStart(newStart)
        dstar.computeShortestPath()
        val path2 = dstar.getPath()
        assertEquals(goal, path2.last(), "Updated path should reach goal")
        // The new path should be shorter (or equal) since we already moved.
        assertTrue(path2.size <= path1.size, "Updated path should be shorter or equal in length")
    }
}