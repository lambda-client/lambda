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
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.launch.momentumSpeed
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The solver's claims, checked against the simulator they are claims about.
 *
 * [LaunchSolver] says "leave the ground here, at this speed, and you will land there".
 * That is a falsifiable statement, and the authority on it is
 * [MovementSimulator] -- so every case here solves a launch, puts a body at the solved
 * take-off with the solved velocity, presses the keys the solution implies, and demands
 * that the body actually arrives. Arithmetic that cannot survive this is not a solver.
 */
class LaunchSolverTest {
    @Test
    fun `the arc recurrence reproduces the vanilla constants it replaces`() {
        val profile = BallisticProfile.VANILLA

        // The per-tick displacement figures the old hand-tuned tables were built around.
        assertEquals(0.2806, profile.cruiseDisplacement(sprint = true), 1e-4)
        assertEquals(0.2159, profile.cruiseDisplacement(sprint = false), 1e-4)

        // The canonical vanilla jump height.
        val jump = assertNotNull(profile.fly(LaunchMode.SPRINT_JUMP, profile.cruiseSpeed(true), rise = 0.0))
        assertEquals(1.2522, jump.apex, 1e-4)
    }

    /**
     * The property the whole inversion rests on.
     *
     * If distance were not affine in take-off speed the solver would have to search for a
     * speed instead of computing one, and the launch window it reports would be a guess.
     */
    @Test
    fun `flight distance is affine in take-off speed, so the inversion is exact`() {
        val profile = BallisticProfile.VANILLA
        for (rise in -3..1) {
            val base = assertNotNull(profile.fly(LaunchMode.SPRINT_JUMP, 0.0, rise.toDouble()))
            val unit = assertNotNull(profile.fly(LaunchMode.SPRINT_JUMP, 1.0, rise.toDouble()))
            val slope = unit.distance - base.distance

            for (speed in listOf(0.05, 0.12, 0.2, 0.28)) {
                val sample = assertNotNull(profile.fly(LaunchMode.SPRINT_JUMP, speed, rise.toDouble()))
                assertEquals(base.distance + slope * speed, sample.distance, 1e-9,
                    "distance must be affine in entry speed at rise $rise")
                assertEquals(base.airTicks, sample.airTicks,
                    "air ticks must not depend on horizontal speed at rise $rise")
            }
        }
    }

