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
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.TrajectoryPlan
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
        val summary: String
            get() {
                val attempts = result.attempts
                val collided = attempts.count { it.collidedHorizontally }
                val leftCorridor = attempts.count { it.leftCorridor }
                val unsupported = attempts.count { it.rejectedByEnvironment }
                val noStop = attempts.size - collided - leftCorridor - unsupported
                return "no certified stop from ${attempts.size} attempts " +
                    "($collided collided, $leftCorridor left the corridor, " +
                    "$unsupported unsupported physics, $noStop never stopped at the goal)"
            }
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
        // Walk-offs and jump candidates stay off: M3 cannot certify them.
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = 0,
            allowJumpCandidates = false,
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

            when (val seed = WalkingSeedSearch.search(route, initial, profile, snapshot, seedConfig)) {
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
                    )
                )

                is WalkingSeedSearchResult.UnsupportedRoute -> PathPlanResult.NoRoute(
                    "route needs unsupported moves: ${seed.edgeKinds.joinToString()}"
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
