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

import com.lambda.util.world.FastVector

/**
 * A simple 3D graph that uses FastVector (a Long) to represent 3D nodes.
 *
 * @param adjMap A map from each vertex to a list of (neighbor, cost) pairs.
 */
class Graph(
    private val adjMap: Map<FastVector, List<Pair<FastVector, Double>>>
) {
    // Build reverse adjacency from forward edges.
    private val revAdjMap: Map<FastVector, List<Pair<FastVector, Double>>> by lazy {
        val tmp = mutableMapOf<FastVector, MutableList<Pair<FastVector, Double>>>()
        adjMap.forEach { (u, edges) ->
            edges.forEach { (v, cost) ->
                tmp.getOrPut(v) { mutableListOf() }.add(Pair(u, cost))
            }
        }
        tmp.mapValues { it.value.toList() }
    }

    /** Returns the successors of a vertex. */
    fun successors(u: FastVector) = adjMap[u] ?: emptyList()

    /** Returns the predecessors of a vertex. */
    fun predecessors(u: FastVector) = revAdjMap[u] ?: emptyList()

    /** Returns the cost of the edge from u to v (or ∞ if none exists). */
    fun cost(u: FastVector, v: FastVector) =
        adjMap[u]?.firstOrNull { it.first == v }?.second ?: Double.POSITIVE_INFINITY
}