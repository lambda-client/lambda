import com.lambda.pathing.dstar.DStarLite
import com.lambda.pathing.dstar.LazyGraph
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

    /** Helper to create a lazy-initialized 3D grid graph with 6-connected neighbors */
    private fun createLazy3DGridGraph() =
        LazyGraph { node ->
            val neighbors = mutableMapOf<FastVector, Double>()
            val (x, y, z) = listOf(node.x, node.y, node.z)

            // possible directions in a 3D grid: 6-connected neighbors
            val directions = listOf(
                fastVectorOf(x - 1, y, z), fastVectorOf(x + 1, y, z),
                fastVectorOf(x, y - 1, z), fastVectorOf(x, y + 1, z),
                fastVectorOf(x, y, z - 1), fastVectorOf(x, y, z + 1)
            )

            directions.forEach { v ->
                neighbors[v] = 1.0
            }

            neighbors
        }

    /** Manhattan distance heuristic for 3D grids */
    private fun heuristic(u: FastVector, v: FastVector): Double =
        (abs(u.x - v.x) + abs(u.y - v.y) + abs(u.z - v.z)).toDouble()

    @Test
    fun test3x3x3Grid() {
        val graph = createLazy3DGridGraph()
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(2, 2, 2)
        val dstar = DStarLite(graph, ::heuristic, start, goal)

        dstar.computeShortestPath()
        val path = dstar.getPath()
        // Manhattan distance between (0,0,0) and (2,2,2) is 6; hence the path should have 7 vertices.
        assertFalse(path.isEmpty(), "Path should not be empty")
        assertEquals(start, path.first(), "Path should start at the initial position")
        assertEquals(goal, path.last(), "Path should end at the goal position")
        assertEquals(7, path.size, "Expected path length is 7 vertices (6 steps)")
    }

    @Test
    fun testUpdateStart3D() {
        val graph = createLazy3DGridGraph()
        var start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(2, 2, 2)
        val dstar = DStarLite(graph, ::heuristic, start, goal)

        dstar.computeShortestPath()

        val initialPath = dstar.getPath()
        assertTrue(initialPath.size > 1, "Initial path should have multiple steps")

        // Move starting position closer to the goal and recompute
        start = fastVectorOf(1, 1, 1)
        dstar.updateStart(start)

        val updatedPath = dstar.getPath()
        assertFalse(updatedPath.isEmpty(), "Updated path should not be empty now from new start")
        assertEquals(start, updatedPath.first(), "Updated path must start at updated position")
        assertEquals(goal, updatedPath.last(), "Updated path must end at the goal")
    }
}
