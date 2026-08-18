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
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
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
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Phase 1.5 acceptance: the anchor search must certify the same trajectories the
 * whole-route beam did, and its work must grow with the number of hazards rather than
 * with every combination of them.
 */
class MotionAnchorSearchTest {
    @Test
    fun `a flat walk certifies a stable stop at the goal`() {
        val environment = flatField(length = 24)
        val route = route(environment, Stance(0, 0, 0), Stance(16, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            MotionAnchorSearch.search(route, initialState(Stance(16, 0, 0)), PROFILE, environment),
        )

        val final = result.rollout.finalState
        assertTrue(final.onGround, "the published tape must end grounded")
        assertTrue(
            final.velocity.horizontalLength() <= 0.012,
            "the published tape must end stopped, got ${final.velocity.horizontalLength()}",
        )
        assertTrue(
            hypot(final.position.x - 16.5, final.position.z - 0.5) <= 0.20,
            "the published tape must end at the goal, got ${final.position}",
        )
    }

    @Test
    fun `a two-block gap is crossed by a discovered launch`() {
        val environment = flatField(length = 24, holes = listOf(8..9))
        val route = route(environment, Stance(0, 0, 0), Stance(16, 0, 0))

        val result = assertIs<WalkingSeedSearchResult.Success>(
            MotionAnchorSearch.search(route, initialState(Stance(16, 0, 0)), PROFILE, environment),
        )

        assertTrue(
            result.rollout.frames.any { it.input.jump },
            "crossing a two-block gap requires a certified launch",
        )
        assertTrue(result.rollout.finalState.onGround)
    }

    /**
     * The Phase 1.5 exit condition, as a test.
     *
     * The whole-route beam replayed the tape containing every earlier launch to discover
     * the next one, so a third gap cost far more than three times one gap. Anchor
     * transitions are local, so the marginal cost of an independent hazard is bounded.
     */
    @Test
    fun `work grows with the number of gaps, not with their combinations`() {
        fun attemptsFor(gaps: Int): Int {
            val holes = (0 until gaps).map { index -> (8 + index * 6)..(9 + index * 6) }
            val length = 14 + gaps * 6
            val environment = flatField(length = length, holes = holes)
            val goal = Stance(length - 6, 0, 0)
            val route = route(environment, Stance(0, 0, 0), goal)
            val result = assertIs<WalkingSeedSearchResult.Success>(
                MotionAnchorSearch.search(route, initialState(goal), PROFILE, environment),
                "the anchor search must certify a $gaps-gap chain",
            )
            return result.attempts.size
        }

        val one = attemptsFor(1)
        val three = attemptsFor(3)
        assertTrue(
            three <= one * 3,
            "three independent gaps cost $three attempts against $one for one: " +
                "the marginal hazard must stay bounded",
        )
    }

    /**
     * A budget refusal is a statement about this search, not about the world. Reporting
     * it as `IMPOSSIBLE_FOR_ALL_ENTRIES` is what blacklisted ordinary gaps out of the
     * coarse graph and produced the "infeasible coarse jump" field diagnostics.
     */
    @Test
    fun `an exhausted budget refuses without claiming the edge impossible for all entries`() {
        val environment = flatField(length = 30, holes = listOf(8..9, 14..15, 20..21))
        val goal = Stance(26, 0, 0)
        val route = route(environment, Stance(0, 0, 0), goal)

        val result = assertIs<WalkingSeedSearchResult.NoSafeStop>(
            MotionAnchorSearch.search(
                route, initialState(goal), PROFILE, environment,
                anchorConfig = MotionAnchorSearchConfig(maxExpansions = 1, maxFinishSweeps = 0),
            ),
        )

        assertEquals(
            WalkingSeedSearchResult.EdgeFailureScope.CURRENT_ENTRY,
            result.edgeFailureScope,
            "one exhausted entry family never proves a coarse edge impossible",
        )
    }

    private fun route(
        environment: SnapshotSimulationEnvironment,
        start: Stance,
        goal: Stance,
    ) = CoarsePlanner(
        environment,
        SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(allowDiagonal = false),
        ),
        start,
        goal,
    ).let { planner ->
        assertTrue(planner.repair(Duration.INFINITE).converged, "the fixture must have a coarse route")
        requireNotNull(planner.routePlan(snapshotRevision = 1L))
    }

    private fun flatField(
        length: Int,
        holes: List<IntRange> = emptyList(),
    ): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..length) {
                if (holes.any { x in it }) continue
                for (z in -3..3) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-3, -8, -3, length, 6, 3), blocks,
        )
    }

    private fun initialState(goal: Stance): MovementSimulationState = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 0.0, 0.5),
        rotation = Rotation(Math.toDegrees(kotlin.math.atan2(-goal.x.toDouble(), goal.z.toDouble())), 0.0),
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
