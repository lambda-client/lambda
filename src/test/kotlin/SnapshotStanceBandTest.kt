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
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * A snapshot has to cover every move the trajectory layer may *attempt* from a stance,
 * not just the cells the coarse mask inspects.
 *
 * The two layers disagree by two blocks: the mask reads at most two cells above a
 * stance, while a sprint jump from it reads four. A capture sized for the mask
 * therefore accepts high ground on which no jump can ever be certified -- and because
 * the rollout fails closed, it refuses without being able to say why.
 *
 * The live symptom was a plain staircase: entered from the bottom step the route
 * refused, and entered one step higher -- which lifted the whole capture by one block
 * -- the same route certified. The old JVM staircase fixtures could not see any of it,
 * because they hand-picked bounds tall enough to hide it. These use the production
 * bounds function on purpose.
 */
class SnapshotStanceBandTest {
    private val start = Stance(0, 0, 0)
    private val goal = Stance(22, 0, 0)

    @Test
    fun `production bounds leave a full jump ceiling above every usable stance`() {
        val bounds = TrajectoryPlanner.boundsCovering(start, goal)

        assertTrue(
            bounds.simulableStanceY.last + SimulationSnapshotBounds.CEILING_REACH <= bounds.maxY,
            "a stance the coarse layer may use must have its whole jump envelope captured",
        )
        assertTrue(
            bounds.simulableStanceY.first - SimulationSnapshotBounds.FLOOR_REACH >= bounds.minY,
            "a stance the coarse layer may use must have its supporting reads captured",
        )
        assertTrue(
            bounds.simulableStanceY.last >= maxOf(start.y, goal.y) + 3,
            "a staircase must be able to climb meaningfully above its endpoints",
        )
    }

    /**
     * The mirror of the staircase bug. A stance at the very bottom of the capture is
     * *simulable* -- a grounded body only reads two blocks down -- and still useless,
     * because the first tick of a fall from it reads past the floor, so `FellBelowRoute`
     * never gets a frame to report and the fall surfaces as terrain we never captured.
     */
    @Test
    fun `the coarse band leaves room under its lowest stance for a fall to be diagnosed`() {
        val bounds = TrajectoryPlanner.boundsCovering(start, goal)
        val environment = staircaseWithGapOnTop(bounds)

        with(TrajectoryPlanner) {
            val budgeted = environment.withinBudget(start, goal).simulableStanceY

            assertTrue(
                budgeted.first - bounds.minY >= 6,
                "a body that walks off the lowest usable stance must fall for several " +
                    "recorded frames before the capture runs out",
            )
            assertTrue(
                bounds.maxY - budgeted.last >= SimulationSnapshotBounds.CEILING_REACH,
                "the highest usable stance must still have a whole sprint jump captured above it",
            )
            assertTrue(
                budgeted.first < minOf(start.y, goal.y) && budgeted.last > maxOf(start.y, goal.y),
                "the budget must admit routes that leave the band its endpoints sit in",
            )
        }
    }

    @Test
    fun `a staircase climbing above both endpoints certifies inside the captured snapshot`() {
        val environment = staircaseWithGapOnTop(TrajectoryPlanner.boundsCovering(start, goal))

        val result = WalkingSeedSearch.searchContinuously(
            route(environment), initialState(), PROFILE, environment, WalkingSeedSearchConfig(),
        )

        val success = assertIs<WalkingSeedSearchResult.Success>(
            result,
            "the top platform sits three blocks above both endpoints; every jump from it " +
                "must still be simulable",
        )
        assertTrue(success.rollout.finalState.onGround)
        assertTrue(success.attempts.none { it.diagnostic is TrajectoryDiagnostic.OutsideSnapshot })
    }

    /**
     * The band is what keeps [TrajectoryDiagnostic.OutsideSnapshot] unreachable. Starve
     * the capture of its jump ceiling and the coarse layer must decline the high ground
     * outright -- an honest "no route" -- rather than hand the seed search a staircase
     * whose every jumping candidate dies reading terrain nobody captured.
     */
    @Test
    fun `a capture too short to jump from refuses the high ground instead of poisoning the search`() {
        val starved = SimulationSnapshotBounds(-3, -14, -3, 25, 6, 3)
        val environment = staircaseWithGapOnTop(starved)

        assertFalse(
            3 in environment.simulableStanceY,
            "the top platform's jump envelope reaches past this capture",
        )
        assertFalse(
            SimpleMoveLibrary.build(
                costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
                options = MOVES,
            ).isStance(environment, Stance(7, 3, 0)),
            "geometry alone does not make a stance usable: the trajectory layer must be " +
                "able to simulate leaving it",
        )

        val planner = CoarsePlanner(
            environment,
            SimpleMoveLibrary.build(CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(), MOVES),
            start,
            goal,
        )
        assertTrue(planner.repair(Duration.INFINITE).converged)
        assertTrue(
            planner.routePlan(snapshotRevision = 1L) == null,
            "the only route crosses the uncapturable platform, so there is no honest route",
        )
    }

    private fun route(environment: SnapshotSimulationEnvironment) = run {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = MOVES,
        )
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        requireNotNull(planner.routePlan(snapshotRevision = 30L)) { "the staircase must be routable" }
    }

    /**
     * Three steps up to a platform three blocks above both endpoints, a two-wide hole
     * across that platform, then three steps back down. The hole is the point: it forces
     * a launch *from the high ground*, which is the only thing that reads four blocks up.
     */
    private fun staircaseWithGapOnTop(bounds: SimulationSnapshotBounds): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..25) for (z in -3..3) {
                val supportY = when (x) {
                    in -3..2 -> -1
                    3 -> 0
                    4 -> 1
                    in 5..9 -> 2
                    in 10..11 -> null // the hole on top of the staircase
                    in 12..14 -> 2
                    15 -> 1
                    16 -> 0
                    in 17..25 -> -1
                    else -> null
                }
                supportY?.let { put(BlockPos(x, it, z), SnapshotBlockPhysics.FULL_CUBE) }
            }
        }
        return SnapshotSimulationEnvironment.synthetic(bounds = bounds, blocks = blocks)
    }

    private fun initialState() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 0.0, 0.5),
        rotation = Rotation(-90.0, 0.0),
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
    )

    private companion object {
        val MOVES = SimpleMoveOptions(
            allowDiagonal = false,
            allowStepUp = true,
            maxWalkOffDepth = 1,
            allowJumpCandidates = true,
            maxJumpSpan = 4,
        )

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
