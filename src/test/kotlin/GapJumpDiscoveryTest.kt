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
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * M4: a gap jump is *discovered*, not scheduled.
 *
 * The nominal walk falls into the hole. That failure is not noise -- it names the
 * frame the body left the ground, and backtracking over the grounded trace before it
 * finds the launch tick that carries the arc across. Nothing is executed on the
 * strength of the coarse candidate alone: only a full simulation certifies it.
 */
class GapJumpDiscoveryTest {
    /**
     * A one-wide hole needs no jump at all: a sprint carries the body across, falling
     * ~0.4 blocks and auto-stepping (0.6) back up on the far side. The trajectory
     * layer finding this is the point -- the coarse layer only ever proposed a jump.
     */
    @Test
    fun `a one-block hole is sprinted across without a jump`() {
        val environment = gapEnvironment(holeAt = 3..3)
        val route = route(environment, Stance(6, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 6.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `a two-block hole is crossed by a discovered launch`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))
        assertTrue(
            route.edges.any { it.kind == CoarseMoveKind.JUMP_CANDIDATE },
            "the coarse layer must propose the jump",
        )

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump }, "a two-wide hole cannot be walked")
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 7.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    @Test
    fun `a three-block hole is crossed by a discovered launch`() {
        val environment = gapEnvironment(holeAt = 3..5)
        val route = route(environment, Stance(8, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initialState(), PROFILE, environment),
        )

        assertTrue(result.tape.asList().any { it.jump })
        assertTrue(result.rollout.finalState.onGround)
        assertTrue(hypot(result.rollout.finalState.position.x - 8.5, result.rollout.finalState.position.z - 0.5) <= 0.20)
    }

    /** The launch has to be a *discovery*: the nominal walk must genuinely fall in. */
    @Test
    fun `without a discovered launch the nominal walk falls into the hole`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))

        // No launch lattice to search: the walk can only fall.
        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            WalkingSeedSearch.search(
                route, initialState(), PROFILE, environment,
                WalkingSeedSearchConfig(maxGapSeeds = 0),
            ),
        )

        assertTrue(
            result.attempts.mapNotNull { it.diagnostic }.any { it is TrajectoryDiagnostic.FellBelowRoute },
            "the nominal walk must fall in -- that failure is what drives the search",
        )
    }

    /**
     * Beyond the mask's reach the graph simply has no edge, so the goal is
     * unreachable rather than optimistically attempted.
     */
    @Test
    fun `a hole wider than the jump mask leaves no coarse route at all`() {
        val environment = gapEnvironment(holeAt = 3..9)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = true,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), Stance(12, 0, 0))
        planner.repair(Duration.INFINITE)

        assertNull(planner.routePlan(snapshotRevision = 31L), "a 7-wide hole has no candidate edge")
    }

    /** The published tape must reproduce exactly: it is the executable plan. */
    @Test
    fun `the discovered jump tape replays deterministically`() {
        val environment = gapEnvironment(holeAt = 3..4)
        val route = route(environment, Stance(7, 0, 0))
        val initial = initialState()

        val result = assertIs<WalkingSeedSearchResult.Success>(
            WalkingSeedSearch.search(route, initial, PROFILE, environment),
        )

        val replay = MovementSimulator(PROFILE, environment, initial)
        result.tape.asList().forEach { replay.tickMovement(it) }
        assertEquals(result.rollout.finalState, replay.state)
    }

    private fun route(environment: SnapshotSimulationEnvironment, goal: Stance) = run {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false,
                allowStepUp = false,
                maxWalkOffDepth = 0,
                allowJumpCandidates = true,
            ),
        )
        val planner = CoarsePlanner(environment, moves, Stance(0, 0, 0), goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        requireNotNull(planner.routePlan(snapshotRevision = 30L))
    }

    /** Flat floor along +X with the floor missing under [holeAt], and a void below. */
    private fun gapEnvironment(holeAt: IntRange): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..14) for (z in -3..3) {
                if (x in holeAt) continue
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-3, -12, -3, 14, 5, 3),
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