    @Test
    fun `a solved flat jump lands where it says it will`() {
        for (span in 2..4) {
            val solution = assertNotNull(
                LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, span), modes = JUMPS),
                "a span-$span flat jump must be solvable",
            )
            assertLands(solution, span = span, rise = 0)
        }
    }

    @Test
    fun `a solved rising jump lands where it says it will`() {
        for (span in 2..3) {
            val solution = assertNotNull(
                LaunchSolver.best(Stance(0, 64, 0), Stance(0, 65, span), modes = JUMPS),
                "a span-$span rise-1 jump must be solvable",
            )
            assertLands(solution, span = span, rise = 1)
        }
    }

    /**
     * Gait is not a free choice the search should enumerate; it is part of the answer.
     *
     * A sprint jump covers 2.88 blocks even from a standstill, so it flies clean over a
     * two-block gap. Choosing the gait from the geometry is the whole difference between
     * solving a launch and trying every combination until one sticks.
     */
    @Test
    fun `a short gap selects a walking jump and a long one selects a sprint`() {
        val short = assertNotNull(LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 2), modes = JUMPS))
        val long = assertNotNull(LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 4), modes = JUMPS))
        assertEquals(LaunchMode.WALK_JUMP, short.mode, "a span-2 gap must not be sprint-jumped")
        assertEquals(LaunchMode.SPRINT_JUMP, long.mode, "a span-4 gap is beyond any walking jump")
    }

    /** A jump cannot reach past its own apex, whatever it is doing horizontally. */
    @Test
    fun `nothing is offered onto a landing two blocks up`() {
        assertNull(LaunchSolver.best(Stance(0, 64, 0), Stance(0, 66, 2)))
    }

    @Test
    fun `a solved drop lands where it says it will, without ever pressing jump`() {
        for (depth in 1..3) {
            for (span in 1..2) {
                val solution = LaunchSolver.best(
                    Stance(0, 64, 0), Stance(0, 64 - depth, span),
                    modes = listOf(LaunchMode.WALK_DROP, LaunchMode.SPRINT_DROP),
                ) ?: continue
                assertTrue(solution.mode.drops, "a drop solution must not jump")
                assertLands(solution, span = span, rise = -depth)
            }
        }
    }

    /**
     * The reason the drop primitive has to exist.
     *
     * Sprinting off a one-block ledge covers about 1.26 blocks, so the body sails past the
     * pad directly below-and-across. Only a controlled leave speed lands on it, and the
     * solver is what knows which speed that is.
     */
    @Test
    fun `a one-block drop onto the adjacent pad needs a slower leave than a sprint`() {
        val profile = BallisticProfile.VANILLA
        val sprintReach = assertNotNull(profile.fly(LaunchMode.SPRINT_DROP, profile.cruiseSpeed(true), -1.0)).distance
        assertTrue(sprintReach > 1.0, "a sprint walk-off must overshoot the adjacent pad ($sprintReach)")

        val solution = assertNotNull(
            LaunchSolver.best(
                Stance(0, 64, 0), Stance(0, 63, 1),
                modes = listOf(LaunchMode.WALK_DROP, LaunchMode.SPRINT_DROP),
            ),
            "a one-block step down must be solvable",
        )
        assertTrue(
            solution.speed < profile.cruiseSpeed(true),
            "landing on the adjacent pad must leave slower than sprint cruise (${solution.speed})",
        )
        assertLands(solution, span = 1, rise = -1)
    }

    @Test
    fun `an unreachable span is refused rather than guessed at`() {
        assertNull(
            LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 7)),
            "seven blocks is beyond every launch mode",
        )
    }

    /** Momentum from a previous jump is what makes chained parkour possible. */
    @Test
    fun `a chained jump may enter faster than ground cruise`() {
        val profile = BallisticProfile.VANILLA
        assertTrue(
            profile.momentumSpeed(sprint = true) > profile.cruiseSpeed(sprint = true) * 1.5,
            "landing from a sprint jump must leave far more speed than ground cruise",
        )
    }

    /**
     * Margin has to mean something, and what it means is fragility rather than distance.
     *
     * Note it is deliberately *not* monotone in span. A short gap can be awkward -- a
     * two-block jump needs the body slower than walking cruise, which is a narrow band --
     * while a comfortable mid-range jump sits in the middle of its. What must always hold
     * is that a launch pinned against the body's top speed, with nowhere to give, ranks
     * below one with room on both sides. That is the ordering the search consumes.
     */
    @Test
    fun `a jump at the edge of reach reports far less margin than a comfortable one`() {
        val comfortable = assertNotNull(LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 3)))
        val desperate = assertNotNull(LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 5)))
        assertTrue(
            comfortable.margin > desperate.margin,
            "a span-3 jump (${comfortable.margin}) must out-rank " +
                "a span-5 jump (${desperate.margin})",
        )
        assertNull(
            LaunchSolver.best(Stance(0, 64, 0), Stance(0, 64, 6), modes = listOf(LaunchMode.SPRINT_JUMP)),
            "a span-6 jump is beyond the body's reach at any entry speed",
        )
    }

    /**
     * Puts a body at the solved take-off with the solved velocity and demands it arrives.
     *
     * The launch runs along +Z so a yaw of zero is the flight heading, which keeps the
     * sprint-jump boost (which reads yaw) on the same axis as the motion.
     */
    private fun assertLands(solution: LaunchSolution, span: Int, rise: Int) {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -2..2) blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -2..2) blocks[BlockPos(x, 63 + rise, span)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 40, -8, 8, 100, span + 8), blocks,
        )

        val simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                // A hair inside the lip, so the body is still supported for the take-off
                // tick the model charges ground acceleration and ground friction for.
                position = Vec3d(0.5, 64.0, 0.5 + solution.launchOffset - LAUNCH_EPSILON),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, solution.speed),
                onGround = true,
                isSprinting = solution.sprint,
            ),
        )

        var landed = false
        var airborne = false
        // A drop stays grounded while it walks to the lip, so "landed" is only meaningful
        // once the body has actually left the ground.
        for (tick in 0..solution.airTicks + LEAVE_TICK_BUDGET) {
            // Fly it exactly as the solution says, control policy included: a drop solved
            // on the coasting arc lands nowhere near its aim if forward is held.
            val forward = if (solution.holdForward) 1.0 else 0.0
            val input = MovementSimulationInput(
                forward = forward,
                sprint = solution.sprint && forward > 0.0,
                jump = solution.jumps && tick == 0,
                rotation = Rotation(0.0, 0.0),
            )
            if (simulator.tryTickMovement(input) !is MovementSimulationStepResult.Advanced) break
            if (!simulator.state.onGround) {
                airborne = true
            } else if (airborne) {
                landed = true
                break
            }
        }

        val state = simulator.state
        assertTrue(landed, "${solution.mode} span=$span rise=$rise never touched down: ${state.position}")
        assertTrue(
            state.position.z + BODY_HALF_WIDTH > span && state.position.z - BODY_HALF_WIDTH < span + 1.0,
            "${solution.mode} span=$span rise=$rise landed at z=${state.position.z} " +
                "with no body overlap on the target cell, " +
                "aiming for ${solution.aimDistance} from the stance centre",
        )
        assertEquals(
            64 + rise, floor(state.position.y + 1e-6).toInt(),
            "${solution.mode} span=$span rise=$rise landed at y=${state.position.y}",
        )
        // The solver's aim is a prediction, not just a target: it should be close.
        assertTrue(
            abs(state.position.z - (0.5 + solution.aimDistance)) < AIM_TOLERANCE,
            "${solution.mode} span=$span rise=$rise predicted z=${0.5 + solution.aimDistance} " +
                "but landed at ${state.position.z}",
        )
    }

    private fun BallisticProfile.cruiseDisplacement(sprint: Boolean): Double =
        cruiseSpeed(sprint) + groundAcceleration(sprint)

    private companion object {
        /** One tick of sprint travel: the arc is predicted to within a single frame. */
        const val AIM_TOLERANCE = 0.3

        const val BODY_HALF_WIDTH = 0.3

        /** Ticks a drop is allowed to spend walking to the lip before it leaves. */
        const val LEAVE_TICK_BUDGET = 12

        /** Keeps the body's box overlapping the take-off block for its last grounded tick. */
        const val LAUNCH_EPSILON = 0.02

        val JUMPS = listOf(LaunchMode.WALK_JUMP, LaunchMode.SPRINT_JUMP)

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
