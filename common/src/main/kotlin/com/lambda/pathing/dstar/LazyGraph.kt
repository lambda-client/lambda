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

import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.buildWorldProjection
import com.lambda.graphics.gl.Matrices.withVertexTransform
import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.graphics.renderer.gui.FontRenderer.drawString
import com.lambda.pathing.PathingSettings
import com.lambda.util.math.Vec2d
import com.lambda.util.math.div
import com.lambda.util.math.plus
import com.lambda.util.world.FastVector
import com.lambda.util.world.string
import com.lambda.util.world.toCenterVec3d
import net.minecraft.util.math.Box
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

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
    val successors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()
    val predecessors = ConcurrentHashMap<FastVector, MutableMap<FastVector, Double>>()
    val dirtyNodes = mutableSetOf<FastVector>()

    val nodes get() = successors.keys + predecessors.keys
    val size get() = successors.size

    /** Initializes a node if not already initialized, then returns successors. */
    fun successors(u: FastVector): MutableMap<FastVector, Double> =
        successors.getOrPut(u) {
            val neighbors = nodeInitializer(u)
            neighbors.forEach { (neighbor, cost) ->
                predecessors.getOrPut(neighbor) { hashMapOf() }[u] = cost
            }
            neighbors.toMutableMap()
        }

    /** Initializes predecessors by ensuring successors of neighboring nodes. */
    fun predecessors(u: FastVector): Map<FastVector, Double> {
        successors(u)
        return predecessors[u] ?: emptyMap()
    }

    fun invalidate(u: FastVector) {
        val neighbors = getNeighbors(u)
        (neighbors + u).forEach { v ->
            successors.remove(v)
            predecessors.remove(v)
            predecessors.values.forEach { predMap ->
                predMap.remove(v)
            }
        }
    }

    fun getNeighbors(u: FastVector): Set<FastVector> = successors(u).keys + predecessors(u).keys

    /** Returns the cost of the edge from u to v (or ∞ if none exists) */
    fun cost(u: FastVector, v: FastVector): Double = successors(u)[v] ?: Double.POSITIVE_INFINITY

    fun contains(u: FastVector): Boolean = successors.containsKey(u)

    fun clear() {
        successors.clear()
        predecessors.clear()
        dirtyNodes.clear()
    }

    fun render(renderer: StaticESP, config: PathingSettings) {
        if (!config.renderGraph) return
        successors.entries.take(config.maxRenderObjects).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, _) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
        dirtyNodes.take(config.maxRenderObjects).forEach { node ->
            renderer.buildOutline(Box.of(node.toCenterVec3d(), 0.2, 0.2, 0.2), Color.RED)
        }
    }
}