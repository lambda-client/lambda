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
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseEdgeId
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The diagnostics are what M4's failure-directed branching keys off, so each one has
 * to name the *earliest* cause rather than a downstream symptom. A rollout that walks
 * into a wall and then fails to stop must report the wall, not the missing stop.
 */
class TrajectoryDiagnosticTest {
    @Test
    fun `a wall across the route reports the collision, not the missing stop`() {
        // A wall the corridor walks straight into: the coarse route is handed to the
        // search directly, so no detour is planned around it.
        val environment = environment(
            walls = listOf(BlockPos(3, 0, 0), BlockPos(3, 1, 0)),
        )

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(search(environment, straightRoute()))

        val diagnostics = result.attempts.mapNotNull { it.diagnostic }
        assertTrue(diagnostics.isNotEmpty())
        assertTrue(
            diagnostics.all { it is TrajectoryDiagnostic.HorizontalCollision },
            "every attempt must blame the wall, got ${diagnostics.map { it::class.simpleName }.distinct()}",
        )
    }

    @Test
    fun `walking off the end of the floor reports the fall`() {
        // Floor stops at x = 3, but the route keeps going to x = 6.
        val environment = environment(floorMaxX = 3)

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(search(environment, straightRoute()))

        val diagnostics = result.attempts.mapNotNull { it.diagnostic }
        assertTrue(
            diagnostics.any { it is TrajectoryDiagnostic.FellBelowRoute },
            "expected a fall, got ${diagnostics.map { it::class.simpleName }.distinct()}",
        )
    }

    @Test
    fun `an unreachable goal reports no stop with the distance still to cover`() {
        // Flat floor, but the route claims a goal 40 blocks out with only 12 frames
        // of budget -- the walk simply never arrives.
        val environment = environment()
        val route = straightRoute(length = 40)

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            search(environment, route, WalkingSeedSearchConfig(maxFrames = 12)),
        )

        val noStop = result.attempts.mapNotNull { it.diagnostic }.filterIsInstance<TrajectoryDiagnostic.NoStop>()
        assertTrue(noStop.isNotEmpty(), "expected NoStop diagnostics")
        assertTrue(noStop.all { it.goalError > 1.0 }, "a walk that never arrived must report the distance left")
    }

    /** The nearest attempt is what M4 will attack first, so it must be the closest one. */
    @Test
    fun `the nearest attempt is the one that got closest to the goal`() {
        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            search(environment(), straightRoute(length = 40), WalkingSeedSearchConfig(maxFrames = 12)),
        )

        val nearest = requireNotNull(result.nearest)
        assertTrue(result.attempts.all { it.finalGoalError >= nearest.finalGoalError })
    }

    private fun search(
        environment: SnapshotSimulationEnvironment,
        route: CoarseRoutePlan,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
    ) = WalkingSeedSearch.search(
        route = route,
        initialState = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 0.0, 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        ),
        profile = PROFILE,
        environment = environment,
        config = config,
    )

    /** A straight WALK route along +X. Handed in directly so no detour is planned. */
    private fun straightRoute(length: Int = 6): CoarseRoutePlan {
        val nodes = (0..length).map { Stance(it, 0, 0) }
        val edges = nodes.zipWithNext { from, to ->
            CoarseEdge(
                id = CoarseEdgeId(MotionTemplateId(0), from),
                from = from,
                to = to,
                kind = CoarseMoveKind.WALK,
                lowerBoundTicks = 1.667,
                readSet = emptySet(),
            )
        }
        return CoarseRoutePlan(
            snapshotRevision = 1L,
            routeVersion = 1L,
            nodes = nodes,
            edges = edges,
            lowerBoundTicks = edges.size * 1.667,
            exactFromStart = true,
            dependencies = emptySet(),
        )
    }

    private fun environment(
        floorMaxX: Int = 48,
        walls: List<BlockPos> = emptyList(),
    ): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..floorMaxX) for (z in -3..3) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            walls.forEach { put(it, SnapshotBlockPhysics.FULL_CUBE) }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -6, -3, 50, 6, 3),
            blocks = blocks,
        )
    }

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
