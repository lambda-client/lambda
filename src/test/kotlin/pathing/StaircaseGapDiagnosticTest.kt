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
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.coarse.CoarseMoveKind
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
import kotlin.test.assertNotNull
import kotlin.time.Duration

/**
 * Mirror of the live `pathing-staircase-above-both-endpoints` scenario (2-block
 * treads up to a peak above both endpoints, a 2-wide hole across the top).
 *
 * Field failure this pins (C3 residual): the measured-cost route opens with a span-4
 * rise-1 jump the probe admits at *sprint entry speed* -- but the body starts from
 * rest, so every sprint launch lands short into the tread wall. The failure position
 * is already nearer the landing node than the takeoff, and the naive nearest-node
 * attribution blamed the edge *after* the jump (a STEP_UP), which disabled the M6
 * retire-and-reroute entirely and silently killed the whole sprint gait.
 */
class StaircaseGapDiagnosticTest {
    @Test
    fun `a sprint-refused mid-edge jump is attributed to the jump, not the edge after it`() {
        val environment = fixture()
        val moves = SimpleMoveLibrary.build(
            costs = com.lambda.pathing.coarse.CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(allowDiagonal = true, maxWalkOffDepth = 3),
        )
        val start = Stance(0, 1, 0)
        val goal = Stance(0, 1, 22)
        val planner = CoarsePlanner(environment, moves, start, goal)
        check(planner.repair(Duration.INFINITE).converged)
        val route = requireNotNull(planner.routePlan(snapshotRevision = 1L))

        val sprintOnly = WalkingSeedSearch.searchContinuously(
            route, initialState(), PROFILE, environment,
            WalkingSeedSearchConfig(sprintModes = listOf(true)),
        )
        val refused = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            sprintOnly,
            "a from-rest sprint cannot reach the span-4 rise-1 landing; if this certifies, " +
                "the fixture no longer reproduces the field shape",
        )
        val dead = assertNotNull(refused.deadEdge, "the refusal must name the blocking edge")
        assertEquals(CoarseMoveKind.JUMP_CANDIDATE, dead.kind, "M6 can only retire a jump")
        assertEquals(route.edges.first(), dead, "the blocker is the opening jump, not the edge after it")

        // The slower gait certifies the same route; the combined search must therefore
        // still publish (walking is rank-honest here until Phase 3/4 make sprint viable).
        val combined = WalkingSeedSearch.searchContinuously(route, initialState(), PROFILE, environment)
        assertIs<WalkingSeedSearchResult.Success>(combined)
    }

    private fun fixture(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -8..8) for (z in -8..8) if (z < 3) blocks[BlockPos(x, 0, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -2..2) {
            for (z in 3..4) blocks[BlockPos(x, 1, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 5..6) blocks[BlockPos(x, 2, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 7..8) blocks[BlockPos(x, 3, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 11..14) blocks[BlockPos(x, 3, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 15..16) blocks[BlockPos(x, 2, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        for (x in -2..2) for (z in 17..24) blocks[BlockPos(x, 0, z)] = SnapshotBlockPhysics.FULL_CUBE
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, -7, -12, 12, 9, 28),
            blocks,
        )
    }

    private fun initialState() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 1.0, 0.5),
        rotation = Rotation(0.0, 0.0),
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
