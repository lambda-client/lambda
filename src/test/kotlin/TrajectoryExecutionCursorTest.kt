/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.execution

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TrajectoryExecutionCursorTest {
    @Test
    fun `cursor replays certified inputs without making movement decisions`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        val simulator = MovementSimulator(PROFILE, fixture.environment, fixture.initial)

        repeat(fixture.plan.tape.frameCount) { frame ->
            val apply = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(simulator.state, REVISION))
            assertEquals(frame, apply.frame)
            simulator.tickMovement(apply.input)
            val observed = cursor.observeAfterTick(simulator.state, REVISION)
            if (frame == fixture.plan.tape.frameCount - 1) assertIs<ExecutionObservationResult.Complete>(observed)
            else assertIs<ExecutionObservationResult.Accepted>(observed)
        }

        assertIs<ExecutionInputResult.Complete>(cursor.nextInput(simulator.state, REVISION))
        assertEquals(fixture.plan.tape.frameCount, cursor.nextFrame)
    }

    @Test
    fun `cursor rejects state drift and remains rejected`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        val shifted = fixture.initial.copy(position = fixture.initial.position.add(0.1, 0.0, 0.0))

        val first = assertIs<ExecutionInputResult.Rejected>(cursor.nextInput(shifted, REVISION))
        assertIs<ExecutionDeviation.Position>(first.deviation)
        assertIs<ExecutionInputResult.Rejected>(cursor.nextInput(fixture.initial, REVISION))
    }

    @Test
    fun `cursor rejects revision changes and invalid call ordering`() {
        val fixture = fixture()
        val revisionCursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        assertIs<ExecutionDeviation.WorldRevision>(
            assertIs<ExecutionInputResult.Rejected>(revisionCursor.nextInput(fixture.initial, REVISION + 1)).deviation,
        )

        val protocolCursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        assertIs<ExecutionInputResult.Apply>(protocolCursor.nextInput(fixture.initial, REVISION))
        assertIs<ExecutionDeviation.Protocol>(
            assertIs<ExecutionInputResult.Rejected>(protocolCursor.nextInput(fixture.initial, REVISION)).deviation,
        )

        val profileCursor = TrajectoryExecutionCursor(fixture.plan, PROFILE.copy(gravity = 0.1))
        assertIs<ExecutionDeviation.PhysicsProfile>(
            assertIs<ExecutionInputResult.Rejected>(profileCursor.nextInput(fixture.initial, REVISION)).deviation,
        )
    }

    @Test
    fun `cursor defers a transient sprint bit mismatch when the next key press converges it`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        val simulator = MovementSimulator(PROFILE, fixture.environment, fixture.initial)

        val first = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(simulator.state, REVISION))
        assertTrue(first.input.sprint, "fixture must begin with a held sprint key")
        simulator.tickMovement(first.input)

        // Integrated-server metadata can expose the old sprint bit after physics,
        // even though the held key sets it again before the following movement.
        val transient = simulator.state.copy(isSprinting = !simulator.state.isSprinting)
        assertIs<ExecutionObservationResult.Accepted>(cursor.observeAfterTick(transient, REVISION))
        val second = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(transient, REVISION))
        assertTrue(second.input.sprint, "the following input must deterministically restore sprint")
    }

    @Test
    fun `cursor rejects a sprint mismatch when forward coasting cannot converge it`() {
        val fixture = fixture(initialSprinting = true, sprintModes = listOf(false))
        val cursor = TrajectoryExecutionCursor(fixture.plan, PROFILE)
        val observed = fixture.initial.copy(isSprinting = false)

        val rejected = assertIs<ExecutionInputResult.Rejected>(
            cursor.nextInput(observed, REVISION),
        )
        val deviation = assertIs<ExecutionDeviation.Flag>(rejected.deviation)
        assertEquals("sprinting", deviation.name)
    }

    private fun fixture(
        initialSprinting: Boolean = false,
        sprintModes: List<Boolean> = listOf(true, false),
    ): Fixture {
        val environment = flatEnvironment()
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 0.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
            isSprinting = initialSprinting,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(false, false, 0, false),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(3, 0, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(REVISION))
        val seed = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(
                route, initial, PROFILE, environment,
                WalkingSeedSearchConfig(sprintModes = sprintModes),
            ),
        )
        return Fixture(environment, initial, TrajectoryPlan.fromWalkingSeed(TrajectoryPlanId(1), seed, PROFILE))
    }

    private fun flatEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..7) for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-3, -3, -3, 7, 4, 3),
            blocks,
        )
    }

    private data class Fixture(
        val environment: SnapshotSimulationEnvironment,
        val initial: MovementSimulationState,
        val plan: TrajectoryPlan,
    )

    private companion object {
        const val REVISION = 44L
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
