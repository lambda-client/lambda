/*
 * Copyright 2026 Lambda
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

import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.graph.EdgeSink
import com.lambda.pathing.graph.LongDStarLite
import com.lambda.pathing.graph.LongLazyGraph
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import kotlin.math.abs
import kotlin.math.sqrt
import pathing.reference.LazyGraph

/**
 * Grid graphs over [PackedStance]-packed nodes (speed bit 0). The same neighbourhood is
 * offered in both provider shapes so the packed engine and the reference engine can be
 * built over identical adjacency in identical insertion order.
 */
object GridGraphTestUtil {
    enum class Connectivity(val minDistSq: Int, val maxDistSq: Int) {
        N6(1, 1),
        N18(1, 2),
        N26(1, 3),
    }

    fun node(x: Int, y: Int, z: Int): Long = PackedStance.pack(x, y, z, SpeedClass.STOPPED)

    val Long.x: Int get() = PackedStance.unpackX(this)
    val Long.y: Int get() = PackedStance.unpackY(this)
    val Long.z: Int get() = PackedStance.unpackZ(this)

    fun graph(
        blockedNodes: Set<Long> = emptySet(),
        connectivity: Connectivity = Connectivity.N6,
    ) = LongLazyGraph(successorProvider = { node, sink ->
        if (node !in blockedNodes) forEachNeighbor(node, connectivity) { neighbor, cost ->
            if (neighbor !in blockedNodes) sink.add(neighbor, cost)
        }
    })

    /** The reference engine's shape of [graph]: same nodes, same order. */
    fun referenceGraph(
        blockedNodes: Set<Long> = emptySet(),
        connectivity: Connectivity = Connectivity.N6,
    ) = LazyGraph<Long>(successorProvider = { node ->
        if (node in blockedNodes) emptyMap()
        else neighborhood(node, connectivity).filterKeys { it !in blockedNodes }
    })

    fun affectedNodes(node: Long, connectivity: Connectivity = Connectivity.N26): Set<Long> =
        neighborhood(node, connectivity).keys + node

    fun neighborhood(origin: Long, connectivity: Connectivity): Map<Long, Double> =
        neighborhood(origin, connectivity.minDistSq, connectivity.maxDistSq)

    fun neighborhood(origin: Long, minDistSq: Int, maxDistSq: Int): Map<Long, Double> {
        val out = LinkedHashMap<Long, Double>()
        forEachNeighbor(origin, minDistSq, maxDistSq) { neighbor, cost -> out[neighbor] = cost }
        return out
    }

    inline fun forEachNeighbor(origin: Long, connectivity: Connectivity, action: (Long, Double) -> Unit) =
        forEachNeighbor(origin, connectivity.minDistSq, connectivity.maxDistSq, action)

    inline fun forEachNeighbor(origin: Long, minDistSq: Int, maxDistSq: Int, action: (Long, Double) -> Unit) {
        val ox = origin.x
        val oy = origin.y
        val oz = origin.z
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq in minDistSq..maxDistSq) {
                action(node(ox + dx, oy + dy, oz + dz), sqrt(distSq.toDouble()))
            }
        }
    }

    fun manhattan(a: Long, b: Long) =
        (abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)).toDouble()

    fun euclidean(a: Long, b: Long): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun LongList.length(): Double {
        var total = 0.0
        for (i in 1 until size) total += euclidean(getLong(i - 1), getLong(i))
        return total
    }

    fun List<Long>.length() = zipWithNext { a, b -> euclidean(a, b) }.sum()

    fun longs(vararg nodes: Long): List<Long> = nodes.toList()

    /** Adapts a map-shaped provider to the sink shape, preserving iteration order. */
    fun EdgeSink.addAll(edges: Map<Long, Double>) {
        for ((node, cost) in edges) add(node, cost)
    }
}

/** The greedy descent as a bare node list; the production planner consumes [LongDStarLite.routeCandidate]. */
fun LongDStarLite.path(maxLength: Int = 10_000): LongArrayList = routeCandidate(maxLength)?.nodes ?: LongArrayList()
