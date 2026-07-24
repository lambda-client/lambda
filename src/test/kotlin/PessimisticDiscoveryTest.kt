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
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Pessimistic-first discovery: the corridor-adherent gait family (first look-ahead,
 * first brake) runs with its launch beam before the full gait grid is paid for. A
 * segment it certifies -- or safely extends -- never sweeps the other 40 gaits; a
 * segment it genuinely cannot solve escalates and keeps the full sweep's guarantees.
 */
class PessimisticDiscoveryTest {
    @Test
    fun `a plain corridor certifies on the adherent tier without the full gait sweep`() {
        val environment = corridor(length = 20)
        val route = route(environment, Stance(20, 0, 0))
        val config = WalkingSeedSearchConfig()

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment, config),
        )

        assertEquals(
            1, result.attempts.size,
            "a simple corridor should stop after the preferred sprint family certifies: " +
                "${result.attempts.map { it.parameters }}",
        )
        assertEquals(config.lookAheadNodes.first(), result.parameters.lookAheadNodes)
        assertEquals(config.brakeDistances.first(), result.parameters.brakeDistance)
        assertTrue(result.parameters.sprint, "sprint still wins inside the adherent tier")
    }

    @Test
    fun `a dead end escalates to the full gait sweep before refusing`() {
        // The coarse route lies: one long WALK edge across a void far too wide for any
        // launch. Progress toward the far node is impossible, so no moving extension
        // exists, and the search must spend the entire gait grid before giving up.
        val environment = uncrossableVoid()
        val liar = route(corridor(length = 7), Stance(7, 0, 0))
        val route = liar.copy(
            nodes = listOf(liar.nodes.first(), liar.nodes.last()),
            edges = listOf(
                CoarseEdge(
                    id = CoarseEdgeId(MotionTemplateId(0), liar.nodes.first()),
                    from = liar.nodes.first(),
                    to = liar.nodes.last(),
                    kind = CoarseMoveKind.WALK,
                    lowerBoundTicks = 12.0,
                    readSet = emptySet(),
                ),
            ),
            tailCosts = listOf(liar.tailCosts.first(), liar.tailCosts.last()),
        )
        val config = WalkingSeedSearchConfig()

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment, config),
        )

        assertNull(result.extension, "no progress is possible over a 7-wide void")
        val first = result.attempts.first().parameters
        assertEquals(config.lookAheadNodes.first(), first.lookAheadNodes)
        assertEquals(config.brakeDistances.first(), first.brakeDistance)
        val nominalGaits = result.attempts
            .filter { it.parameters.gapLaunchFrames.isEmpty() }
            .map { it.parameters.lookAheadNodes to it.parameters.brakeDistance }
            .toSet()
        assertEquals(
            config.lookAheadNodes.size * config.brakeDistances.size, nominalGaits.size,
            "a refusal must be backed by the escalated full sweep, got $nominalGaits",
        )
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

    /** One block of floor, then nothing at all: no launch lands, no progress happens. */
    private fun uncrossableVoid(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (z in -3..3) put(BlockPos(0, -1, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 10, 5, 3),
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
