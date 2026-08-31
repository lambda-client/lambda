/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

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
        val moves = SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(allowSlimeBounces = true),
        )
        return CoarsePlanner(environment, moves, Stance(0, 10, 0), Stance(0, 8, 9))
    }

    @Test
    fun `a slime pit is crossed by a bounce edge`() {
        val planner = planner(world(slime))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "the pit is only crossable by rebound; got ${route.edges.map { it.movement }}",
        )
    }

    @Test
    fun `a carpet over the slime still routes the bounce`() {
        val planner = planner(world(slime, cover = carpet))
        assertTrue(planner.repair(Duration.INFINITE).converged)
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
        val moves = SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(allowSlimeBounces = true),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 10, 0), Stance(0, 8, 7))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "drop 4 / land +2 at span 5 must route as a bounce; got ${route.edges.map { it.movement }}",
        )
    }

    @Test
    fun `a carpet over stone does not invent a bounce`() {
        val planner = planner(world(SnapshotBlockPhysics.FULL_CUBE, cover = carpet))
        planner.repair(Duration.INFINITE)
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
        certifyArrival(environment, goal = Stance(0, 9, 7), start = Stance(0, 11, 0))
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

    private fun certifyArrival(
        environment: SnapshotSimulationEnvironment,
        goal: Stance,
        start: Stance = Stance(0, 10, 0),
    ) {
        val moves = SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(allowSlimeBounces = true),
        )
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 60.0, timeBudget = Duration.INFINITE, maxExpansions = 40_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
        assertTrue(
            route.edges.any { it.movement == MovementId.BOUNCE },
            "the pit is only crossable by rebound; got ${route.edges.map { it.movement }}",
        )

        // Feet sit on the real surfaces: a carpeted start or goal is 0.9375 below its stance.
        val startFeet = start.y + environment.surfaceOffset(start.x, start.y - 1, start.z)
        val goalFeet = goal.y + environment.surfaceOffset(goal.x, goal.y - 1, goal.z)
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, startFeet, start.z + 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive on the far platform")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.y > goalFeet - 0.01 && terminal.z > goal.z - 0.6,
            "the tape must end on the far platform, ended at $terminal",
        )
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
