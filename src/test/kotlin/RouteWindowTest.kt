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
 * A trajectory can only be certified as far as the simulator reaches inside its frame
 * budget. Beyond that a route must be walked as a series of windows, each ending at a
 * certified stop -- the body never crosses the certified frontier on faith.
 */
class RouteWindowTest {
    @Test
    fun `a route longer than the frame budget cannot be certified whole`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))

        val result = WalkingSeedSearch.search(route, initialState(), PROFILE, environment)

        assertIs<WalkingSeedSearchResult.NoSafeStop>(result, "120 blocks must not fit in one tape")
    }

    @Test
    fun `its window does certify, and stops short of the goal on purpose`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))
        val config = WalkingSeedSearchConfig()

        val sizes = TrajectoryPlanner.windowSizes(route, config)
        val window = route.prefix(sizes.first())

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(window, initialState(), PROFILE, environment, config),
        )

        assertTrue(window.goal != route.goal, "the window must stop short of the goal")
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(result.tape.frameCount <= config.maxFrames)
    }

    /** A window must not carry the tail's read set, or a block it never touches invalidates it. */
    @Test
    fun `a window recomputes its own cost and dependencies`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))

        val window = route.prefix(10)

        assertEquals(10, window.nodes.size)
        assertEquals(9, window.edges.size)
        assertEquals(Stance(9, 0, 0), window.goal)
        assertTrue(window.lowerBoundTicks < route.lowerBoundTicks)
        assertTrue(window.dependencies.size < route.dependencies.size)
        assertTrue(route.dependencies.containsAll(window.dependencies))
        assertTrue(!window.exactFromStart, "only a goal-reaching route has an exact cost to the goal")
    }

    /** Shrinking retries exist because the first estimate is a guess, not a proof. */
    @Test
    fun `window sizes shrink and never exceed the route`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(120, 0, 0))

        val sizes = TrajectoryPlanner.windowSizes(route, WalkingSeedSearchConfig())

        assertTrue(sizes.isNotEmpty())
        assertTrue(sizes.all { it in 2..route.nodes.size })
        assertEquals(sizes.sortedDescending(), sizes, "longest window first")
        assertEquals(sizes.distinct(), sizes)
    }

    @Test
    fun `a short route is planned whole, not windowed`() {
        val environment = corridor(length = 120)
        val route = route(environment, Stance(10, 0, 0))

        val sizes = TrajectoryPlanner.windowSizes(route, WalkingSeedSearchConfig())

        assertEquals(route.nodes.size, sizes.first(), "a route inside the budget needs no window")
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
