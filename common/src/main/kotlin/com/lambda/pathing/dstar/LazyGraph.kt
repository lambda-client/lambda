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
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.pathing.PathingSettings
import com.lambda.util.world.FastVector
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
    val invalidated = mutableSetOf<FastVector>()

    val nodes get() = successors.keys + predecessors.keys
    val size get() = nodes.size

    /** Initializes a node if not already initialized, then returns successors. */
    fun successors(u: FastVector): MutableMap<FastVector, Double> =
        successors.getOrPut(u) { initialize(u).toMutableMap() }

    /** Initializes predecessors by ensuring successors of neighboring nodes. */
    fun predecessors(u: FastVector): Map<FastVector, Double> {
        successors(u)
        return predecessors[u] ?: emptyMap()
    }

    fun removeNode(u: FastVector) {
        successors.remove(u)
        successors.values.forEach { it.remove(u) }
        predecessors.remove(u)
        predecessors.values.forEach { it.remove(u) }
    }

    fun removeEdge(u: FastVector, v: FastVector) {
        successors[u]?.remove(v)
        predecessors[v]?.remove(u)
        if (successors[u]?.isEmpty() == true) {
            successors.remove(u)
        }
        if (predecessors[v]?.isEmpty() == true) {
            predecessors.remove(v)
        }
    }

    fun initialize(u: FastVector) =
        nodeInitializer(u).onEach { (neighbor, cost) ->
            predecessors.getOrPut(neighbor) { hashMapOf() }[u] = cost
        }

    fun setCost(u: FastVector, v: FastVector, c: Double) {
        successors[u]?.put(v, c)
        predecessors[v]?.put(u, c)
    }

    fun clear() {
        successors.clear()
        predecessors.clear()
        invalidated.clear()
    }

    fun edges(u: FastVector) = successors(u).entries + predecessors(u).entries
    fun neighbors(u: FastVector): Set<FastVector> = successors(u).keys + predecessors(u).keys

    /** Returns the cost of the edge from u to v (or ∞ if none exists) */
    fun cost(u: FastVector, v: FastVector): Double = successors(u)[v] ?: Double.POSITIVE_INFINITY

    operator fun contains(u: FastVector): Boolean = nodes.contains(u)

    fun render(renderer: StaticESP, config: PathingSettings) {
        if (!config.renderGraph) return
        if (config.renderSuccessors) successors.entries.take(config.maxRenderObjects).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, _) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
        if (config.renderPredecessors) predecessors.entries.take(config.maxRenderObjects).forEach { (origin, neighbors) ->
            neighbors.forEach { (neighbor, _) ->
                renderer.buildLine(origin.toCenterVec3d(), neighbor.toCenterVec3d(), Color.PINK)
            }
        }
        if (config.renderInvalidated) invalidated.take(config.maxRenderObjects).forEach { node ->
            renderer.buildOutline(Box.of(node.toCenterVec3d(), 0.2, 0.2, 0.2), Color.RED)
        }
    }
}