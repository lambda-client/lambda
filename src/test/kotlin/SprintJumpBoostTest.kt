/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.util.player.prediction

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

/**
 * Vanilla's sprint-jump boost (`LivingEntity.jump`) reads the **float** yaw and
 * goes through `MathHelper.sin/cos`, which are a 65536-entry float lookup table --
 * not `Math.sin`. The table's ~5e-5 quantization error is an order of magnitude
 * larger than the replay tolerance, so a simulator using exact trig drifts on
 * every sprint jump.
 *
 * The error vanishes at yaw 0/45/90 -- those land exactly on table indices --
 * which is why axis-aligned scenarios cannot catch it.
 *
 * A stationary sprint jump gains no other horizontal velocity, so the horizontal
 * velocity after the tick is exactly `boost * groundFriction`. The friction is
 * recovered from the yaw-0 case, where the table is exact by construction.
 */
class SprintJumpBoostTest {
    @Test
    fun `sprint jump boost matches the vanilla sine table off axis`() {
        val friction = groundFriction()

        for (yaw in listOf(30.0, 33.0, 37.0, 60.0, 127.5, -22.5)) {
            val velocity = sprintJumpVelocity(yaw)
            val boost = vanillaBoost(yaw)

            assertEquals(boost.x * friction, velocity.x, 1.0E-12, "sprint-jump boost x at yaw $yaw")
            assertEquals(boost.z * friction, velocity.z, 1.0E-12, "sprint-jump boost z at yaw $yaw")
        }
    }

    /**
     * Proves the test above has teeth: exact trig and the vanilla table really do
     * disagree by more than the replay epsilon at these headings.
     */
    @Test
    fun `exact trig would miss the vanilla table by more than the replay epsilon`() {
        for (yaw in listOf(30.0, 37.0, 60.0)) {
            val table = vanillaBoost(yaw)
            val radians = Math.toRadians(yaw)
            val exact = Vec3d(-kotlin.math.sin(radians) * 0.2, 0.0, kotlin.math.cos(radians) * 0.2)

            assertTrue(
                abs(table.x - exact.x) > REPLAY_EPSILON || abs(table.z - exact.z) > REPLAY_EPSILON,
                "yaw $yaw must distinguish the sine table from exact trig",
            )
        }
    }

    /** At these headings the table is exact, so they can never catch the bug. */
    @Test
    fun `axis aligned headings cannot distinguish the sine table from exact trig`() {
        for (yaw in listOf(0.0, 45.0, 90.0)) {
            val table = vanillaBoost(yaw)
            val radians = Math.toRadians(yaw)
            val exact = Vec3d(-kotlin.math.sin(radians) * 0.2, 0.0, kotlin.math.cos(radians) * 0.2)

            assertTrue(abs(table.x - exact.x) < REPLAY_EPSILON, "yaw $yaw x")
            assertTrue(abs(table.z - exact.z) < REPLAY_EPSILON, "yaw $yaw z")
        }
    }

    /** @see net.minecraft.entity.LivingEntity.jump */
    private fun vanillaBoost(yaw: Double): Vec3d {
        val radians = yaw.toFloat() * (Math.PI / 180.0).toFloat()
        return Vec3d(
            -MathHelper.sin(radians.toDouble()).toDouble() * 0.2,
            0.0,
            MathHelper.cos(radians.toDouble()).toDouble() * 0.2,
        )
    }

    /** Yaw 0 boosts by exactly (0, 0, 0.2), so it reveals the ground friction. */
    private fun groundFriction(): Double = sprintJumpVelocity(0.0).z / 0.2

    /** Horizontal velocity after one grounded sprint+jump tick with no steering input. */
    private fun sprintJumpVelocity(yaw: Double): Vec3d {
        val rotation = Rotation(yaw, 0.0)
        val simulator = MovementSimulator(
	        profile = PROFILE,
	        environment = environment(),
	        initialState = MovementSimulationState.synthetic(
		        profile = PROFILE,
		        position = Vec3d(0.5, 0.0, 0.5),
		        rotation = rotation,
		        velocity = Vec3d.ZERO,
		        onGround = true,
		        isSprinting = true,
	        ),
        )

        simulator.tickMovement(MovementSimulationInput(sprint = true, jump = true, rotation = rotation))
        return simulator.state.velocity
    }

    private fun environment() = SnapshotSimulationEnvironment.synthetic(
        bounds = SimulationSnapshotBounds(-2, -2, -2, 2, 4, 2),
        blocks = buildMap {
            for (x in -2..2) for (z in -2..2) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        },
    )

    private companion object {
        const val REPLAY_EPSILON = 1.0E-6

    }
}
