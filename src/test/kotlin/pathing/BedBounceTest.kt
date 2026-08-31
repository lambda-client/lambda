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
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Beds, which bounce like weak slime with three quirks that decide parkour over them:
 * the reflection is 0.66 rather than total, the landing tick still counts as GROUNDED
 * (so a held jump on the next tick overrides the rebound -- the field technique), and
 * sneaking through the touchdown suppresses the bounce entirely. The simulator refused
 * to model any of this and every certified tape across a bed diverged at the landing.
 */
class BedBounceTest {
    private val bed = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0),
        bounceFactor = SnapshotSimulationEnvironment.BED_BOUNCE_FACTOR,
    )

    private fun bedFloor(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(BlockPos(x, 99, z), bed)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
    }

    private fun simulator(environment: SnapshotSimulationEnvironment) = MovementSimulator(
        profile = PROFILE,
        environment = environment,
        initialState = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 100.0, 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        ),
    )

    @Test
    fun `landing on a bed bounces two thirds and still counts as grounded`() {
        val sim = simulator(bedFloor())
        var impact = 0.0
        var reflected = 0.0
        var groundedOnBounce = false
        repeat(16) { frame ->
            val before = sim.state.velocity.y
            val state = sim.tickMovement(
                MovementSimulationInput(forward = 1.0, sprint = true, jump = frame == 2, rotation = Rotation(0.0, 0.0)),
            ).simulator.state
            if (frame > 6 && state.velocity.y > 0.0 && reflected == 0.0) {
                impact = before
                reflected = state.velocity.y
                groundedOnBounce = state.onGround
            }
        }
        assertTrue(reflected > 0.0, "the bed must reflect the fall")
        // The reflection happens mid-tick, so the observed velocity carries the same
        // tick's gravity and drag: ((-impact * 0.66) - g) * 0.98. The field log's
        // observed rebound (0.2542683097376668) matches this to the last digit.
        val expected = ((-impact) * SnapshotSimulationEnvironment.BED_BOUNCE_FACTOR - PROFILE.gravity) * 0.98
        // 1e-6, not tighter: vanilla mixes float32 into the drag chain, and the
        // scalar recomputation here differs in association order at the ninth digit.
        assertTrue(
            abs(reflected - expected) < 1e-6,
            "a bed reflects two thirds: impact=$impact reflected=$reflected expected=$expected",
        )
        assertTrue(groundedOnBounce, "the bounce tick still counts as grounded -- a held jump overrides it")
    }

    @Test
    fun `sneaking through the touchdown settles instead of bouncing`() {
        val sim = simulator(bedFloor())
        var settled = false
        repeat(20) { frame ->
            val state = sim.tickMovement(
                MovementSimulationInput(
                    forward = if (frame < 6) 1.0 else 0.0,
                    sprint = frame < 6,
                    jump = frame == 2,
                    sneak = frame >= 6,
                    rotation = Rotation(0.0, 0.0),
                ),
            ).simulator.state
            if (frame > 8 && state.onGround && state.velocity.y <= 0.0) settled = true
            assertTrue(frame <= 8 || state.velocity.y <= 0.01, "sneaked landing must not rebound, vy=${state.velocity.y} at $frame")
        }
        assertTrue(settled, "the sneaked landing must settle on the bed")
    }

    @Test
    fun `a held jump converts the rebound into a real jump`() {
        val sim = simulator(bedFloor())
        var jumped = false
        var bounced = false
        repeat(24) { frame ->
            val state = sim.tickMovement(
                MovementSimulationInput(
                    forward = 1.0,
                    sprint = frame < 6,
                    // Jump held from before the landing: the landing tick bounces, the
                    // next tick is still grounded and the held key fires a REAL jump.
                    jump = frame == 2 || frame >= 8,
                    rotation = Rotation(0.0, 0.0),
                ),
            ).simulator.state
            // A rebound off this fall reads ~0.25 at tick end; a real jump reads
            // (0.42 - g) * 0.98 = 0.3332. The gap between them is the assertion.
            if (frame > 6 && state.velocity.y in 0.1..0.3) bounced = true
            if (frame > 6 && abs(state.velocity.y - (0.42 - PROFILE.gravity) * 0.98) < 0.01) jumped = true
        }
        assertTrue(bounced, "the un-sneaked landing must rebound first")
        assertTrue(jumped, "the held jump must override the rebound with a full jump")
    }

    /**
     * The parkour section, end to end: two bed platforms with a gap, crossable only by
     * jumping -- which means jumping FROM a bed the body just landed on bouncing. The
     * search has to find the timing (a held jump on the grounded rebound tick, or a
     * settle) on its own; what this pins is that the simulator prices the bed honestly
     * enough for SOME certified tape to exist.
     */
    @Test
    fun `a gap between bed platforms is jumped and certified`() {
        val blocks = buildMap {
            for (x in -1..1) {
                for (z in 0..1) put(BlockPos(x, 9, z), bed)
                for (z in 4..5) put(BlockPos(x, 9, z), bed)
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks,
        )
        val moves = SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(),
        )
        val start = Stance(0, 10, 0)
        val goal = Stance(0, 10, 5)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 60.0, timeBudget = Duration.INFINITE, maxExpansions = 40_000)
        val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 9.5625, 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
        assertTrue(!path.partial, "the walk must arrive on the far bed platform")
        val terminal = path.plan.frames.last().state.position
        assertTrue(
            terminal.z > 4.4 && abs(terminal.y - 9.5625) < 0.01,
            "the tape must end standing on the far beds, ended at $terminal",
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
