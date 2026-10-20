/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PaneBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.StairsBlock
import net.minecraft.block.WallBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.SlabType
import net.minecraft.block.enums.WallShape
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The bounce-template path at COARSE level, end to end: a pillar, a slime pit four
 * deep, and a landing platform reachable by nothing but the rebound. The route must
 * exist, and it must exist just the same when the classic parkour disguise -- a
 * carpet laid over the slime -- hides the pad, because the game's landing probe
 * reaches 0.2 below the feet and bounces through thin cover.
 */
class SlimeBounceRouteTest {
    private val slime = SnapshotBlockPhysics.of(
        VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true,
    )
    private val carpet = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0),
    )

    private fun world(
        pad: SnapshotBlockPhysics,
        cover: SnapshotBlockPhysics? = null,
    ): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            // Launch pillar: stance (0, 10, 0).
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            // The pit floor at y=4: bare contact stance y=5 (drop 5); with a carpet
            // the contact stance is y=6 (drop 4). Both leave the pit too deep for a
            // walk-off from the pillar and too far below the platform to jump out.
            for (x in -1..1) for (z in 1..5) {
                put(BlockPos(x, 4, z), pad)
                cover?.let { put(BlockPos(x, 5, z), it) }
            }
            // Landing platform at stance y=8: 2 below the lip. Only the rebound gets there.
            for (x in -1..1) for (z in 6..11) put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
    }

    private fun planner(environment: SnapshotSimulationEnvironment): CoarsePlanner {
        val moves = moveLibrary(SimpleMoveOptions(allowSlimeBounces = true))
        return CoarsePlanner(environment, moves, Stance(0, 10, 0), Stance(0, 8, 9))
    }

    @Test
    fun `a slime pit is crossed by a bounce edge`() {
        val planner = planner(world(slime))
        assertTrue(planner.repair().converged)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "the pit is only crossable by rebound; got ${route.edges.map { it.movement }}",
        )
    }

    @Test
    fun `a carpet over the slime still routes the bounce`() {
        val planner = planner(world(slime, cover = carpet))
        assertTrue(planner.repair().converged)
        val route = checkNotNull(planner.routePlan(0L)) {
            "carpet over slime must still bounce: ${planner.routeFailureReport()}"
        }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "the pit is only crossable by rebound; got ${route.edges.map { it.movement }}",
        )
    }

    /**
     * The field jump this pins down (Dragon Trail): 4 down and 3 forward onto the
     * slime, rebounding 2 up and 2 forward -- drop 4, rise -2, span 5. The probe
     * always solved it (as a gentle walk-off launch); the template window's floor was
     * computed from a SPRINT standing launch and silently excluded every softer
     * bounce below it, so the jump was never offered.
     */
    @Test
    fun `a gentle rebound below the sprint window is still offered`() {
        val blocks = buildMap {
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            // Slime around the contact point three blocks out, four below the lip:
            // contact stance y=6, slime cell y=5.
            for (x in -1..1) for (z in 2..4) put(BlockPos(x, 5, z), slime)
            // Landing platform: stance y=8 from z=5 -- two up from contact, two on.
            for (x in -1..1) for (z in 5..9) put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        val moves = moveLibrary(SimpleMoveOptions(allowSlimeBounces = true, allowDeepDropJumps = false))
        val planner = CoarsePlanner(environment, moves, Stance(0, 10, 0), Stance(0, 8, 7))
        assertTrue(planner.repair().converged)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "drop 4 / land +2 at span 5 must route as a bounce; got ${route.edges.map { it.movement }}",
        )
    }

    /**
     * Without slime anywhere in the snapshot the search estimates with ground moves only,
     * which is twice as tight as the bounce reach. A pad streaming in afterwards must swap
     * the estimate back before the new bounce edge can be trusted.
     */
    @Test
    fun `slime streamed in after a slime free search still routes the bounce`() {
        val environment = world(SnapshotBlockPhysics.FULL_CUBE)
        val planner = planner(environment)
        planner.repair()
        assertNull(planner.routePlan(0L), "a stone pit floor must not route")

        val sections = HashMap<Long, ImmutableSnapshotSection.Builder>()
        world(slime).forEachInstalledBlock { pos, physics ->
            sections.getOrPut(ChunkSectionPos.toLong(BlockPos.fromLong(pos))) { ImmutableSnapshotSection.Builder() }
                .set(BlockPos.unpackLongX(pos), BlockPos.unpackLongY(pos), BlockPos.unpackLongZ(pos), physics)
        }
        sections.forEach { (key, section) -> environment.install(key, section.build()) }
        planner.chunksChanged(setOf(PathingChunk(-1, 0), PathingChunk(0, 0)))

        assertTrue(planner.repair().converged)
        val route = checkNotNull(planner.routePlan(1L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "the streamed pad is only crossable by rebound; got ${route.edges.map { it.movement }}",
        )
    }

    @Test
    fun `a carpet over stone does not invent a bounce`() {
        val planner = planner(world(SnapshotBlockPhysics.FULL_CUBE, cover = carpet))
        planner.repair()
        assertNull(planner.routePlan(0L), "stone under carpet must not bounce")
    }

    /**
     * The coarse route is not the tape: everything above proves the bounce EDGE
     * exists, none of it proves the rollout certifies it. This walks the horizon on
     * the bare-slime pit exactly like the field does and demands the tape actually
     * arrives on the far platform.
     */
    @Test
    fun `the bounce is certified end to end`() {
        certifyArrival(world(slime), goal = Stance(0, 8, 9))
    }

    @Test
    fun `the carpeted bounce is certified end to end`() {
        certifyArrival(world(slime, cover = carpet), goal = Stance(0, 8, 9))
    }

    /**
     * The field case a walk-off can never serve: drop 4 with the landing ONE below
     * the lip (three above the slime). The walk-off rebound tops out two below the
     * lip; only jumping off the edge buys the extra impact speed. Certified end to
     * end so the jump trigger in the program is proven, not just the solver line.
     */
    @Test
    fun `a jump launch lands one below the lip and is certified end to end`() {
        val blocks = buildMap {
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            for (x in -1..1) for (z in 2..4) put(BlockPos(x, 5, z), slime)
            // Landing platform one below the lip: stance y=9 from z=6, span 6.
            for (x in -1..1) for (z in 6..10) put(BlockPos(x, 8, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 9, 8))
    }

    /**
     * The full parkour disguise: carpet on the LAUNCH lip, the pit, and the LANDING
     * platform. The launch feet sit 0.9375 below the launch stance and the landing
     * feet 0.9375 below theirs -- both offsets must flow through launchHeight and
     * riseHeight or the arc is solved a block wrong at each end.
     */
    @Test
    fun `a fully carpeted course still bounces and certifies`() {
        val blocks = buildMap {
            // Launch: full cube at y=9 with carpet at y=10 -- launch stance (0, 11, 0).
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(0, 10, 0), carpet)
            // Pit: slime at y=5 under carpet at y=6, contact stance y=7 (drop 4 from
            // stance 11; real fall 11-0.9375 to 6.0625 = 4.0).
            for (x in -1..1) for (z in 2..4) {
                put(BlockPos(x, 5, z), slime)
                put(BlockPos(x, 6, z), carpet)
            }
            // Landing: full cubes at y=7 with carpet at y=8 -- stance y=9, rise -2.
            for (x in -1..1) for (z in 5..9) {
                put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
                put(BlockPos(x, 8, z), carpet)
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(
            environment, goal = Stance(0, 9, 7), start = Stance(0, 11, 0),
            options = SimpleMoveOptions(allowSlimeBounces = true, allowDeepDropJumps = false),
        )
    }

    /**
     * The online course that field-failed (Dragon Trail successor, dump
     * plan-1788171172560 translated): a SINGLE carpet-covered slime cell in a pit
     * whose stone floor tops out level with the pad. The old one-cell contact slack
     * accepted arcs whose trough landed on the stone BESIDE the pad -- bouncy within
     * slack, harmful fall in execution -- and the contact refinement had a second
     * fixed point on that stone (offset 0). The contact must aim through the pad
     * cell itself.
     */
    @Test
    fun `a one-cell carpeted pad in a level pit is hit through the middle`() {
        val blocks = buildMap {
            put(BlockPos(0, 10, 0), SnapshotBlockPhysics.FULL_CUBE)
            // Pit floor at y=6 (stance 7, four below the launch feet at 11), with the
            // single pad: slime under carpet, carpet top a sixteenth above the floor.
            for (x in -1..1) for (z in 1..6) put(BlockPos(x, 6, z), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(0, 6, 4), slime)
            put(BlockPos(0, 7, 4), carpet)
            // Landing platform two below the launch feet, span 7 -- the dump's exact spans.
            for (x in -1..1) for (z in 7..12) put(BlockPos(x, 8, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 9, 9), start = Stance(0, 11, 0))
    }

    /**
     * The field's deep ceiling-grazing bounce: launch under a three-block ceiling
     * (the jump apex would poke 0.05 into it), a single carpeted pad six below,
     * and a far ledge whose own LOWER ceiling hangs four blocks over the descending
     * body. The solver must clamp the ascent at the ceiling (vanilla's rising head
     * collision) rather than refuse the arc -- and must scope that clamp to the
     * LAUNCH ascent, or the landing's lower ceiling buries the jump entirely. The
     * landing itself is a corner catch 0.53 past the cell centre: inside the
     * physical support reach, outside the old +-0.5 window.
     */
    @Test
    fun `a ceiling-grazing bounce is clamped, not refused`() {
        val blocks = buildMap {
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            // Ceiling: bottom at y=13, three blocks over the launch feet, spanning
            // the whole corridor; a lower shelf at y=12 hangs over the landing.
            for (x in -1..1) for (z in -1..12) put(BlockPos(x, 13, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in -1..1) for (z in 9..12) put(BlockPos(x, 12, z), SnapshotBlockPhysics.FULL_CUBE)
            // Single carpeted pad, stance drop 5 (real 5.94).
            put(BlockPos(0, 3, 6), slime)
            put(BlockPos(0, 4, 6), carpet)
            // Landing ledge at stance y=6 (rise -4), starting at span 11.
            for (x in -1..1) for (z in 11..12) put(BlockPos(x, 5, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 32), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 6, 11))
    }

    /**
     * The field's fence-launched bounce: standing on a FENCE (feet half a block
     * proud of the stance grid, a quarter-block post to creep on), three air cells
     * to a slime row whose top is 2.5 below the feet, one more gap, landing two
     * above the slime. Stance ints read drop 3 / rise -1 / spans 4+2; the real
     * fall is 2.5 and the real landing 0.5 below the feet. Easy by hand from
     * standing -- the launch surface offset and the post's tiny creep room must
     * both flow through the standing-start solve.
     */
    @Test
    fun `a fence launched bounce is certified end to end`() {
        val fence = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
            fenceLike = true,
        )
        val blocks = buildMap {
            put(BlockPos(0, 8, 0), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(0, 9, 0), fence)
            // Slime row at z=4: top 8.0, contact stance y=8, three air cells before it.
            for (x in -1..1) put(BlockPos(x, 7, 4), slime)
            // Landing at stance y=10 (top 10.0): two above the slime, half below the feet.
            for (x in -1..1) for (z in 6..10) put(BlockPos(x, 9, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 10, 8), start = Stance(0, 11, 0))
    }

    /**
     * The field's exact fence-bounce shape (dump plan-1788192764076, translated):
     * fence launch (feet half a block proud), a CARPETED slime pad level with the
     * pit floor, and a two-high wall right behind it. The carpet lifts the contact
     * stance a block, so the whole jump reads STANCE DROP 2 (real fall 2.44) --
     * below the old MIN_DROP=3, structurally unofferable no matter what the
     * physics said. The route must bounce and the walk must certify.
     */
    @Test
    fun `a stance drop two carpeted bounce off a fence is certified end to end`() {
        val fence = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
            fenceLike = true,
        )
        val blocks = buildMap {
            // Launch: block at y=7 carrying the fence at y=8 -- stance (0,10,0), feet 9.5.
            put(BlockPos(0, 7, 0), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(0, 8, 0), fence)
            // Pit floor at y=6 (top 7), the slime replacing one floor cell, carpet on it:
            // contact stance (0,8,4), feet 7.0625 -- stance drop 2, real fall 2.4375.
            for (x in -1..1) for (z in 1..5) put(BlockPos(x, 6, z), SnapshotBlockPhysics.FULL_CUBE)
            put(BlockPos(0, 6, 4), slime)
            put(BlockPos(0, 7, 4), carpet)
            // The wall/landing: two high off the pit floor, top 9.0 -- stance y=9, rise -1.
            for (x in -1..1) for (z in 6..9) {
                put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
                put(BlockPos(x, 8, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 9, 8), start = Stance(0, 10, 0))
    }

    /**
     * The cylonemc gazebo (dump plan-1791738884955, mirrored in z): from a ledge, six down onto a two by two
     * slime pad in a stone floor and up onto the roof through the gap between two wall posts in its railing,
     * eight on and five across. No compass ray from the ledge crosses the pad and threads the gap, so the
     * bounce has to be offered off the eight directions.
     */
    @Test
    fun `an off-axis bounce threads a railing gap and is certified end to end`() {
        MinecraftBootstrap.ensure()
        val stone = Blocks.STONE.defaultState
        val states = HashMap<BlockPos, BlockState>()
        states[BlockPos(0, 9, 0)] = stone
        for (x in -2..10) for (z in 1..7) states[BlockPos(x, 3, z)] = stone
        for (x in 3..4) for (z in 4..5) states[BlockPos(x, 3, z)] = Blocks.SLIME_BLOCK.defaultState
        for (x in -2..9) for (z in 8..12) states[BlockPos(x, 7, z)] = stone
        for (x in listOf(0, 2, 4, 6, 8)) for (y in 4..6) states[BlockPos(x, y, 8)] = Blocks.OAK_FENCE.defaultState
        val double = Blocks.OAK_SLAB.defaultState.with(SlabBlock.TYPE, SlabType.DOUBLE)
        val bottom = Blocks.OAK_SLAB.defaultState
        val wall = Blocks.COBBLESTONE_WALL.defaultState.with(WallBlock.UP, true)
        for (x in -2..0) states[BlockPos(x, 8, 8)] = stone
        states[BlockPos(1, 8, 8)] = double
        states[BlockPos(2, 8, 8)] = bottom
        states[BlockPos(3, 8, 8)] = double
        states[BlockPos(4, 8, 8)] = wall.with(WallBlock.WEST_WALL_SHAPE, WallShape.LOW)
        states[BlockPos(6, 8, 8)] = wall.with(WallBlock.EAST_WALL_SHAPE, WallShape.LOW)
        states[BlockPos(7, 8, 8)] = double
        states[BlockPos(8, 8, 8)] = bottom
        states[BlockPos(9, 8, 8)] = double
        states[BlockPos(3, 9, 8)] = wall
        states[BlockPos(7, 9, 8)] = wall
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16),
            states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
        )
        val compassOnly = CoarsePlanner(
            environment, moveLibrary(SimpleMoveOptions(allowSlimeBounces = true, allowOffAxisJumps = false)), Stance(0, 10, 0), Stance(5, 8, 9),
        )
        compassOnly.repair()
        assertNull(compassOnly.routePlan(0L), "no compass ray crosses the pad and the gap")
        certifyArrival(environment, goal = Stance(5, 8, 9))
    }

    /** The six-cell pit (drop 4, span 7): beyond every walk-off window, jump range. */
    @Test
    fun `a six cell pit is crossed by a jump launch and certified end to end`() {
        val blocks = buildMap {
            put(BlockPos(0, 9, 0), SnapshotBlockPhysics.FULL_CUBE)
            for (x in -1..1) for (z in 1..6) put(BlockPos(x, 5, z), slime)
            // Landing two below the lip: stance y=8 from z=7, span 7.
            for (x in -1..1) for (z in 7..12) put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        certifyArrival(environment, goal = Stance(0, 8, 10))
    }

    /**
     * Two slime pads in a row with the landing beyond the second (the CycloneMC nether course, translated):
     * one rebound cannot reach the slab ledge and the second pad is no landing, so only a bounce that
     * touches both pads crosses -- a sprint jump held through both rebounds.
     */
    @Test
    fun `two pads in a row are crossed by one double bounce and certified end to end`() {
        MinecraftBootstrap.ensure()
        val states = HashMap<BlockPos, BlockState>()
        for (z in -1..0) states[BlockPos(0, 9, z)] = Blocks.NETHER_BRICKS.defaultState
        for (x in -1..0) for (z in listOf(5, 6, 10, 11)) states[BlockPos(x, 5, z)] = Blocks.SLIME_BLOCK.defaultState
        for (x in -1..0) for (z in 13..16) {
            states[BlockPos(x, 6, z)] = Blocks.NETHER_BRICKS.defaultState
            states[BlockPos(x, 7, z)] = Blocks.NETHER_BRICK_SLAB.defaultState
        }
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 32),
            states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
        )
        certifyArrival(environment, goal = Stance(0, 8, 15))
    }

    /**
     * The CycloneMC stair bounce, translated: four below the take-off, the rebound peaks 0.009 above the lower step
     * of a stair on a wall beyond the slime and never reaches the stair's top. The bounce holds forward into the wall
     * while it rises and slips onto the step at the apex.
     */
    @Test
    fun `a rebound that peaks just above a stair step presses into the wall and is certified end to end`() {
        MinecraftBootstrap.ensure()
        val stone = Blocks.STONE.defaultState
        val stair = Blocks.SMOOTH_RED_SANDSTONE_STAIRS.defaultState.with(StairsBlock.FACING, Direction.SOUTH)
        val states = HashMap<BlockPos, BlockState>()
        for (x in 0..1) {
            for (z in -3..0) states[BlockPos(x, 9, z)] = stone
            for (z in 1..4) states[BlockPos(x, 5, z)] = stone
            for (z in 5..6) states[BlockPos(x, 5, z)] = Blocks.SLIME_BLOCK.defaultState
            for (y in 6..8) states[BlockPos(x, y, 7)] = stone
            states[BlockPos(x, 9, 7)] = stair
            for (z in 8..9) states[BlockPos(x, 9, z)] = stone
        }
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16),
            states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
        )
        certifyArrival(environment, goal = Stance(0, 10, 8))
    }

    /**
     * The CycloneMC end corridor, translated: three slime pads over a void, four below the take-off and each one block
     * further aside than a compass line, then a slab ledge. No template covers three contacts off the compass, so the
     * chain is found from the pads themselves and verified on the line from the take-off to the ledge.
     */
    @Test
    fun `three pads off the compass are crossed by one chain of bounces and certified end to end`() {
        MinecraftBootstrap.ensure()
        val states = HashMap<BlockPos, BlockState>()
        for (z in -4..0) states[BlockPos(0, 9, z)] = Blocks.STONE.defaultState
        for ((x, z) in listOf(-2 to 5, -3 to 10, -4 to 14)) for (dx in 0..1) for (dz in 0..1) states[BlockPos(x + dx, 5, z + dz)] = Blocks.SLIME_BLOCK.defaultState
        for (x in -4..-2) for (z in 17..19) states[BlockPos(x, 7, z)] = Blocks.OAK_SLAB.defaultState
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 32),
            states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
        )
        certifyArrival(environment, goal = Stance(-3, 8, 18), contacts = 3)
    }

    /**
     * The CycloneMC end shaft, translated: off a slab, 16.5 blocks down through a gap in a ceiling onto slime, and the
     * rebound bonks that ceiling before it lands on a platform beside the pad. Deeper than any template; the chain scan
     * finds it as a single contact with the ceiling capping the rebound.
     */
    @Test
    fun `a deep bounce under a ceiling is found from the pad and certified end to end`() {
        MinecraftBootstrap.ensure()
        val states = HashMap<BlockPos, BlockState>()
        states[BlockPos(0, 24, 0)] = Blocks.PURPUR_SLAB.defaultState
        for (z in -1..1) {
            states[BlockPos(0, 18, z)] = Blocks.STONE.defaultState
            for (x in 5..10) states[BlockPos(x, 18, z)] = Blocks.STONE.defaultState
            for (x in 8..11) states[BlockPos(x, 14, z)] = Blocks.STONE.defaultState
        }
        for (x in 4..6) for (z in -1..0) states[BlockPos(x, 7, z)] = Blocks.SLIME_BLOCK.defaultState
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 40, 16),
            states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
        )
        certifyArrival(environment, goal = Stance(9, 15, 0), start = Stance(0, 25, 0), options = SimpleMoveOptions(allowSlimeBounces = true, maxBounceDrop = 24))
    }

    /**
     * CycloneMC Pride Tower, checkpoint 7 to 8, translated: off a 2x2 platform onto a carpeted pad four below, on to a
     * second carpeted pad one block lower still, and up onto a platform against the tower wall. Neither pad is a
     * landing, so only one bounce touching both pads, each at its own height, crosses.
     */
    @Test
    fun `two pads at different heights are crossed by one double bounce and certified end to end`() {
        MinecraftBootstrap.ensure()
        val copper = Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB.defaultState.with(SlabBlock.TYPE, SlabType.TOP)
        val terracotta = Blocks.LIME_GLAZED_TERRACOTTA.defaultState
        val states = HashMap<BlockPos, BlockState>()
        for (x in -1..0) for (z in -1..0) {
            states[BlockPos(x, 8, z)] = copper
            states[BlockPos(x, 9, z)] = terracotta
        }
        for (x in 1..2) for (z in 4..5) {
            states[BlockPos(x, 4, z)] = copper
            states[BlockPos(x, 5, z)] = Blocks.SLIME_BLOCK.defaultState
            states[BlockPos(x, 6, z)] = Blocks.LIME_CARPET.defaultState
        }
        for (x in 2..3) for (z in 10..11) {
            states[BlockPos(x, 4, z)] = Blocks.SLIME_BLOCK.defaultState
            states[BlockPos(x, 5, z)] = Blocks.LIME_CARPET.defaultState
        }
        for (x in 3..4) for (z in 14..15) {
            states[BlockPos(x, 5, z)] = copper
            states[BlockPos(x, 6, z)] = terracotta
        }
        for (y in 4..11) for (z in 14..16) states[BlockPos(5, y, z)] = Blocks.LIME_CONCRETE.defaultState
        certifyArrival(sampled(states, SimulationSnapshotBounds(-16, 0, -16, 16, 30, 32)), goal = Stance(3, 7, 14), contacts = 2)
    }

    /**
     * CycloneMC Pride Tower checkpoint 8, translated: from a post of glass panes around a block, a sprint jump onto a
     * carpeted pad in a corner of the tower wall; the platform to land on lies beside the pad, behind a wall of glass
     * panes, so the rebound has to turn off the line it came in on and round the panes onto the platform.
     */
    @Test
    fun `a rebound turned off its approach line onto a platform beside the pad is certified end to end`() {
        MinecraftBootstrap.ensure()
        val copper = Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB.defaultState.with(SlabBlock.TYPE, SlabType.TOP)
        val pane = Blocks.LIME_STAINED_GLASS_PANE.defaultState
        val states = HashMap<BlockPos, BlockState>()
        states[BlockPos(6, 9, 3)] = Blocks.BLACK_CONCRETE.defaultState
        states[BlockPos(5, 9, 3)] = Blocks.WHITE_STAINED_GLASS_PANE.defaultState.with(PaneBlock.EAST, true)
        states[BlockPos(7, 9, 3)] = Blocks.WHITE_STAINED_GLASS_PANE.defaultState.with(PaneBlock.WEST, true)
        for (x in 0..1) for (z in 2..3) {
            states[BlockPos(x, 5, z)] = copper
            states[BlockPos(x, 6, z)] = Blocks.SLIME_BLOCK.defaultState
            states[BlockPos(x, 7, z)] = Blocks.LIME_CARPET.defaultState
        }
        for (x in 0..1) for (z in 0..1) {
            states[BlockPos(x, 8, z)] = copper
            states[BlockPos(x, 9, z)] = Blocks.LIME_GLAZED_TERRACOTTA.defaultState
        }
        for (y in 10..12) {
            states[BlockPos(2, y, 0)] = pane.with(PaneBlock.SOUTH, true)
            states[BlockPos(2, y, 1)] = pane.with(PaneBlock.NORTH, true).with(PaneBlock.SOUTH, true)
            states[BlockPos(2, y, 2)] = pane.with(PaneBlock.NORTH, true)
        }
        for (y in 3..13) {
            for (z in 1..3) states[BlockPos(-1, y, z)] = Blocks.LIME_CONCRETE.defaultState
            for (z in -2..0) states[BlockPos(-2, y, z)] = Blocks.LIME_CONCRETE.defaultState
            if (y >= 5) for (z in 4..5) states[BlockPos(0, y, z)] = Blocks.LIME_CONCRETE.defaultState
            states[BlockPos(1, y, 6)] = Blocks.LIME_CONCRETE.defaultState
        }
        certifyArrival(
            sampled(states, SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16)),
            goal = Stance(0, 10, 1),
            start = Stance(6, 10, 3),
            arrived = { it.y > 9.99 && it.x in -0.3..2.0 && it.z in -0.3..1.95 },
        )
    }

    private fun sampled(states: Map<BlockPos, BlockState>, bounds: SimulationSnapshotBounds): SnapshotSimulationEnvironment {
        val view = object : BlockView {
            override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
            override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getHeight(): Int = 384
            override fun getBottomY(): Int = -64
        }
        val sampler = BlockPhysicsSampler(view)
        return SnapshotSimulationEnvironment.synthetic(bounds, states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) })
    }

    private fun certifyArrival(
        environment: SnapshotSimulationEnvironment,
        goal: Stance,
        start: Stance = Stance(0, 10, 0),
        contacts: Int = 1,
        options: SimpleMoveOptions = SimpleMoveOptions(allowSlimeBounces = true),
        arrived: ((Vec3d) -> Boolean)? = null,
    ) {
        val moves = moveLibrary(options)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair().converged)
        planner.expandField(extraTicks = 60.0, timeBudget = Duration.INFINITE, maxExpansions = 40_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE && it.bounce!!.contactDistances.size >= contacts },
            "the pit is only crossable by a rebound touching $contacts pads; got ${route.edges.map { "${it.movement}/${it.bounce?.contactDistances?.size}" }}",
        )

        // Feet sit on the real surfaces: a carpeted start or goal is 0.9375 below its stance.
        val startFeet = start.y + environment.surfaceOffset(start.x, start.y - 1, start.z)
        val goalFeet = goal.y + environment.surfaceOffset(goal.x, goal.y - 1, goal.z)
        val initial = resting(Vec3d(start.x + 0.5, startFeet, start.z + 0.5))
        val outcome = walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive on the far platform")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            arrived?.invoke(terminal) ?: (terminal.y > goalFeet - 0.01 && terminal.z > goal.z - 0.6),
            "the tape must end on the far platform, ended at $terminal",
        )
    }

}
