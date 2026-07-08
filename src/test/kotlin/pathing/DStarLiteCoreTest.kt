/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.core.DStarLite
import com.lambda.pathing.core.Key
import com.lambda.util.world.fastVectorOf
import pathing.GridGraphTestUtil.Connectivity.N26
import pathing.GridGraphTestUtil.Connectivity.N6
import pathing.GridGraphTestUtil.affectedNodes
import pathing.GridGraphTestUtil.euclidean
import pathing.GridGraphTestUtil.graph
import pathing.GridGraphTestUtil.length
import pathing.GridGraphTestUtil.manhattan
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DStarLiteCoreTest {
    @Test
    fun `initialize sets goal rhs to zero and queues the goal`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val planner = DStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        assertEquals(0.0, planner.rhs(goal))
        assertEquals(Double.POSITIVE_INFINITY, planner.g(goal))
        assertEquals(1, planner.queue.size())
        assertEquals(goal, planner.queue.top())
        assertEquals(Key(manhattan(start, goal), 0.0), planner.queue.topKey(Key.INFINITY))
    }

    @Test
    fun `computeShortestPath finds straight path on six connected grid`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val planner = DStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        val result = planner.computeShortestPath()
        val path = planner.path()

        assertFalse(result.timedOut)
        assertEquals(5.0, planner.g(start), 0.001)
        assertEquals(0.0, planner.g(goal), 0.001)
        assertEquals(
            listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(1, 0, 0),
                fastVectorOf(2, 0, 0),
                fastVectorOf(3, 0, 0),
                fastVectorOf(4, 0, 0),
                fastVectorOf(5, 0, 0),
            ),
            path,
        )
    }

    @Test
    fun `computeShortestPath finds diagonal path on twenty six connected grid`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(2, 2, 2)
        val planner = DStarLite(graph(connectivity = N26), start, goal, ::euclidean)

        planner.computeShortestPath()

        assertEquals(2.0 * sqrt(3.0), planner.g(start), 0.001)
        assertEquals(
            listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(1, 1, 1),
                fastVectorOf(2, 2, 2),
            ),
            planner.path(),
        )
    }

    @Test
    fun `updateStart advances km and reuses the previous search`() {
        val start1 = fastVectorOf(0, 0, 0)
        val start2 = fastVectorOf(1, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val planner = DStarLite(graph(connectivity = N6), start1, goal, ::manhattan)

        planner.computeShortestPath()
        planner.updateStart(start2)
        planner.computeShortestPath()

        assertEquals(1.0, planner.km, 0.001)
        assertEquals(4.0, planner.g(start2), 0.001)
        assertEquals(start2, planner.path().first())
        assertEquals(goal, planner.path().last())
    }

    @Test
    fun `start equals goal produces a single node path`() {
        val start = fastVectorOf(3, 3, 3)
        val planner = DStarLite(graph(connectivity = N26), start, start, ::euclidean)

        planner.computeShortestPath()

        assertEquals(0.0, planner.g(start), 0.001)
        assertEquals(listOf(start), planner.path())
    }

    @Test
    fun `synchronizeAffected repairs path after a known node becomes blocked`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val blocked = mutableSetOf<Long>()
        val planner = DStarLite(graph(blocked, N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val initialPath = planner.path()

        val blockedNode = fastVectorOf(2, 0, 0)
        blocked += blockedNode
        val sync = planner.synchronizeAffected(affectedNodes(blockedNode, N6))
        planner.computeShortestPath()
        val repairedPath = planner.path()

        assertTrue(sync.nodesChecked > 0)
        assertTrue(blockedNode !in repairedPath)
        assertEquals(start, repairedPath.first())
        assertEquals(goal, repairedPath.last())
        assertTrue(repairedPath.length() > initialPath.length())
    }

    @Test
    fun `synchronizeAffected repairs path after a blocked node becomes passable`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val blockedNode = fastVectorOf(2, 0, 0)
        val blocked = mutableSetOf(blockedNode)
        val planner = DStarLite(graph(blocked, N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val detour = planner.path()

        blocked -= blockedNode
        planner.synchronizeAffected(affectedNodes(blockedNode, N6))
        planner.computeShortestPath()
        val repairedPath = planner.path()

        assertTrue(blockedNode in repairedPath)
        assertTrue(repairedPath.length() < detour.length())
    }

    @Test
    fun `synchronizeAffected ignores unknown far away graph nodes`() {
        val start = fastVectorOf(0, 0, 0)
        val goal = fastVectorOf(5, 0, 0)
        val planner = DStarLite(graph(connectivity = N6), start, goal, ::manhattan)

        planner.computeShortestPath()
        val path = planner.path()
        val beforeKm = planner.km
        val sync = planner.synchronizeAffected(affectedNodes(fastVectorOf(100, 0, 100), N6))
        planner.computeShortestPath()

        assertEquals(0, sync.nodesChecked)
        assertEquals(beforeKm, planner.km)
        assertEquals(path, planner.path())
    }
}
