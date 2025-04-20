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
import com.lambda.pathing.dstar.Key
import com.lambda.pathing.dstar.LazyGraph
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import org.junit.jupiter.api.BeforeEach
import kotlin.math.abs
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

    private val blockedNodes = mutableSetOf<FastVector>()

    // Simple Manhattan distance heuristic
    private fun manhattanHeuristic(a: FastVector, b: FastVector): Double {
        return (abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)).toDouble()
    }

    // Euclidean distance heuristic (more accurate for diagonal movement)
    private fun euclideanHeuristic(a: FastVector, b: FastVector): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // 6-connectivity (Axis-aligned moves only)
    private fun createGridGraph6Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        val cost = 1.0
        return LazyGraph { node ->
            val neighbors = mutableMapOf<FastVector, Double>()
            val x = node.x
            val y = node.y
            val z = node.z
            // Add neighbors differing by 1 in exactly one dimension
            neighbors[fastVectorOf(x + 1, y, z)] = cost
            neighbors[fastVectorOf(x - 1, y, z)] = cost
            neighbors[fastVectorOf(x, y + 1, z)] = cost
            neighbors[fastVectorOf(x, y - 1, z)] = cost
            neighbors[fastVectorOf(x, y, z + 1)] = cost
            neighbors[fastVectorOf(x, y, z - 1)] = cost
            neighbors.minus(blockedNodes)
        }
    }

    // 18-connectivity (Axis-aligned + Face diagonal moves)
    private fun createGridGraph18Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        val cost1 = 1.0 // Axis-aligned
        val cost2 = sqrt(2.0) // Face diagonal
        return LazyGraph { node ->
            val neighbors = mutableMapOf<FastVector, Double>()
            val x = node.x
            val y = node.y
            val z = node.z
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue // Skip self
                        val distSq = dx*dx + dy*dy + dz*dz
                        if (distSq > 2) continue // Exclude cube diagonals (distSq = 3)

                        val cost = if (distSq == 1) cost1 else cost2
                        neighbors[fastVectorOf(x + dx, y + dy, z + dz)] = cost
                    }
                }
            }
            neighbors.minus(blockedNodes)
        }
    }

    // 26-connectivity (Axis-aligned + Face diagonal + Cube diagonal moves)
    private fun createGridGraph26Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        val cost1 = 1.0 // Axis-aligned
        val cost2 = sqrt(2.0) // Face diagonal
        val cost3 = sqrt(3.0) // Cube diagonal
        return LazyGraph { node ->
            val neighbors = mutableMapOf<FastVector, Double>()
            val x = node.x
            val y = node.y
            val z = node.z
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue // Skip self

                        val cost = when (dx*dx + dy*dy + dz*dz) {
                            1 -> cost1
                            2 -> cost2
                            3 -> cost3
                            else -> continue // Should not happen with dx/dy/dz in -1..1
                        }
                        neighbors[fastVectorOf(x + dx, y + dy, z + dz)] = cost
                    }
                }
            }
            neighbors.minus(blockedNodes)
        }
    }

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
}
