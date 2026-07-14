/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.client.network.ClientPlayerEntity
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

sealed interface PathPlanResult {
    data class Planned(val path: PathingManager.PublishedPath) : PathPlanResult
    data class NoRoute(val reason: String) : PathPlanResult

    /** A refusal, not a failure: no parameter set reached a certified stop. */
    data class NoSafeStop(val result: WalkingSeedSearchResult.NoSafeStop) : PathPlanResult {
        /**
         * Names *why*, not just that it refused. The dominant diagnostic is the one
         * M4's failure-directed branching should attack first.
         */
        val summary: String
            get() {
                val attempts = result.attempts
                val byKind = attempts
                    .mapNotNull { it.diagnostic }
                    .groupingBy { it::class.simpleName ?: "?" }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .joinToString { "${it.value} ${it.key}" }
                val nearest = result.nearest
                val closest = nearest?.let {
                    "; closest ended %.2f blocks from the remaining goal at %.3f b/t: %s; %s".format(
                        it.finalGoalError, it.finalHorizontalSpeed, it.diagnostic.describe(),
                        it.parameters.describe(),
                    )
                } ?: ""
                val launchDepths = attempts.groupBy { it.parameters.gapLaunchFrames.size }
                    .entries.sortedBy { it.key }
                    .joinToString("; ") { (depth, atDepth) ->
                        val failures = atDepth.groupingBy {
                            it.diagnostic?.let { diagnostic -> diagnostic::class.simpleName } ?: "Success"
                        }.eachCount().entries.sortedByDescending { it.value }
                            .joinToString { (kind, count) -> "$count $kind" }
                        "${depth}j[$failures]"
                    }
                val expansion = if (result.completedSegments > 0) {
                    val start = result.remainingStart?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "?"
                    val goal = result.remainingGoal?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "?"
                    " after ${result.completedSegments} moving segment(s) through frames " +
                        "${result.spliceFrames.joinToString()}, while expanding $start -> $goal" +
                        result.remainingMoveSummary.takeIf { it.isNotEmpty() }
                            ?.let { " over {$it}" }.orEmpty()
                } else ""
                return "no certified continuous trajectory$expansion from ${attempts.size} attempts: " +
                    "$byKind; search depths $launchDepths$closest"
            }

        private fun com.lambda.pathing.trajectory.WalkingSeedParameters.describe(): String =
            "sprint=$sprint, lookAhead=$lookAheadNodes, brake=%.2f, stepLead=%s, launches=%s".format(
                brakeDistance,
                stepUpJumpLeadDistance?.let { "%.2f".format(it) } ?: "none",
                gapLaunchFrames.joinToString(prefix = "[", postfix = "]"),
            )

        private fun TrajectoryDiagnostic?.describe(): String = when (this) {
            null -> "no diagnostic"
            is TrajectoryDiagnostic.HorizontalCollision ->
                "ground collision at frame $frame near ${position.short()}"
            is TrajectoryDiagnostic.HeadBonk ->
                "head bonk at frame $frame near ${position.short()}"
            is TrajectoryDiagnostic.FellBelowRoute ->
                "fell %.2f blocks below the route at frame %d".format(depth, frame)
            is TrajectoryDiagnostic.LeftCorridor ->
                "left the corridor by %.2f blocks at frame %d".format(deviation, frame)
            is TrajectoryDiagnostic.HarmfulFall ->
                "unsafe %.2f-block fall at frame %d".format(fallDistance, frame)
            is TrajectoryDiagnostic.NoStop ->
                "no stable stop (%.2f blocks away, %.3f b/t) after %d frames"
                    .format(goalError, speed, frame)
            is TrajectoryDiagnostic.UnsupportedPhysics ->
                "unsupported physics at frame $frame: $reason"
        }

        private fun net.minecraft.util.math.Vec3d.short(): String =
            "(%.2f, %.2f, %.2f)".format(x, y, z)
    }
}

/**
 * Captures an immutable snapshot on the client thread, then runs D* and the walking
 * seed search off-thread. Planning never touches live world state.
 *
 * Only WALK and STEP_UP are certified by the M3 seed search; anything else comes
 * back as a typed refusal rather than an optimistic guess.
 */
object TrajectoryPlanner {
    private val planIds = AtomicLong()

    /**
     * Bounds every coarse lower-bound cost. 0.6 b/t sits above any real horizontal
     * speed, including a sprint jump, so the derived costs stay admissible.
     */
    val envelope = CoarseKinematicEnvelope(
        maxHorizontalBlocksPerTick = 0.6,
        maxAscentBlocksPerTick = 0.5,
        maxDescentBlocksPerTick = 4.0,
    )

