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

import com.lambda.context.SafeContext
import com.lambda.util.world.FastVector

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
    private val successors = hashMapOf<FastVector, MutableMap<FastVector, Double>>()
    private val predecessors = hashMapOf<FastVector, MutableMap<FastVector, Double>>()

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

    /** Returns the cost of the edge from u to v (or ∞ if none exists) */
    fun cost(u: FastVector, v: FastVector): Double = successors(u)[v] ?: Double.POSITIVE_INFINITY

    fun contains(u: FastVector): Boolean = u in successors

    fun clear() {
        successors.clear()
        predecessors.clear()
    }
}
