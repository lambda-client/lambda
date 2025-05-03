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

package com.lambda.util

import com.lambda.pathing.dstar.LazyGraph
import com.lambda.util.world.FastVector
import com.lambda.util.world.dist
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.string
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import kotlin.math.abs
import kotlin.math.sqrt

object GraphUtil {
    // Simple Manhattan distance heuristic
    fun manhattanHeuristic(a: FastVector, b: FastVector): Double {
        return (abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)).toDouble()
    }

    // Euclidean distance heuristic (more accurate for diagonal movement)
    fun euclideanHeuristic(a: FastVector, b: FastVector): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // 6-connectivity (Axis-aligned moves only)
    fun createGridGraph6Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        return LazyGraph { node ->
            if (node in blockedNodes) emptyMap()
            else n6(node).filterKeys { it !in blockedNodes }
        }
    }

    // 18-connectivity (Axis-aligned + Face diagonal moves)
    fun createGridGraph18Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        return LazyGraph { node ->
            if (node in blockedNodes) emptyMap()
            else n18(node).filterKeys { it !in blockedNodes }
        }
    }

    // 26-connectivity (Axis-aligned + Face diagonal + Cube diagonal moves)
    fun createGridGraph26Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
        return LazyGraph { node ->
            if (node in blockedNodes) emptyMap()
            else n26(node).filterKeys { it !in blockedNodes }
        }
    }

    fun n6(o: FastVector) = neighborhood(o, minDistSq = 1, maxDistSq = 1)
    fun n18(o: FastVector) = neighborhood(o, minDistSq = 1, maxDistSq = 2)
    fun n26(o: FastVector) = neighborhood(o, minDistSq = 1, maxDistSq = 3)

    fun neighborhood(origin: FastVector, minDistSq: Int = 1, maxDistSq: Int = 1): Map<FastVector, Double> =
        (-1..1).flatMap { dx ->
            (-1..1).flatMap { dy ->
                (-1..1).mapNotNull { dz ->
                    val distSq = dx*dx + dy*dy + dz*dz
                    if (distSq in minDistSq..maxDistSq) {
                        val neighbor = fastVectorOf(origin.x + dx, origin.y + dy, origin.z + dz)
                        val cost = when (distSq) {
                            1 -> 1.0
                            2 -> COST_SQRT_2
                            3 -> COST_SQRT_3
                            else -> error("Unexpected squared distance: $distSq")
                        }
                        neighbor to cost
                    } else null
                }
            }
        }.toMap()

    fun List<FastVector>.string() = joinToString(" -> ") { it.string }
    fun List<FastVector>.length() = zipWithNext { a, b -> a dist b }.sum()

    private const val COST_SQRT_2 = 1.4142135623730951
    private const val COST_SQRT_3 = 1.7320508075688772
}