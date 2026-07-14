/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.util.player.prediction.MovementSimulationState
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

/**
 * Local search horizons are worker-internal. A route beyond one horizon is expanded
 * from exact predicted moving states and flattened into one replayable trajectory;
 * execution must never stop merely because a controller horizon ended.
 */
class ContinuousTrajectoryExpansionTest {
    @Test
    fun `a singleton route certifies an already stopped body`() {
        val environment = corridor(length = 0)
        val route = route(environment, Stance(0, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.finalState.onGround)
        assertTrue(result.tape.frameCount >= WalkingSeedSearchConfig().stableStopFrames)
    }

    @Test
    fun `a route longer than the frame budget cannot be certified whole`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))

        val config = WalkingSeedSearchConfig()
        val result = WalkingSeedSearch.search(route, initialState(), PROFILE, environment, config)

        val refused = assertIs<WalkingSeedSearchResult.NoSafeStop>(result, "120 blocks must not fit in one tape")
        val extension = checkNotNull(refused.extension)
        assertTrue(extension.spliceNodeIndex > 0)
        assertTrue(extension.rollout.finalState.velocity.horizontalLength() > config.stoppedSpeed)
    }

    @Test
    fun `a route beyond one horizon expands through moving states into one tape`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))
        val config = WalkingSeedSearchConfig()

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.searchContinuously(route, initialState(), PROFILE, environment, config),
        )

        assertTrue(result.controlSegments > 1)
        assertTrue(result.spliceFrames.isNotEmpty())
        assertTrue(result.tape.frameCount > config.maxFrames)
        assertTrue(result.spliceFrames.all { it in 1 until result.tape.frameCount })
        assertTrue(result.spliceFrames.all { frame ->
            result.rollout.frames[frame - 1].state.velocity.horizontalLength() > config.stoppedSpeed
        }, "every internal boundary must preserve momentum rather than certify a stop")
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(result.rollout.finalState.velocity.horizontalLength() <= config.stoppedSpeed)
    }

    @Test
    fun `a suffix recomputes cost and dependencies from its moving splice node`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))

        val suffix = route.suffix(40)

        assertEquals(Stance(40, 0, 0), suffix.nodes.first())
        assertEquals(route.goal, suffix.goal)
        assertEquals(route.nodes.size - 40, suffix.nodes.size)
        assertTrue(suffix.lowerBoundTicks < route.lowerBoundTicks)
        assertTrue(route.dependencies.containsAll(suffix.dependencies))
    }

    private fun route(environment: SnapshotSimulationEnvironment, goal: Stance) = run {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = false,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        requireNotNull(planner.routePlan(snapshotRevision = 40L))
    }

    private fun corridor(length: Int): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..length + 3) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -6, -3, length + 3, 5, 3),
            blocks = blocks,
        )
    }

    private fun initialState() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 0.0, 0.5),
        rotation = Rotation(-90.0, 0.0),
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
    )

    private companion object {
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
