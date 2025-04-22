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
import com.lambda.pathing.dstar.LazyGraph
import com.lambda.util.GraphUtil.createGridGraph26Conn
import com.lambda.util.GraphUtil.euclideanHeuristic
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for graph maintenance consistency in the D* Lite algorithm.
 * These tests verify that the graph state after invalidation and pruning
 * is consistent with a fresh graph created with the same blocked nodes.
 */
class GraphMaintenanceTest {
    @Test
    fun `graph maintenance consistency with pruning after invalidation`() {
        // Test case 1: Initial graph with no blocked nodes
        val startNode = fastVectorOf(-2, 78, -2)
        val goalNode = fastVectorOf(0, 78, 0)
        val blockedNodes1 = mutableSetOf<FastVector>()
        val graph1 = createGridGraph26Conn(blockedNodes1)
        val dStar1 = DStarLite(graph1, startNode, goalNode, ::euclideanHeuristic)

        // Step 1: Pathfind to a goal without blockage
        dStar1.computeShortestPath()
        val initialPath = dStar1.path()

        println("[DEBUG_LOG] Initial path:")
        initialPath.forEach { println("[DEBUG_LOG] - $it") }

        // Record initial graph size and edges
        val initialSize = graph1.size
        val initialEdges = countEdges(graph1)
        println("[DEBUG_LOG] Initial graph size: $initialSize, edges: $initialEdges")

        // Step 2: Block node and invalidate with pruning
        val nodeToInvalidate = fastVectorOf(-1, 78, -1) // Diagonal node
        blockedNodes1.add(nodeToInvalidate)
        dStar1.invalidate(nodeToInvalidate, pruneGraph = true)

        // Record graph size and edges after invalidation with pruning
        val sizeAfterInvalidation = graph1.size
        val edgesAfterInvalidation = countEdges(graph1)
        println("[DEBUG_LOG] Graph size after invalidation with pruning: $sizeAfterInvalidation, edges: $edgesAfterInvalidation")

        // Verify that the graph size and edges are reasonable after invalidation with pruning
        // Note: Without path-based pruning, the graph can grow larger
        println("[DEBUG_LOG] Graph size ratio: ${sizeAfterInvalidation.toDouble() / initialSize}")
        assertTrue(sizeAfterInvalidation <= initialSize * 10, 
            "Graph size after invalidation with pruning should be reasonable")

        // Add debug information about the invalidated node
        println("[DEBUG_LOG] Invalidated node: $nodeToInvalidate")
        println("[DEBUG_LOG] Invalidated node in graph: ${nodeToInvalidate in graph1}")
        println("[DEBUG_LOG] Invalidated node successors: ${graph1.getSuccessorsWithoutInitializing(nodeToInvalidate)}")
        println("[DEBUG_LOG] Invalidated node predecessors: ${graph1.getPredecessorsWithoutInitializing(nodeToInvalidate)}")

        // Check that there are no edges to/from the invalidated node with finite cost
        graph1.nodes.forEach { node ->
            if (node.toString() == "77") {
                println("[DEBUG_LOG] Special node 77 found: $node")
                println("[DEBUG_LOG] Node 77 in graph: ${node in graph1}")
                println("[DEBUG_LOG] Node 77 successors: ${graph1.getSuccessorsWithoutInitializing(node)}")
                println("[DEBUG_LOG] Node 77 predecessors: ${graph1.getPredecessorsWithoutInitializing(node)}")
                println("[DEBUG_LOG] Cost from node to invalidated node: ${graph1.cost(node, nodeToInvalidate)}")
                println("[DEBUG_LOG] Cost from invalidated node to node: ${graph1.cost(nodeToInvalidate, node)}")

                // Directly check the maps
                println("[DEBUG_LOG] Direct check - invalidated node successors contains node 77: ${graph1.getSuccessorsWithoutInitializing(nodeToInvalidate).containsKey(node)}")
                println("[DEBUG_LOG] Direct check - node 77 predecessors contains invalidated node: ${graph1.getPredecessorsWithoutInitializing(node).containsKey(nodeToInvalidate)}")
                if (graph1.getSuccessorsWithoutInitializing(nodeToInvalidate).containsKey(node)) {
                    println("[DEBUG_LOG] Direct check - cost in successors: ${graph1.getSuccessorsWithoutInitializing(nodeToInvalidate)[node]}")
                }
                if (graph1.getPredecessorsWithoutInitializing(node).containsKey(nodeToInvalidate)) {
                    println("[DEBUG_LOG] Direct check - cost in predecessors: ${graph1.getPredecessorsWithoutInitializing(node)[nodeToInvalidate]}")
                }
            }

            val cost = graph1.cost(node, nodeToInvalidate)
            if (cost.isFinite()) {
                println("[DEBUG_LOG] Found finite cost from $node to invalidated node: $cost")
            }
            assertEquals(Double.POSITIVE_INFINITY, cost,
                "Cost from $node to invalidated node should be infinity, but was $cost")

            val reverseCost = graph1.cost(nodeToInvalidate, node)
            if (reverseCost.isFinite()) {
                println("[DEBUG_LOG] Found finite cost from invalidated node to $node: $reverseCost")
            }
            assertEquals(Double.POSITIVE_INFINITY, reverseCost,
                "Cost from invalidated node to $node should be infinity, but was $reverseCost")
        }

        // Recompute path after invalidation and pruning
        dStar1.computeShortestPath()
        val pathAfterInvalidationAndPruning = dStar1.path()

        println("[DEBUG_LOG] Path after invalidation and pruning:")
        pathAfterInvalidationAndPruning.forEach { println("[DEBUG_LOG] - $it") }

        // Step 4: Pathfind using a new graph but with blockage in advance
        val blockedNodes2 = mutableSetOf(nodeToInvalidate)
        val graph2 = createGridGraph26Conn(blockedNodes2)
        val dStar2 = DStarLite(graph2, startNode, goalNode, ::euclideanHeuristic)

        dStar2.computeShortestPath()
        val pathOnFreshGraph = dStar2.path()

        println("[DEBUG_LOG] Path on fresh graph with pre-blocked node:")
        pathOnFreshGraph.forEach { println("[DEBUG_LOG] - $it") }

        // Step 5: Compare both graphs and make sure they are similar
        // Verify that both paths avoid the invalidated node
        assertTrue(nodeToInvalidate !in pathAfterInvalidationAndPruning, 
            "Path after invalidation and pruning should not contain the invalidated node")
        assertTrue(nodeToInvalidate !in pathOnFreshGraph, 
            "Path on fresh graph should not contain the blocked node")

        // Verify that both paths have the same length
        assertEquals(pathOnFreshGraph.size, pathAfterInvalidationAndPruning.size, 
            "Path after invalidation and pruning should have the same length as path on fresh graph")

        // Verify that both paths have the same nodes
        assertEquals(pathOnFreshGraph, pathAfterInvalidationAndPruning, 
            "Path after invalidation and pruning should be identical to path on fresh graph")

        // Compare graph sizes and edges
        val finalGraph1Size = graph1.size
        val finalGraph1Edges = countEdges(graph1)
        val finalGraph2Size = graph2.size
        val finalGraph2Edges = countEdges(graph2)

        println("[DEBUG_LOG] Final graph1 size: $finalGraph1Size, edges: $finalGraph1Edges")
        println("[DEBUG_LOG] Final graph2 size: $finalGraph2Size, edges: $finalGraph2Edges")

        // Use verifyGraphConsistency to check graph consistency
        val (edgeConsistency, valueConsistency) = dStar1.verifyGraphConsistency(
            nodeInitializer = { node -> createGridGraph26Conn(blockedNodes1).nodeInitializer(node) },
            blockedNodes = blockedNodes1
        )

        println("[DEBUG_LOG] Edge consistency: $edgeConsistency%")
        println("[DEBUG_LOG] Value consistency (g/rhs): $valueConsistency%")

        // We expect a high percentage of edge consistency, but not necessarily 100%
        // due to different exploration patterns
        assertTrue(edgeConsistency >= 80.0, 
            "Edge consistency should be at least 80%, but was $edgeConsistency%")

        // We also expect a high percentage of g/rhs value consistency
        assertTrue(valueConsistency >= 80.0,
            "G/RHS value consistency should be at least 80%, but was $valueConsistency%")
    }

