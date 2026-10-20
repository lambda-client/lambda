/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.coarse.CoarseEdgeCache
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.Vanilla
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary

class CoarsePlannerTest {
    @Test
    fun `kinematic envelope derives optimistic costs`() {
        val envelope = CoarseKinematicEnvelope(
            maxHorizontalBlocksPerTick = 0.5,
            maxAscentBlocksPerTick = 0.4,
            maxDescentBlocksPerTick = 2.0,
        )
        val costs = envelope.moveCosts()

        assertEquals(2.0, costs.cardinalWalk, 1e-9)
        assertEquals(kotlin.math.sqrt(2.0) / 0.5, costs.diagonalWalk, 1e-9)
        assertEquals(2.5, costs.stepUp, 1e-9)
        assertEquals(4.0, costs.jumpCandidateCost(2.0, 0)!!, 1e-9)
        assertEquals(6.0, costs.jumpCandidateCost(3.0, 0)!!, 1e-9)
        assertEquals(2.0, costs.walkOffCost(4), 1e-9)
    }

    @Test
    fun `field labels follow the route beyond the label radius around the start`() {
        val world = SyntheticView().apply { fillGround(-4..304, -3..3, y = 0) }
        val start = Stance(0, 1, 0)
        val planner = CoarsePlanner(world, moveLibrary(SimpleMoveOptions()), start, Stance(300, 1, 0))
        assertTrue(planner.repair().converged)
        val route = assertNotNull(planner.routePlan(0L))
        val far = Stance(250, 1, 0)
        assertFalse(planner.labels(start, 128, 64).label(far, SpeedClass.MOVING).isFinite(), "the start box alone ends 128 blocks out")
        val labels = planner.labels(listOf(start), route.nodes, 128, 48, 64)
        assertTrue(labels.label(far, SpeedClass.MOVING).isFinite(), "a stance on the route 250 blocks out must keep its value")
        assertFalse(labels.label(Stance(250, 1, 3), SpeedClass.MOVING).isNaN())
    }

    @Test
    fun `simple library plans a flat route through the copied D star core`() {
        val world = SyntheticView().apply { fillGround(-8..8, -8..8, y = 0) }
        val planner = CoarsePlanner(world, library(), Stance(0, 1, 0), Stance(5, 1, 0))

        val result = planner.repair()
        val route = assertNotNull(planner.route())

        assertTrue(result.converged)
        assertTrue(route.exactFromStart)
        assertEquals(Stance(0, 1, 0), route.nodes.first())
        assertEquals(Stance(5, 1, 0), route.nodes.last())

        val published = assertNotNull(planner.routePlan(snapshotRevision = 71L))
        assertEquals(71L, published.snapshotRevision)
        assertEquals(route.nodes, published.nodes)
        // Momentum pricing decouples the route bound from the flat per-edge sum: a
        // chained stretch sheds the transition tax below it, and a short rest-to-rest
        // hop pays its startup and brake above it. Either side is honest; equality is
        // the one thing that no longer holds.
        assertTrue(route.ticks.isFinite() && route.ticks > 0.0)
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
        assertTrue(m.stepUp > 2.5 * m.cardinalWalk, "a climb must cost far more than a stride")

        // A jump is priced by airborne time, which barely grows with span: clearing more
        // ground in one arc costs the same, so a long jump beats a short one plus a walk.
        assertEquals(m.jumpCandidateCost(2.0, 0)!!, m.jumpCandidateCost(4.0, 0)!!, 1e-9)
        assertTrue(m.jumpCandidateCost(4.0, 0)!! < m.jumpCandidateCost(2.0, 0)!! + m.cardinalWalk)
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
        planner.repair()
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
        planner.repair()

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
    fun `a wide diagonal drop that lands short of a corner walled at the landing level is not proposed`() {
        fun pit(walled: Boolean) = SyntheticView().apply {
            this[VoxelPos(0, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, 0, 0)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(0, -1, -1)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, -1, -1)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(2, -1, -2)] = CoarseVoxel.FULL_BLOCK
            if (walled) this[VoxelPos(1, 0, -2)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(2, 0, -1)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(1, 2, -2)] = CoarseVoxel.FULL_BLOCK
            this[VoxelPos(2, 2, -2)] = CoarseVoxel.FULL_BLOCK
        }
        fun drops(walled: Boolean) = library().edgesFrom(pit(walled), Stance(0, 1, 0)).filter { it.to == Stance(2, 0, -2) && it.movement == MovementId.DROP }
        assertTrue(drops(walled = false).isNotEmpty(), "with one landing-level corner open the drop stays offered")
        assertTrue(drops(walled = true).isEmpty(), "the body lands in the pit beside the target and cannot pass the walled corner")
    }