    /**
     * Must be called on the client thread: it reads the live player and world.
     *
     * [config] is the requester's own planning config, snapshotted here so the worker
     * never reads a setting that could change under it.
     */
    fun planAsync(
        player: ClientPlayerEntity,
        goal: Stance,
        config: PathingConfig,
    ): CompletableFuture<PathPlanResult> {
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = config.maxWalkOffDepth,
            allowJumpCandidates = config.allowJumpCandidates,
            maxJumpSpan = config.maxJumpSpan,
            maxJumpDrop = config.maxJumpDrop,
        )
        val seedConfig = WalkingSeedSearchConfig(
            maxFrames = config.maxFrames,
            goalRadius = config.goalRadius,
            maxCorridorDeviation = config.maxCorridorDeviation,
            sprintModes = if (config.allowSprint) listOf(true, false) else listOf(false),
        )
        val initial = MovementSimulationState.from(player)
        val profile = PlayerPhysicsProfile.capture(player)
        val start = Stance(player.blockPos.x, player.blockPos.y, player.blockPos.z)

        // Every coarse cost is a lower bound derived from this envelope, so a start
        // state outside it makes the whole route's optimism unfounded. Refuse rather
        // than plan against costs that do not bound the body we actually have.
        if (!envelope.contains(initial.velocity)) {
            return CompletableFuture.completedFuture(
                PathPlanResult.NoRoute(
                    "entry velocity %.3f b/t is outside the coarse kinematic envelope"
                        .format(initial.velocity.horizontalLength())
                )
            )
        }

        val snapshot = SnapshotSimulationEnvironment.capture(
            player.entityWorld, player, boundsCovering(start, goal),
        )

        return CompletableFuture.supplyAsync {
            val started = System.currentTimeMillis()
            val moves = SimpleMoveLibrary.build(costs = envelope.moveCosts(), options = moveOptions)
            val planner = CoarsePlanner(snapshot, moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) {
                return@supplyAsync PathPlanResult.NoRoute("D* did not converge")
            }
            val route = planner.routePlan(snapshotRevision = snapshot.capturedWorldTime)
                ?: return@supplyAsync PathPlanResult.NoRoute("no coarse route to the goal")

            // Local controllers are only search pieces. A failed piece may publish a
            // safe *simulated* moving prefix to the next worker-local expansion, but
            // never to execution. The final result is one concatenated tape replayed
            // from the original state through the final stable stop. There are no
            // runtime stop-and-replan legs hidden behind trajectory windowing.
            when (val seed = WalkingSeedSearch.searchContinuously(
                route, initial, profile, snapshot, seedConfig,
            )) {
                is WalkingSeedSearchResult.Success -> PathPlanResult.Planned(
                    PathingManager.PublishedPath(
                        route = route,
                        plan = TrajectoryPlan.fromWalkingSeed(
                            TrajectoryPlanId(planIds.incrementAndGet()), seed, profile,
                        ),
                        profile = profile,
                        parameters = seed.parameters,
                        attempts = seed.attempts.size,
                        planMillis = System.currentTimeMillis() - started,
                        finalGoal = goal,
                        controlSegments = seed.controlSegments,
                        spliceFrames = seed.spliceFrames,
                    )
                )

                is WalkingSeedSearchResult.UnsupportedRoute -> PathPlanResult.NoRoute(
                    "route needs unsupported moves: ${seed.edgeKinds.joinToString()}"
                )

                is WalkingSeedSearchResult.UnstableReplay -> PathPlanResult.NoRoute(
                    "certified expanded tape did not reproduce: ${seed.reason}"
                )

                is WalkingSeedSearchResult.NoSafeStop -> PathPlanResult.NoSafeStop(seed)
            }
        }
    }

    /** The snapshot must cover the whole search region plus jump/fall headroom. */
    private fun boundsCovering(start: Stance, goal: Stance) = SimulationSnapshotBounds(
        minX = minOf(start.x, goal.x) - MARGIN,
        minY = minOf(start.y, goal.y) - VERTICAL_MARGIN,
        minZ = minOf(start.z, goal.z) - MARGIN,
        maxX = maxOf(start.x, goal.x) + MARGIN,
        maxY = maxOf(start.y, goal.y) + VERTICAL_MARGIN,
        maxZ = maxOf(start.z, goal.z) + MARGIN,
    )

    private const val MARGIN = 12
    private const val VERTICAL_MARGIN = 6
}
