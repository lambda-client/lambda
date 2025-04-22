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
import com.lambda.util.world.fastVectorOf
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
    fun createGridGraph18Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
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
    fun createGridGraph26Conn(blockedNodes: MutableSet<FastVector> = mutableSetOf()): LazyGraph {
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
}