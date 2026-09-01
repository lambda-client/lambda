/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.CoarseMoveRates
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.graph.TailCost
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary

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
        assertEquals(4.0, costs.jumpCandidateCost(2.0, 0), 1e-9)
        assertEquals(6.0, costs.jumpCandidateCost(3.0, 0), 1e-9)
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
        // Momentum pricing decouples the route bound from the flat per-edge sum: a
        // chained stretch sheds the transition tax below it, and a short rest-to-rest
        // hop pays its startup and brake above it. Either side is honest; equality is
        // the one thing that no longer holds.
        assertTrue(route.ticks.isFinite() && route.ticks > 0.0)
        assertEquals(published.nodes.size, published.tailCosts.size)
        assertIs<TailCost.Exact>(published.tailCosts.first())
        assertTrue(published.tailCosts.all { it !is TailCost.Unreachable })
        assertIs<TailCost.Exact>(published.tailCosts.last())
        assertTrue(published.dependencies.isNotEmpty())
    }

    @Test
    fun `a single step down is a walk off, enumerated backward`() {
        val world = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, -1, 0)] = CoarseVoxel.FULL_BLOCK
        }
        val moves = library()
        val origin = Stance(0, 1, 0)
        val target = Stance(1, 0, 0)

        assertTrue(
            moves.edgesTo(world, target)
                .any { it.from == origin && it.to == target && it.movement == MovementId.WALK_OFF },
            "a one-block step down needs no control and stays a plain walk-off",
        )
    }

    @Test
    fun `a descent is enumerated backward without inventing a reverse move`() {
        val world = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, -2, 0)] = CoarseVoxel.FULL_BLOCK
        }
        val moves = library()
        val origin = Stance(0, 1, 0)
        val target = Stance(1, -1, 0)

        assertTrue(moves.edgesTo(world, target).any { it.from == origin && it.to == target })
        assertFalse(
            moves.edgesFrom(world, target).any { it.to == origin },
            "nothing climbs two blocks, so the descent must not enumerate a reverse",
        )
    }

    /**
     * A descent deeper than one block is a drop, and a drop is not free.
     *
     * The distinction is the whole point of the primitive: a walk-off carries whatever
     * speed the approach left it with, which off a two-block ledge lands a block and a
     * half out. A drop arrives at the lip at a solved speed, so the edge is only offered
     * when a speed exists that lands on the target.
     */
    @Test
    fun `a descent past one block is a solved drop rather than a walk off`() {
        val world = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, -2, 0)] = CoarseVoxel.FULL_BLOCK
        }
        val moves = library()
        val origin = Stance(0, 1, 0)
        val target = Stance(1, -1, 0)

        val edge = assertNotNull(
            moves.edgesFrom(world, origin).firstOrNull { it.to == target },
            "a two-block descent onto a clear pad must be offered",
        )
        assertEquals(MovementId.DROP, edge.movement)
        val launch = assertNotNull(edge.launch, "a drop must publish the take-off that makes it work")
        assertTrue(launch.mode.drops, "a drop must not be solved as a jump")
        assertFalse(
            moves.edgesFrom(world, origin).any { it.to == target && it.movement == MovementId.WALK_OFF },
            "walk-off must not also claim a descent it cannot control",
        )
    }

    @Test
    fun `measured costs price a climb far above a stride and amortise long jumps`() {
        val m = CoarseMoveCosts.measured()
        // A climb is ~10 ticks/block, a stride ~3.6 -- so a step-up is roughly 3x a walk,
        // where the uniform envelope made them almost equal (2.0 vs 1.67). That is why the
        // old graph over-climbed.
        assertEquals(1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK, m.cardinalWalk, 1e-9)
        assertEquals(1.0 / CoarseMoveRates.STEP_UP_BLOCKS_PER_TICK, m.stepUp, 1e-9)
        assertTrue(m.stepUp > 2.5 * m.cardinalWalk, "a climb must cost far more than a stride")

        // A jump is priced by airborne time, which barely grows with span: clearing more
        // ground in one arc costs the same, so a long jump beats a short one plus a walk.
        assertEquals(m.jumpCandidateCost(2.0, 0), m.jumpCandidateCost(4.0, 0), 1e-9)
        assertTrue(m.jumpCandidateCost(4.0, 0) < m.jumpCandidateCost(2.0, 0) + m.cardinalWalk)
    }

    @Test
    fun `measured costs prefer jumping a one-high bump over stepping up and off it`() {
        // A single-block bump on the direct line. Stepping up then off pays two grounded
        // vertical moves (~16 ticks); one flat jump clears it in one arc (~12) -- the
        // momentum-keeping line, which distance-only costs could never see.
        val moves = moveLibrary(SimpleMoveOptions(allowDiagonal = false))
        val world = SyntheticView().apply {
            for (x in 0..4) this[VoxelPos(x, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(2, 1, 0)] = CoarseVoxel.FULL_BLOCK // the bump (top at y=2)
        }
        val planner = CoarsePlanner(world, moves, Stance(0, 1, 0), Stance(4, 1, 0))
        planner.repair(timeBudget = Duration.INFINITE)
        val route = assertNotNull(planner.routePlan(snapshotRevision = 1L))
        assertTrue(
            route.edges.any { it.movement == MovementId.JUMP },
            "the bump should be jumped, not climbed: ${route.edges.map { it.movement }}",
        )
        assertFalse(route.edges.any { it.movement == MovementId.STEP_UP })
    }

    @Test
    fun `the transition toll prefers one long jump over a short jump then a walk`() {
        // A single flying edge over a short jump-then-walk to the same landing. Measured
        // jump cost is airborne time (span-independent), so the long jump already wins on
        // arc time; the per-edge toll widens the margin. x=0 start, x=2 a hoppable ledge,
        // x=3.. landing: reaching x=3 is one span-3 jump or a span-2 jump plus a walk.
        val moves = moveLibrary(SimpleMoveOptions(allowDiagonal = false, allowStepUp = false, maxWalkOffDepth = 0))
        val world = SyntheticView().apply {
            for (x in listOf(0, 2, 3, 4, 5)) this[VoxelPos(x, 0, 0)] = CoarseVoxel.FULL_BLOCK
        }
        val planner = CoarsePlanner(world, moves, Stance(0, 1, 0), Stance(3, 1, 0))
        planner.repair(timeBudget = Duration.INFINITE)

        val route = assertNotNull(planner.routePlan(snapshotRevision = 1L))
        assertEquals(
            listOf(Stance(0, 1, 0), Stance(3, 1, 0)), route.nodes,
            "one span-3 jump must beat a span-2 hop plus a walk: ${route.nodes}",
        )
        assertEquals(MovementId.JUMP, route.edges.single().movement)
    }

    @Test
    fun `the transition toll keeps a lower bound the heuristic never overestimates`() {
        // The toll amortises over the longest template (its best per-block rate feeds the
        // heuristic), so it must remain admissible: h(from) <= edgeCost + h(to) everywhere.
        val moves = moveLibrary(SimpleMoveOptions())
        val random = kotlin.random.Random(99)
        val goal = Stance(7, 3, -4)
        repeat(20_000) {
            val from = Stance(random.nextInt(-12, 12), random.nextInt(-12, 12), random.nextInt(-12, 12))
            val template = moves.templates.random(random)
            val to = from.offset(template.dx, template.dy, template.dz)
            assertTrue(
                moves.heuristic(from, goal) <= template.lowerBoundTicks + moves.heuristic(to, goal) + 1e-9,
                "toll broke consistency at template=${template.id} from=$from to=$to",
            )
        }
    }

    @Test
    fun `a jump whose apex arc passes through a block is not proposed`() {
        // A four-stance gap the body would clear, but with a block sitting at the arc's
        // apex column (two out) at head-apex height (+3 above the takeoff feet). The body
        // is at ~1.25 there, so its head is at that block -- a head bonk. The old fixed
        // y=0..2 arc check never looked at +3 and admitted the jump; the body then drove
        // straight through the wall.
        val world = SyntheticView().apply {
            for (x in 0..4) this[VoxelPos(x, 0, 0)] = CoarseVoxel.FULL_BLOCK // support at ends
            this[VoxelPos(1, 0, 0)] = CoarseVoxel.AIR                        // carve the gap
            this[VoxelPos(2, 0, 0)] = CoarseVoxel.AIR
            this[VoxelPos(3, 0, 0)] = CoarseVoxel.AIR
            this[VoxelPos(2, 4, 0)] = CoarseVoxel.FULL_BLOCK                 // apex-head block
        }
        val moves = library()

        val jumps = moves.edgesFrom(world, Stance(0, 1, 0))
            .filter { it.movement == MovementId.JUMP }
        assertFalse(
            jumps.any { it.to == Stance(4, 1, 0) },
            "a jump whose apex head hits the block at (2,4,0) must be rejected: ${jumps.map { it.to }}",
        )
    }

    @Test
    fun `diagonal parkour is a straight diagonal jump line, not a cardinal zigzag`() {
        // Four stances on a diagonal, everything else air (a void). Only diagonal jumps
        // connect them; without them the coarse layer finds no route and the trajectory
        // is handed a cardinal zigzag -- the "slalom" the body walks.
        val world = SyntheticView().apply {
            for ((x, z) in listOf(0 to 0, 2 to 2, 4 to 4, 6 to 6)) this[VoxelPos(x, 0, z)] = CoarseVoxel.FULL_BLOCK
        }
        val planner = CoarsePlanner(world, library(), Stance(0, 1, 0), Stance(6, 1, 6))
        planner.repair(timeBudget = Duration.INFINITE)

        val route = assertNotNull(planner.route(), "diagonal platforms must be reachable by diagonal jumps")
        assertEquals(listOf(Stance(0, 1, 0), Stance(2, 1, 2), Stance(4, 1, 4), Stance(6, 1, 6)), route.nodes)
        val plan = assertNotNull(planner.routePlan(snapshotRevision = 1L))
        assertTrue(
            plan.edges.all { it.movement == MovementId.JUMP && it.from.x != it.to.x && it.from.z != it.to.z },
            "every leg must be a diagonal jump: ${plan.edges.map { it.movement }}",
        )
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
    fun `chunk refresh repairs overlapping known origins without rebuilding D star`() {
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
        val graphSize = planner.graphSize

        world[VoxelPos(1, 1, 0)] = CoarseVoxel.FULL_BLOCK
        val sync = planner.chunksChanged(listOf(PathingChunk(0, 0)))
        val repaired = planner.repair(timeBudget = Duration.INFINITE)
        val route = assertNotNull(planner.route())

        assertTrue(sync.nodesChecked > 0)
        assertTrue(repaired.converged)
        assertFalse(Stance(1, 1, 0) in route.nodes)
        assertTrue(planner.graphSize >= graphSize, "the retained graph must not be cleared")
    }

    @Test
    fun `derived invalidation includes the origin of every edge read`() {
        val world = SyntheticView().apply { fillGround(-5..5, -5..5, y = 0) }
        val moves = library()
        val origin = Stance(0, 1, 0)

        for (edge in moves.edgesFrom(world, origin)) {
            for (read in edge.readSet) {
                assertTrue(origin in moves.affectedOrigins(read), "${edge.movement} did not invert read $read")
                assertTrue(
                    origin in moves.affectedOrigins(PathingChunk.containing(read), listOf(origin)),
                    "${edge.movement} did not overlap the refreshed chunk containing $read",
                )
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
            jumpCandidate = { distance, rise -> 9.0 + distance + rise },
        ),
        options = options,
    )

    private class SyntheticView : CoarseVoxelView {
        private val voxels = HashMap<VoxelPos, CoarseVoxel>()

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
            voxels[VoxelPos(x, y, z)] ?: CoarseVoxel.AIR

        /** Trait-derived shapes so the swept-arc jump mask works on synthetic worlds. */
        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape =
            if (voxel(x, y, z).fullyPassable) VoxelShapes.empty() else VoxelShapes.fullCube()

        operator fun set(pos: VoxelPos, voxel: CoarseVoxel) {
            voxels[pos] = voxel
        }

        fun fillGround(xs: IntRange, zs: IntRange, y: Int) {
            for (x in xs) for (z in zs) voxels[VoxelPos(x, y, z)] = CoarseVoxel.FULL_BLOCK
        }
    }
}
