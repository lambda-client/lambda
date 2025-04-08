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

package com.lambda.pathing.dstar

import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.util.world.FastVector
import com.lambda.util.world.toCenterVec3d
import com.lambda.util.world.toVec3d
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * A 3D graph that uses FastVector (a Long) to represent 3D nodes.
 *
 * Runtime Complexity:
 * - successor(u): O(N), where N is the number of neighbors of node u, due to node initialization.
 * - predecessors(u): O(N), similar reasoning to successors(u).
 * - cost(u, v): O(1), constant time lookup after initialization.
 * - contains(u): O(1), hash map lookup.
 *
 * Space Complexity:
 * - O(V + E), where V = number of nodes (vertices) initialized and E = number of edges stored.
 * - Additional memory overhead is based on the dynamically expanding hash maps.
 */
class LazyGraph(
    private val nodeInitializer: (FastVector) -> Map<FastVector, Double>
) {
    private val successors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()
    private val predecessors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()
    private val dirtyNodes = mutableSetOf<FastVector>()

    val size get() = successors.size

    /** Initializes a node if not already initialized, then returns successors. */
    fun successors(u: FastVector) =
        successors.computeIfAbsent(u) {
            val neighbors = nodeInitializer(u)
            neighbors.forEach { (neighbor, cost) ->
                predecessors.computeIfAbsent(neighbor) { hashMapOf() }[u] = cost
            }
            neighbors.toMutableMap()
        }

    /** Initializes predecessors by ensuring successors of neighboring nodes. */
    fun predecessors(u: FastVector): Map<FastVector, Double> {
        successors(u)
        return predecessors[u] ?: emptyMap()
    }

    fun markDirty(pos: FastVector) {
        dirtyNodes.add(pos)
        predecessors[pos]?.keys?.let { pred ->
            dirtyNodes.addAll(pred)
        }
    }

    fun clearDirty() = dirtyNodes.clear()

    /** Returns the cost of the edge from u to v (or ∞ if none exists) */
    fun cost(u: FastVector, v: FastVector): Double = successors(u)[v] ?: Double.POSITIVE_INFINITY

    fun contains(u: FastVector): Boolean = successors.containsKey(u)

    fun clear() {
        successors.clear()
        predecessors.clear()
    }

    fun render(renderer: StaticESP) {
        successors.entries.take(1000).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, cost) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
    }

    fun buildDebugInfo() {
//        val projection = buildWorldProjection(blockPos, 0.4, Matrices.ProjRotationMode.TO_CAMERA)
//        withVertexTransform(projection) {
//            val lines = arrayOf(
//                ""
//            )
//
//            var height = -0.5 * lines.size * (FontRenderer.getHeight() + 2)
//
//            lines.forEach {
//                drawString(it, Vec2d(-FontRenderer.getWidth(it) * 0.5, height))
//                height += FontRenderer.getHeight() + 2
//            }
//        }
    }
}
