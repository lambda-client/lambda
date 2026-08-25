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

import com.lambda.pathing.graph.LazyGraph
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import kotlin.math.abs
import kotlin.math.sqrt

object GridGraphTestUtil {
    enum class Connectivity(val minDistSq: Int, val maxDistSq: Int) {
        N6(1, 1),
        N18(1, 2),
        N26(1, 3),
    }

    fun graph(
        blockedNodes: Set<FastVector> = emptySet(),
        connectivity: Connectivity = Connectivity.N6,
    ) = LazyGraph<FastVector>(successorProvider = { node ->
        if (node in blockedNodes) emptyMap()
        else neighborhood(node, connectivity).filterKeys { it !in blockedNodes }
    })

    fun affectedNodes(node: FastVector, connectivity: Connectivity = Connectivity.N26): Set<FastVector> =
        neighborhood(node, connectivity).keys + node

    fun neighborhood(origin: FastVector, connectivity: Connectivity): Map<FastVector, Double> =
        neighborhood(origin, connectivity.minDistSq, connectivity.maxDistSq)

    fun neighborhood(origin: FastVector, minDistSq: Int, maxDistSq: Int): Map<FastVector, Double> =
        (-1..1).flatMap { dx ->
            (-1..1).flatMap { dy ->
                (-1..1).mapNotNull { dz ->
                    val distSq = dx * dx + dy * dy + dz * dz
                    if (distSq in minDistSq..maxDistSq) {
                        val neighbor = fastVectorOf(origin.x + dx, origin.y + dy, origin.z + dz)
                        neighbor to sqrt(distSq.toDouble())
                    } else null
                }
            }
        }.toMap()

    fun manhattan(a: FastVector, b: FastVector) =
        (abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)).toDouble()

    fun euclidean(a: FastVector, b: FastVector): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun List<FastVector>.length() = zipWithNext { a, b -> euclidean(a, b) }.sum()
}
