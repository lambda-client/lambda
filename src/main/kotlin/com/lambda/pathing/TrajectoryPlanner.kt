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
                    "; closest ended %.2f blocks short at %.3f b/t (%s)".format(
                        it.finalGoalError, it.finalHorizontalSpeed, it.diagnostic,
                    )
                } ?: ""
                return "no certified stop from ${attempts.size} attempts: $byKind$closest"
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
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = config.maxWalkOffDepth,
            allowJumpCandidates = config.allowJumpCandidates,
            maxJumpSpan = config.maxJumpSpan,
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

            var failure: WalkingSeedSearchResult? = null

            // A trajectory can only be certified as far as the simulator can reach
            // inside its frame budget, so a long route is walked as a series of
            // windows -- each ending at a *certified stop*, which is what invariant 5
            // demands before the body may cross the certified frontier. The manager
            // continues from that stop; nothing is executed on faith.
            for (nodeCount in windowSizes(route, seedConfig)) {
                val window = route.prefix(nodeCount)
                when (val seed = WalkingSeedSearch.search(window, initial, profile, snapshot, seedConfig)) {
                    is WalkingSeedSearchResult.Success -> return@supplyAsync PathPlanResult.Planned(
                        PathingManager.PublishedPath(
                            route = window,
                            plan = TrajectoryPlan.fromWalkingSeed(
                                TrajectoryPlanId(planIds.incrementAndGet()), seed, profile,
                            ),
                            profile = profile,
                            parameters = seed.parameters,
                            attempts = seed.attempts.size,
                            planMillis = System.currentTimeMillis() - started,
                            finalGoal = goal,
                        )
                    )

                    is WalkingSeedSearchResult.UnsupportedRoute -> return@supplyAsync PathPlanResult.NoRoute(
                        "route needs unsupported moves: ${seed.edgeKinds.joinToString()}"
                    )

                    is WalkingSeedSearchResult.UnstableReplay -> return@supplyAsync PathPlanResult.NoRoute(
                        "certified tape did not reproduce: ${seed.reason}"
                    )

                    is WalkingSeedSearchResult.NoSafeStop -> failure = seed
                }
            }

            PathPlanResult.NoSafeStop(failure as WalkingSeedSearchResult.NoSafeStop)
        }
    }

    /**
     * Window sizes to try, longest first.
     *
     * The coarse cost is a *lower* bound -- it assumes the body moves at the envelope's
     * maximum -- so the real walk takes rather longer. The first guess deflates it by
     * [TICK_PESSIMISM] and leaves [STOP_MARGIN_TICKS] to brake in. If that still will
     * not certify, shrink: a window that cannot be simulated is worth nothing, and a
     * shorter certified one is worth everything.
     */
    internal fun windowSizes(route: CoarseRoutePlan, config: WalkingSeedSearchConfig): List<Int> {
        if (route.nodes.size < 2) return emptyList()

        val budget = config.maxFrames - STOP_MARGIN_TICKS
        var ticks = 0.0
        var reachable = 1
        for (edge in route.edges) {
            ticks += edge.lowerBoundTicks * TICK_PESSIMISM
            if (ticks > budget) break
            reachable++
        }

        val first = reachable.coerceIn(2, route.nodes.size)
        return listOf(first, first * 3 / 4, first / 2, 2)
            .map { it.coerceIn(2, route.nodes.size) }
            .distinct()
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

    /** The coarse cost assumes envelope-max speed; a real walk takes about twice that. */
    private const val TICK_PESSIMISM = 2.0

    /** Frames left free at the end of a window so the body can actually brake. */
    private const val STOP_MARGIN_TICKS = 20

    private const val MARGIN = 12
    private const val VERTICAL_MARGIN = 6
}
