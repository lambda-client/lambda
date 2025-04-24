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

package pathing

import com.lambda.pathing.dstar.DStarLite
import com.lambda.util.GraphUtil.createGridGraph6Conn
import com.lambda.util.GraphUtil.euclideanHeuristic
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for graph maintenance consistency in the D* Lite algorithm.
 * These tests verify that the graph state after invalidation and pruning
 * is consistent with a fresh graph created with the same blocked nodes.
 */
class GraphConsistencyTest {
    @Test
    fun `graph consistency N6`() {
        val startNode = fastVectorOf(0, 0, 5)
        val goalNode = fastVectorOf(0, 0, 0)
        val blockedNodes = mutableSetOf<FastVector>()
        val graph1 = createGridGraph6Conn(blockedNodes)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        val blockedNode = fastVectorOf(0, 0, 4)
        blockedNodes.add(blockedNode)

        dStar1.invalidate(blockedNode)
        dStar1.computeShortestPath()
        val path1 = dStar1.path()

        assertTrue(initialPath.size < path1.size, "Initial path size (${initialPath.size}) is less than blocked path size (${path1.size})")

        val graph2 = createGridGraph6Conn(blockedNodes)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)
        dStar2.computeShortestPath()
        val path2 = dStar2.path()

        assertEquals(path1, path2, "Graph consistency test failed for N6 graph with blocked node at ${blockedNode.string}.\nPath1: ${path1.string()}\nPath2: ${path2.string()}")

        val (graphDifferences, valueDifferences) = dStar1.compareWith(dStar2)
        assertFalse(graphDifferences.hasAnyDifferences, graphDifferences.toString())
        assertFalse(valueDifferences.isNotEmpty(), valueDifferences.joinToString("\n  "))
    }

    private fun List<FastVector>.string() = joinToString(" -> ") { it.string }
}
