/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.core.TailCost
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.math.Vec3d
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class CoarsePlannerTest {
    @Test
    fun `kinematic envelope derives optimistic costs and rejects velocity outside its proof scope`() {
        val envelope = CoarseKinematicEnvelope(
            maxHorizontalBlocksPerTick = 0.5,
            maxAscentBlocksPerTick = 0.4,
            maxDescentBlocksPerTick = 2.0,
        )
        val costs = envelope.moveCosts()

        assertEquals(2.0, costs.cardinalWalk, 1e-9)
        assertEquals(kotlin.math.sqrt(2.0) / 0.5, costs.diagonalWalk, 1e-9)
        assertEquals(2.5, costs.stepUp, 1e-9)
        assertEquals(4.0, costs.jumpCandidateCost(2, 0), 1e-9)
        assertEquals(6.0, costs.jumpCandidateCost(3, 0), 1e-9)
        assertEquals(2.0, costs.walkOffCost(4), 1e-9)
        assertTrue(envelope.contains(Vec3d(0.3, 0.2, 0.4)))
        assertFalse(envelope.contains(Vec3d(0.4, 0.0, 0.4)))
        assertFalse(envelope.contains(Vec3d(0.0, -2.1, 0.0)))
    }

    @Test
    fun `simple library plans a flat route through the copied D star core`() {
        val world = SyntheticView().apply { fillGround(-8..8, -8..8, y = 0) }
        val planner = CoarsePlanner(world, library(), Stance(0, 1, 0), Stance(5, 1, 0))

        val result = planner.repair(timeBudget = Duration.INFINITE)
        val route = assertNotNull(planner.route())

        assertTrue(result.converged)
        assertTrue(route.exactFromStart)
        assertEquals(Stance(0, 1, 0), route.nodes.first())
        assertEquals(Stance(5, 1, 0), route.nodes.last())
        assertIs<TailCost.Exact>(planner.tailCost())

        val published = assertNotNull(planner.routePlan(snapshotRevision = 71L))
        assertEquals(71L, published.snapshotRevision)
        assertEquals(route.routeVersion, published.routeVersion)
        assertEquals(route.nodes, published.nodes)
        assertEquals(route.ticks, published.edges.sumOf { it.lowerBoundTicks }, 1e-9)
        assertTrue(published.dependencies.isNotEmpty())
    }

    @Test
    fun `walk off is enumerated backward without inventing a reverse move`() {
        val world = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, -2, 0)] = CoarseVoxel.FULL_BLOCK
        }
        val moves = library()
        val origin = Stance(0, 1, 0)
        val target = Stance(1, -1, 0)

        val incoming = moves.edgesTo(world, target)

        assertTrue(incoming.any { it.from == origin && it.to == target && it.kind == CoarseMoveKind.WALK_OFF })
        assertFalse(moves.edgesFrom(world, target).any { it.to == origin })
    }

    @Test
    fun `world change repairs only derived affected origins and reroutes`() {
        val world = SyntheticView().apply { fillGround(-8..8, -8..8, y = 0) }
        val start = Stance(0, 1, 0)
        val goal = Stance(4, 1, 0)
        val planner = CoarsePlanner(
            world,
            library(SimpleMoveOptions(allowDiagonal = false, allowStepUp = false, maxWalkOffDepth = 0, allowJumpCandidates = false)),
            start,
            goal,
        )
        planner.repair(timeBudget = Duration.INFINITE)
        assertTrue(Stance(1, 1, 0) in assertNotNull(planner.route()).nodes)

        val changed = VoxelPos(1, 1, 0)
        world[changed] = CoarseVoxel.FULL_BLOCK
        val sync = planner.worldChanged(listOf(changed))
        val repaired = planner.repair(timeBudget = Duration.INFINITE)
        val route = assertNotNull(planner.route())

        assertTrue(sync.nodesChecked > 0)
        assertTrue(repaired.converged)
        assertFalse(Stance(1, 1, 0) in route.nodes)
        assertEquals(start, route.nodes.first())
        assertEquals(goal, route.nodes.last())
    }

    @Test
    fun `derived invalidation includes the origin of every edge read`() {
        val world = SyntheticView().apply { fillGround(-5..5, -5..5, y = 0) }
        val moves = library()
        val origin = Stance(0, 1, 0)

        for (edge in moves.edgesFrom(world, origin)) {
            for (read in edge.readSet) {
                assertTrue(origin in moves.affectedOrigins(read), "${edge.kind} did not invert read $read")
            }
        }
    }

    @Test
    fun `derived anisotropic heuristic is consistent with every enabled template`() {
        val moves = library()
        val random = Random(42)

        repeat(10_000) {
            val from = Stance(random.nextInt(-20, 21), random.nextInt(-5, 6), random.nextInt(-20, 21))
            val goal = Stance(random.nextInt(-20, 21), random.nextInt(-5, 6), random.nextInt(-20, 21))
            val template = moves.templates.random(random)
            val to = from.offset(template.dx, template.dy, template.dz)

            assertTrue(
                moves.heuristic(from, goal) <= template.lowerBoundTicks + moves.heuristic(to, goal) + 1e-9,
                "inconsistent template=${template.id} from=$from to=$to goal=$goal",
            )
        }
    }

    private fun library(options: SimpleMoveOptions = SimpleMoveOptions()) = SimpleMoveLibrary.build(
        costs = CoarseMoveCosts(
            cardinalWalk = 4.0,
            diagonalWalk = 5.7,
            stepUp = 8.0,
            walkOff = { depth -> 4.0 + depth },
            jumpCandidate = { span, rise -> 9.0 + span + rise },
        ),
        options = options,
    )

    private class SyntheticView : CoarseVoxelView {
        private val voxels = HashMap<VoxelPos, CoarseVoxel>()

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
            voxels[VoxelPos(x, y, z)] ?: CoarseVoxel.AIR

        operator fun set(pos: VoxelPos, voxel: CoarseVoxel) {
            voxels[pos] = voxel
        }

        fun fillGround(xs: IntRange, zs: IntRange, y: Int) {
            for (x in xs) for (z in zs) voxels[VoxelPos(x, y, z)] = CoarseVoxel.FULL_BLOCK
        }
    }
}
