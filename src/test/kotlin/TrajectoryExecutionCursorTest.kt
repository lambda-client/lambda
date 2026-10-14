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
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.search.MotionPlanResult
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.search.SearchLoop
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.CoarseKinematicEnvelope
import pathing.ProbeScenarios.PROFILE

class TrajectoryExecutionCursorTest {
    @Test
    fun `cursor replays certified inputs without making movement decisions`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        val simulator = MovementSimulator(PROFILE, fixture.environment, fixture.initial)

        repeat(fixture.plan.tape.frameCount) { frame ->
            val apply = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(simulator.state))
            assertEquals(frame, apply.frame)
            simulator.tickMovement(apply.input)
            val observed = cursor.observeAfterTick(simulator.state)
            if (frame == fixture.plan.tape.frameCount - 1) assertIs<ExecutionObservationResult.Complete>(observed)
            else assertIs<ExecutionObservationResult.Accepted>(observed)
        }

        assertIs<ExecutionInputResult.Complete>(cursor.nextInput(simulator.state))
        assertEquals(fixture.plan.tape.frameCount, cursor.nextFrame)
    }

    @Test
    fun `cursor rejects state drift and remains rejected`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        val shifted = fixture.initial.copy(position = fixture.initial.position.add(0.1, 0.0, 0.0))

        val first = assertIs<ExecutionInputResult.Rejected>(cursor.nextInput(shifted))
        assertIs<ExecutionDeviation.Position>(first.deviation)
        assertIs<ExecutionInputResult.Rejected>(cursor.nextInput(fixture.initial))
    }

    @Test
    fun `cursor rejects invalid call ordering and profile changes`() {
        val fixture = fixture()
        val protocolCursor = TrajectoryExecutionCursor(fixture.plan)
        assertIs<ExecutionInputResult.Apply>(protocolCursor.nextInput(fixture.initial))
        assertIs<ExecutionDeviation.Protocol>(
            assertIs<ExecutionInputResult.Rejected>(protocolCursor.nextInput(fixture.initial)).deviation,
        )

        assertFalse(PROFILE.copy(gravity = 0.1).isCompatibleWith(fixture.plan.physicsProfile))
    }

    @Test
    fun `cursor accepts float round trip noise in the physics profile`() {
        val fixture = fixture()
        val sprintRoundTrip = PROFILE.copy(movementSpeed = PROFILE.movementSpeed + 7e-9)
        assertTrue(sprintRoundTrip.isCompatibleWith(fixture.plan.physicsProfile))
    }

    @Test
    fun `cursor defers a transient sprint bit mismatch when the next key press converges it`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        val simulator = MovementSimulator(PROFILE, fixture.environment, fixture.initial)

        val first = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(simulator.state))
        assertTrue(first.input.sprint, "fixture must begin with a held sprint key")
        simulator.tickMovement(first.input)

        // Integrated-server metadata can expose the old sprint bit after physics,
        // even though the held key sets it again before the following movement.
        val transient = simulator.state.copy(isSprinting = !simulator.state.isSprinting)
        assertIs<ExecutionObservationResult.Accepted>(cursor.observeAfterTick(transient))
        val second = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(transient))
        assertTrue(second.input.sprint, "the following input must deterministically restore sprint")
    }

    @Test
    fun `cursor tolerates a swimming pose that an integrated server reports a tick early`() {
        val fixture = fixture()
        val simulator = MovementSimulator(PROFILE, fixture.environment, fixture.initial)
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        simulator.tickMovement(assertIs<ExecutionInputResult.Apply>(cursor.nextInput(simulator.state)).input)
        val box = simulator.state.boundingBox

        // The server decides swimming from the moved body and its pose echo arrives before the client's next tick.
        val swimming = simulator.state.copy(boundingBox = box.withMaxY(box.minY + PROFILE.swimmingHeight))
        assertIs<ExecutionObservationResult.Accepted>(cursor.observeAfterTick(swimming))

        val other = TrajectoryExecutionCursor(fixture.plan)
        assertIs<ExecutionInputResult.Apply>(other.nextInput(fixture.initial))
        val odd = simulator.state.copy(boundingBox = box.withMaxY(box.minY + 1.0))
        assertIs<ExecutionDeviation.Position>(assertIs<ExecutionObservationResult.Rejected>(other.observeAfterTick(odd)).deviation)
    }

    @Test
    fun `cursor rejects a sprint bit that a walking plan cannot clear`() {
        val fixture = fixture(sprintModes = listOf(false))
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        val observed = fixture.initial.copy(isSprinting = true)

        val rejected = assertIs<ExecutionInputResult.Rejected>(
            cursor.nextInput(observed),
        )
        val deviation = assertIs<ExecutionDeviation.Flag>(rejected.deviation)
        assertEquals("sprinting", deviation.name)
    }

    @Test
    fun `cursor holds the sprint key while the plan sprints so a cleared sprint bit converges`() {
        val fixture = fixture(initialSprinting = true, sprintModes = listOf(false))
        assertTrue(fixture.plan.tape.asList().none { it.sprint }, "fixture must coast on a running sprint without the key")

        val replay = TrajectoryExecutionCursor(fixture.plan)
        val body = MovementSimulator(PROFILE, fixture.environment, fixture.initial)
        repeat(fixture.plan.tape.frameCount) {
            val apply = assertIs<ExecutionInputResult.Apply>(replay.nextInput(body.state))
            val before = if (apply.frame == 0) fixture.plan.initialState else fixture.plan.frames[apply.frame - 1].state
            val coasting = before.isSprinting && fixture.plan.tape[apply.frame].forward > 0.0
            assertEquals(coasting, apply.input.sprint, "the key is held exactly while the plan coasts on a sprint (frame ${apply.frame})")
            body.tickMovement(apply.input)
            assertIs<ExecutionObservationResult>(replay.observeAfterTick(body.state))
            assertEquals(fixture.plan.frames[apply.frame].state, body.state, "the held key must not change frame ${apply.frame}")
        }

        // A server echo of an earlier sprint stop clears the bit between ticks; the held key restarts it before the move.
        val echoed = fixture.initial.copy(isSprinting = false, hadForwardMovement = true)
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        val apply = assertIs<ExecutionInputResult.Apply>(cursor.nextInput(echoed))
        val live = MovementSimulator(PROFILE, fixture.environment, echoed)
        live.tickMovement(apply.input)
        assertTrue(live.state.isSprinting)
        assertIs<ExecutionObservationResult.Accepted>(cursor.observeAfterTick(live.state))
    }

    @Test
    fun `a plan starts only from its exact initial body whatever the facing`() {
        val fixture = fixture()
        val cursor = TrajectoryExecutionCursor(fixture.plan)
        assertTrue(cursor.startsFrom(fixture.initial.copy(rotation = Rotation(37.0, 12.0))))
        assertFalse(cursor.startsFrom(fixture.initial.copy(position = fixture.initial.position.add(0.0, 1.0, 0.0))))
        assertFalse(cursor.startsFrom(fixture.initial.copy(velocity = Vec3d(0.0, 0.0, 0.0))))
    }

    @Test
    fun `completed terrain cannot invalidate the remaining tape`() {
        val fixture = fixture(startX = 14, goalX = 20)
        val plan = fixture.plan
        val oldChunk = PathingChunk(0, 0)
        assertTrue(oldChunk in plan.dependencyChunksBetween(0, plan.tape.frameCount))

        val safelyPastOldChunk = plan.frames.indexOfFirst { it.state.position.x > 18.0 }
        assertTrue(safelyPastOldChunk > 0, "fixture must cross into the next chunk")
        assertTrue(oldChunk !in plan.dependencyChunksBetween(safelyPastOldChunk, plan.tape.frameCount))
        assertTrue(plan.dependencyChunksBetween(plan.tape.frameCount, plan.tape.frameCount).isEmpty())
    }

    private fun fixture(
        initialSprinting: Boolean = false,
        sprintModes: List<Boolean> = listOf(true, false),
        startX: Int = 0,
        goalX: Int = 3,
    ): Fixture {
        val environment = flatEnvironment(minX = startX - 3, maxX = goalX + 4)
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(startX + 0.5, 0.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
            isSprinting = initialSprinting,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false, allowStepUp = false,
                maxWalkOffDepth = 0, allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(startX, 0, 0), Stance(goalX, 0, 0))
        assertTrue(planner.repair().converged)
        planner.expandSearchField()
        val route = requireNotNull(planner.routePlan(REVISION))
        val seed = assertIs<MotionPlanResult.Success>(
            SearchLoop(
                route, moves.catalog, planner.valueField(), initial, PROFILE, environment,
                MotionConstraints(sprintModes = sprintModes),
            ).run(),
        )
        return Fixture(environment, initial, seed.plan)
    }

    private fun flatEnvironment(minX: Int = -3, maxX: Int = 7): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in minX..maxX) for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(minX, -3, -3, maxX, 4, 3),
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
    }
}
