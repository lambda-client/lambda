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
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.core.TailCost
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TailBoundTest {
    @Test
    fun `a continuous moving checkpoint has a typed admissible tail score`() {
        val route = route()
        val atNode = state(x = 1.5)
        val movingTowardNext = state(x = 1.75, velocityX = 0.25)

        val first = tailLowerBound(atNode, route, fromNodeIndex = 1)
        val moving = tailLowerBound(movingTowardNext, route, fromNodeIndex = 1)

        assertEquals(4.0, first.ticks, 1e-9)
        assertEquals(3.5, moving.ticks, 1e-9)
        assertIs<TailCost.Bounds>(moving.tailCost)
        assertTrue(moving.ticks < first.ticks, "progress between stances must reduce the remaining bound")
    }

    @Test
    fun `checkpoint ranking keeps total time primary and uses contact events only as a tie break`() {
        val movingContinuation = TrajectoryRank(true, 5, 21.0, 1, 2, 4)
        val earlyBrake = TrajectoryRank(true, 5, 24.0, 0, 8, 1)
        val equallyFastButClear = movingContinuation.copy(collisionEvents = 0)

        assertTrue(movingContinuation < earlyBrake)
        assertTrue(equallyFastButClear < movingContinuation)
    }

    private fun route(): CoarseRoutePlan {
        val nodes = (0..3).map { Stance(it, 0, 0) }
        val edges = nodes.zipWithNext { from, to ->
            CoarseEdge(
                CoarseEdgeId(MotionTemplateId(0), from), from, to, CoarseMoveKind.WALK,
                lowerBoundTicks = 2.0, readSet = emptySet(),
            )
        }
        return CoarseRoutePlan(
            snapshotRevision = 1L,
            routeVersion = 7L,
            nodes = nodes,
            edges = edges,
            lowerBoundTicks = 6.0,
            exactFromStart = true,
            dependencies = emptySet(),
            tailCosts = listOf(
                TailCost.Exact(6.0, 7L),
                TailCost.Bounds(4.0, 4.0, 7L),
                TailCost.Bounds(2.0, 2.0, 7L),
                TailCost.Exact(0.0, 7L),
            ),
            heuristicCaps = SimpleMoveLibrary.HeuristicCaps(2.0, 4.0, 0.5),
        )
    }

    private fun state(x: Double, velocityX: Double = 0.0) = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(x, 0.0, 0.5),
        rotation = Rotation(-90.0, 0.0),
        velocity = Vec3d(velocityX, -0.0784, 0.0),
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