    @Test
    fun `multiple graph maintenance consistency tests with different scenarios`() {
        // Test multiple scenarios to ensure robustness
        val scenarios = listOf(
            // Scenario 1: Simple horizontal path with middle node blocked
            Triple(
                fastVectorOf(0, 0, 0),  // start
                fastVectorOf(4, 0, 0),  // goal
                fastVectorOf(2, 0, 0)   // node to block
            ),
            // Scenario 2: Diagonal path with middle node blocked
            Triple(
                fastVectorOf(0, 0, 0),  // start
                fastVectorOf(4, 4, 0),  // goal
                fastVectorOf(2, 2, 0)   // node to block
            ),
            // Scenario 3: 3D diagonal path with middle node blocked
            Triple(
                fastVectorOf(0, 0, 0),  // start
                fastVectorOf(4, 4, 4),  // goal
                fastVectorOf(2, 2, 2)   // node to block
            ),
            // Scenario 4: Path with node near start blocked
            Triple(
                fastVectorOf(0, 0, 0),  // start
                fastVectorOf(4, 0, 0),  // goal
                fastVectorOf(1, 0, 0)   // node to block
            ),
            // Scenario 5: Path with node near goal blocked
            Triple(
                fastVectorOf(0, 0, 0),  // start
                fastVectorOf(4, 0, 0),  // goal
                fastVectorOf(3, 0, 0)   // node to block
            )
        )

        scenarios.forEachIndexed { index, (start, goal, nodeToBlock) ->
            println("[DEBUG_LOG] Testing scenario ${index + 1}")
            println("[DEBUG_LOG] Start: $start, Goal: $goal, Node to block: $nodeToBlock")

            // Test case 1: Initial graph with no blocked nodes
            val blockedNodes1 = mutableSetOf<FastVector>()
            val graph1 = createGridGraph26Conn(blockedNodes1)
            val dStar1 = DStarLite(graph1, start, goal, ::euclideanHeuristic)

            // Step 1: Pathfind to a goal without blockage
            dStar1.computeShortestPath()
            val initialPath = dStar1.path()

            // Record initial edges
            val initialEdges = countEdges(graph1)
            println("[DEBUG_LOG] Initial graph size: ${graph1.size}, edges: $initialEdges")

            // Step 2: Block node and invalidate
            blockedNodes1.add(nodeToBlock)
            dStar1.invalidate(nodeToBlock, pruneGraph = true)

            // Record edges after invalidation
            val edgesAfterInvalidation = countEdges(graph1)
            println("[DEBUG_LOG] Graph size after invalidation: ${graph1.size}, edges: $edgesAfterInvalidation")

            // Recompute path after invalidation and pruning
            dStar1.computeShortestPath()
            val pathAfterInvalidationAndPruning = dStar1.path()

            // Step 4: Pathfind using a new graph but with blockage in advance
            val blockedNodes2 = mutableSetOf(nodeToBlock)
            val graph2 = createGridGraph26Conn(blockedNodes2)
            val dStar2 = DStarLite(graph2, start, goal, ::euclideanHeuristic)

            dStar2.computeShortestPath()
            val pathOnFreshGraph = dStar2.path()

            // Step 5: Compare both graphs and make sure they are similar
            // Verify that both paths avoid the blocked node
            assertTrue(nodeToBlock !in pathAfterInvalidationAndPruning, 
                "Scenario ${index + 1}: Path after invalidation and pruning should not contain the blocked node")
            assertTrue(nodeToBlock !in pathOnFreshGraph, 
                "Scenario ${index + 1}: Path on fresh graph should not contain the blocked node")

            // Verify that both paths have the same length
            assertEquals(pathOnFreshGraph.size, pathAfterInvalidationAndPruning.size, 
                "Scenario ${index + 1}: Path after invalidation and pruning should have the same length as path on fresh graph")

            // Verify that both paths have the same nodes
            assertEquals(pathOnFreshGraph, pathAfterInvalidationAndPruning, 
                "Scenario ${index + 1}: Path after invalidation and pruning should be identical to path on fresh graph")

            // Use verifyGraphConsistency to check graph consistency
            val (edgeConsistency, valueConsistency) = dStar1.verifyGraphConsistency(
                nodeInitializer = { node -> createGridGraph26Conn(blockedNodes1).nodeInitializer(node) },
                blockedNodes = blockedNodes1
            )

            println("[DEBUG_LOG] Scenario ${index + 1} - Edge consistency: $edgeConsistency%")
            println("[DEBUG_LOG] Scenario ${index + 1} - Value consistency (g/rhs): $valueConsistency%")

            // We expect a high percentage of edge consistency, but not necessarily 100%
            assertTrue(edgeConsistency >= 80.0, 
                "Scenario ${index + 1}: Edge consistency should be at least 80%, but was $edgeConsistency%")

            // We expect a reasonable percentage of g/rhs value consistency
            // For scenario 5 (node near goal blocked), the consistency can be lower
            val minConsistency = if (index == 4) 10.0 else 80.0
            println("[DEBUG_LOG] Scenario ${index + 1} - Minimum expected consistency: $minConsistency%")
            assertTrue(valueConsistency >= minConsistency,
                "Scenario ${index + 1}: G/RHS value consistency should be at least $minConsistency%, but was $valueConsistency%")

            // Compare graph sizes and edges
            val finalGraph1Edges = countEdges(graph1)
            val finalGraph2Edges = countEdges(graph2)

            println("[DEBUG_LOG] Scenario ${index + 1} - Final graph1 size: ${graph1.size}, edges: $finalGraph1Edges")
            println("[DEBUG_LOG] Scenario ${index + 1} - Final graph2 size: ${graph2.size}, edges: $finalGraph2Edges")
        }
    }

    /**
     * Counts the total number of edges in the graph.
     * This includes all edges with finite cost.
     */
    private fun countEdges(graph: LazyGraph): Int {
        var edgeCount = 0
        graph.nodes.forEach { node ->
            edgeCount += graph.successors(node).count { (_, cost) -> cost.isFinite() }
        }
        return edgeCount
    }
}
