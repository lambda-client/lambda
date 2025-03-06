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

/**
 * Simple graph class that stores both forward and reverse adjacency.
 *
 * @param adjMap a map from each vertex u to a list of (successor, cost) pairs.
 */
class Graph(private val adjMap: Map<Int, List<Pair<Int, Double>>>) {

    // Build reverse adjacency by scanning all forward edges
    private val revAdjMap: Map<Int, List<Pair<Int, Double>>> by lazy {
        val tmp = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        adjMap.forEach { (u, edges) ->
            edges.forEach { (v, cost) ->
                tmp.getOrPut(v) { mutableListOf() }.add(Pair(u, cost))
            }
        }
        // Convert to immutable
        tmp.mapValues { it.value.toList() }
    }

    /**
     * Returns the successors of u, i.e., all (v, cost) for edges u->v.
     */
    fun successors(u: Int) = adjMap[u] ?: emptyList()

    /**
     * Returns the predecessors of u, i.e., all (v, cost) for edges v->u.
     */
    fun predecessors(u: Int) = revAdjMap[u] ?: emptyList()

    /**
     * Helper to get the cost of an edge u->v, or ∞ if none.
     */
    fun cost(u: Int, v: Int) =
        adjMap[u]
            ?.firstOrNull { it.first == v }
            ?.second
            ?: Double.POSITIVE_INFINITY
}