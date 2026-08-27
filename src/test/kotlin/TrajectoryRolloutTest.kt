/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.movement.*
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrajectoryRolloutTest {
    @Test
    fun `input tape rollout equals direct simulator replay`() {
        val environment = flatEnvironment()
        val initial = initialState()
        val tape = InputTape(
            buildList {
                repeat(4) { add(MovementSimulationInput(forward = 1.0)) }
                add(MovementSimulationInput(forward = 1.0, jump = true))
                repeat(5) { add(MovementSimulationInput(forward = 1.0)) }
            },
        )

        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = initial,
            profile = PROFILE,
            environment = environment,
            program = tape,
            frameCount = tape.frameCount,
        )
        val direct = MovementSimulator(PROFILE, environment, initial)
        val directStates = tape.asList().map { direct.tickMovement(it).simulator.state }

        assertTrue(rollout.completed)
        assertEquals(directStates, rollout.frames.map { it.state })
        assertEquals(directStates.last(), rollout.finalState)
        assertEquals(tape.frameCount - 1, rollout.certifiedThrough)
    }

    @Test
    fun `feedback program observes the exact prior simulated state`() {
        val initial = initialState()
        val observed = ArrayList<MovementSimulationState>()
        val program = ControlProgram { _, state ->
            observed += state
            MovementSimulationInput(forward = 1.0)
        }

        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = initial,
            profile = PROFILE,
            environment = flatEnvironment(),
            program = program,
            frameCount = 4,
        )

        assertEquals(initial, observed.first())
        assertEquals(rollout.frames.dropLast(1).map { it.state }, observed.drop(1))
    }

    @Test
    fun `out of bounds rollout rejects the exact frame without publishing it`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(0, 0, 0, 0, 0, 0),
            blocks = emptyMap(),
        )
        val initial = initialState(position = Vec3d(0.5, 1.0, 0.5))

        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = initial,
            profile = PROFILE,
            environment = environment,
            program = InputTape(listOf(MovementSimulationInput(forward = 1.0))),
            frameCount = 1,
        )

        val rejected = assertIs<TrajectoryRolloutTermination.Rejected>(rollout.termination)
        assertEquals(0, rejected.frame)
        assertIs<SimulationSnapshotOutOfBoundsException>(rejected.failure)
        assertTrue(rollout.frames.isEmpty())
        assertEquals(initial, rollout.finalState)
        assertEquals(-1, rollout.certifiedThrough)
    }

    @Test
    fun `input tape defensively copies its source`() {
        val source = mutableListOf(MovementSimulationInput(forward = 1.0))
        val tape = InputTape(source)
        source.clear()

        assertEquals(1, tape.frameCount)
        assertEquals(1.0, tape[0].forward)
    }

    private fun flatEnvironment(): SnapshotSimulationEnvironment {
        val floor = buildMap {
            for (x in -4..4) {
                for (z in -4..8) {
                    put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
                }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(BOUNDS, floor)
    }

    private fun initialState(position: Vec3d = Vec3d.ZERO) = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = position,
        rotation = Rotation(0.0, 0.0),
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
    )

    private companion object {
        val BOUNDS = SimulationSnapshotBounds(-4, -3, -4, 4, 5, 8)

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1,
            sneakSpeedModifier = 0.3,
            gravity = 0.08,
            jumpStrength = 0.42,
            stepHeight = 0.6,
            jumpBoostVelocityModifier = 0.0,
            slowFalling = false,
            width = 0.6,
            height = 1.8,
            eyeHeight = 1.62,
        )
    }
}
