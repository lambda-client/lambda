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
import com.lambda.pathing.search.momentumCredit
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.physics.MovementSimulator
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

/**
 * What is a block per tick of forward momentum actually worth, in ticks?
 *
 * The search ranks anchors by `elapsed + value`, and the coarse value prices a
 * *stationary* body, so a sprinting anchor and a stopped one on the same block score the
 * same though they are plainly not equivalent. The correction is only as good as its
 * constant, and the constant is a physical fact, so it is measured here against the exact
 * simulator rather than guessed. (Guessing it produced "about eleven ticks", which this
 * test shows is off by roughly a factor of five.)
 */
class MomentumCreditTest {
    @Test
    fun `the credit constant matches the measured cost of starting from rest`() {
        val measured = ticksLost(DISTANCE_BLOCKS)
        val predicted = momentumCredit(TOP_SPEED, alignment = 1.0)

        // Both describe the same quantity: how many ticks a body that starts at top speed
        // finishes ahead of one that starts from rest over the same straight run.
        assertTrue(
            abs(measured - predicted) <= TOLERANCE_TICKS,
            "credit constant predicts %.2f ticks, the simulator measures %.2f".format(predicted, measured),
        )
        println("[momentum] measured %.2f ticks, predicted %.2f".format(measured, predicted))
    }

    @Test
    fun `credit is proportional to aligned speed and never negative`() {
        assertTrue(momentumCredit(0.0, alignment = 1.0) == 0.0)
        assertTrue(momentumCredit(TOP_SPEED, alignment = 0.0) == 0.0, "sideways momentum earns nothing")
        assertTrue(momentumCredit(TOP_SPEED, alignment = -1.0) == 0.0, "momentum away earns nothing")
        assertTrue(
            momentumCredit(TOP_SPEED, alignment = 1.0) > momentumCredit(TOP_SPEED / 2, alignment = 1.0),
            "more aligned speed is worth more",
        )
    }

    /** Ticks a body starting from rest needs beyond one already at [TOP_SPEED]. */
    private fun ticksLost(distance: Double): Double {
        val fromRest = ticksToCover(distance, Vec3d.ZERO)
        val fromSpeed = ticksToCover(distance, Vec3d(0.0, 0.0, TOP_SPEED))
        return (fromRest - fromSpeed).toDouble()
    }

    private fun ticksToCover(distance: Double, velocity: Vec3d): Int {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (z in -4..(distance.toInt() + 8)) for (x in -4..4) {
            blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-8, 58, -8, 8, 80, distance.toInt() + 12),
            blocks = blocks,
        )
        val simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 64.0, 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = velocity.add(0.0, -0.0784, 0.0),
                onGround = true,
            ),
        )
        val input = MovementSimulationInput(
            forward = 1.0, sprint = true, jump = false, rotation = Rotation(0.0, 0.0),
        )
        var ticks = 0
        while (simulator.state.position.z - 0.5 < distance && ticks < 400) {
            if (simulator.tryTickMovement(input) !is MovementSimulationStepResult.Advanced) break
            ticks++
        }
        return ticks
    }

    private companion object {
        const val DISTANCE_BLOCKS = 30.0

        /** Sprint equilibrium on stone; the same rate the coarse costs are measured at. */
        const val TOP_SPEED = 0.2806

        /** One tick either way: the quantity is integer-valued in the simulator. */
        const val TOLERANCE_TICKS = 1.0

    }
}