    @Test
    fun `a jump whose apex arc passes through a block is not proposed`() {
        // A four-stance gap the body would clear, but with a block sitting at the arc's
        // apex column (two out) at head-apex height (+3 above the takeoff feet). The old
        // fixed y=0..2 arc check never looked at +3 and admitted the free arc; the body
        // then drove straight through the wall. The head may only meet the block's
        // underside: a bonked arc that keeps the head below y=4 is the one jump allowed.
        val world = SyntheticView().apply {
            for (x in 0..4) this[VoxelPos(x, 0, 0)] = CoarseVoxel.FULL_BLOCK // support at ends
            this[VoxelPos(1, 0, 0)] = CoarseVoxel.AIR                        // carve the gap
            this[VoxelPos(2, 0, 0)] = CoarseVoxel.AIR
            this[VoxelPos(3, 0, 0)] = CoarseVoxel.AIR
            this[VoxelPos(2, 4, 0)] = CoarseVoxel.FULL_BLOCK                 // apex-head block
        }
        val moves = library()

        val jumps = moves.edgesFrom(world, Stance(0, 1, 0))
            .filter { it.movement == MovementId.JUMP && it.to == Stance(4, 1, 0) }
        for (jump in jumps) {
            val head = 1.0 + jump.launch!!.arc.apex + Vanilla.PLAYER_HEIGHT
            assertTrue(head <= 4.0, "a jump whose apex head passes through the block at (2,4,0) must be rejected, head reached $head")
        }
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
        planner.repair()

        val route = assertNotNull(planner.route(), "diagonal platforms must be reachable by diagonal jumps")
        assertEquals(listOf(Stance(0, 1, 0), Stance(2, 1, 2), Stance(4, 1, 4), Stance(6, 1, 6)), route.nodes)
        val plan = assertNotNull(planner.routePlan(snapshotRevision = 1L))
        assertTrue(
            plan.edges.all { it.movement == MovementId.JUMP && it.from.x != it.to.x && it.from.z != it.to.z },
            "every leg must be a diagonal jump: ${plan.edges.map { it.movement }}",
        )
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
        planner.repair()
        val graphSize = planner.graphSize

        world[VoxelPos(1, 1, 0)] = CoarseVoxel.FULL_BLOCK
        planner.chunksChanged(listOf(PathingChunk(0, 0)))
        val repaired = planner.repair()
        val route = assertNotNull(planner.route())

        assertTrue(repaired.converged)
        assertFalse(Stance(1, 1, 0) in route.nodes)
        assertTrue(planner.graphSize >= graphSize, "the retained graph must not be cleared")
    }

    @Test
    fun `section invalidation covers the origin and target of every edge read`() {
        val world = SyntheticView().apply { fillGround(-5..5, -5..5, y = 0) }
        val moves = library()
        val origin = Stance(0, 1, 0)

        for (edge in moves.edgesFrom(world, origin)) {
            for (read in edge.readSet) {
                val section = PathingSection.containing(read)
                val changed = CellBox.sections(PathingChunk(section.x, section.z), section.y, section.y)
                assertTrue(
                    moves.originsReading(changed, world).contains(origin.x, origin.y, origin.z),
                    "${edge.movement} origin is outside the refresh of the section containing $read",
                )
                assertTrue(
                    moves.targetsReading(changed, world).contains(edge.to.x, edge.to.y, edge.to.z),
                    "${edge.movement} target is outside the refresh of the section containing $read",
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


    private fun SimpleMoveLibrary.edgesTo(view: CoarseVoxelView, target: Stance): List<CoarseEdge> {
        val cache = CoarseEdgeCache(view, this)
        return templates(view, target).mapNotNull { template ->
            cache.edgesFrom(target.offset(-template.dx, -template.dy, -template.dz)).firstOrNull { it.template == template.id }
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
