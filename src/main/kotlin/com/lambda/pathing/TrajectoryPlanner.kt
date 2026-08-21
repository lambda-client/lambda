/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.trajectory.SearchClock
import com.lambda.pathing.trajectory.SystemSearchClock
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.trajectory.MotionConstraints
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface PathPlanResult {
    data class Planned(val path: PathingManager.PublishedPath) : PathPlanResult
    data class NoRoute(val reason: String) : PathPlanResult
}

object TrajectoryPlanner {
    private val planIds = AtomicLong()

    val envelope = CoarseKinematicEnvelope(
        maxHorizontalBlocksPerTick = 0.6,
        maxAscentBlocksPerTick = 0.5,
        maxDescentBlocksPerTick = 4.0,
    )

    private val moveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

    @Volatile
    var publications: Int = 0
        private set

    fun planAsync(
        player: ClientPlayerEntity,
        goal: Stance,
        config: PathingConfig,
        turnSpeed: Double,
        onSafePrefix: (PathingManager.PublishedPath) -> Unit,
        cursorFrame: () -> Int?,
        onImprovement: (PathingManager.PublishedPath) -> Unit,
    ): CompletableFuture<PathPlanResult> {
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = config.maxWalkOffDepth,
            allowJumpCandidates = config.allowJumpCandidates,
            maxJumpSpan = config.maxJumpSpan,
            maxJumpDrop = config.maxJumpDrop,
        )
        val seedConfig = MotionConstraints(
            maxFrames = config.maxFrames,
            maxYawDegreesPerFrame = turnSpeed,
            goalRadius = config.goalRadius,
            sprintModes = if (config.allowSprint) listOf(true, false) else listOf(false),
        )
        val initial = MovementSimulationState.from(player)
        val profile = PlayerPhysicsProfile.capture(player)
        val start = Stance(player.blockPos.x, player.blockPos.y, player.blockPos.z)

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

        val dumpDirectory = if (config.dumpFailedPlans) {
            MinecraftClient.getInstance().runDirectory.toPath().resolve(DUMP_DIRECTORY)
        } else {
            null
        }

        return CompletableFuture.supplyAsync {
            val started = System.currentTimeMillis()
            val moves = SimpleMoveLibrary.build(costs = moveCosts, options = moveOptions)
            val planner = CoarsePlanner(snapshot.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) {
                return@supplyAsync PathPlanResult.NoRoute("D* did not converge")
            }

            planner.expandField(
                extraTicks = FIELD_EXPANSION_TICKS,
                timeBudget = FIELD_EXPANSION_BUDGET,
                maxExpansions = FIELD_EXPANSION_NODES,
            )

            val route = planner.routePlan(snapshot.capturedWorldTime)
                ?: return@supplyAsync PathPlanResult.NoRoute("no coarse route to the goal")
            PlanningDebugChannel.publishRoute(route)

            val outcome = walkHorizon(
                route, planner, initial, profile, snapshot, seedConfig, cursorFrame,
                publish = { path, running -> if (running) onImprovement(path) else onSafePrefix(path) },
                started = started,
                lookahead = config.horizonRunwayFrames,
                commitFrames = config.horizonCommitFrames,
            )

            if (outcome is PathPlanResult.NoRoute) {
                dumpDirectory?.let { directory ->
                    runCatching {
                        PlanDump.write(
                            directory, snapshot, start, goal, initial, profile,
                            moveOptions, seedConfig, note = outcome.reason,
                        )
                    }.onFailure { LOG.error("Could not write the failed plan dump", it) }
                }
            }
            outcome
        }
    }

    internal fun walkHorizon(
        route: CoarseRoutePlan,
        planner: CoarsePlanner,
        initial: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        seedConfig: MotionConstraints,
        cursorFrame: () -> Int?,
        publish: (PathingManager.PublishedPath, Boolean) -> Unit,
        started: Long,
        lookahead: Int = HORIZON_FRAMES,
        commitFrames: Int = HORIZON_CHUNK_FRAMES,
        clock: SearchClock = SystemSearchClock(),
    ): PathPlanResult {
        val field = planner.valueField()
        var published = 0
        var last: PathingManager.PublishedPath? = null
        publications = 0

        val result = ValueFieldAnchorSearch.search(
            route, field, initial, profile, snapshot, seedConfig,
            ValueFieldSearchConfig(
                safePrefixFrames = commitFrames,
                safePrefixDelayMillis = HORIZON_BOOTSTRAP_DELAY_MS,
                horizonCommitFrames = commitFrames,
                horizonRunwayFrames = lookahead,
                localHorizonFrames = lookahead + commitFrames * HORIZON_WINDOW_CHUNKS,
                maxExpansions = HORIZON_EXPANSIONS,
                minCommitExpansions = HORIZON_MIN_COMMIT_EXPANSIONS,
                maxFinalCommitFrames = commitFrames * HORIZON_FINAL_COMMIT_CHUNKS,
            ),
            onSafePrefix = { step ->
                val path = publishedPath(
                    step, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = true,
                )
                publish(path, published > 0)
                last = path
                published++
                publications = published
            },
            cursorFrame = cursorFrame,
            clock = clock,
        )

        return when (result) {
            is MotionPlanResult.Success -> PathPlanResult.Planned(
                publishedPath(
                    result, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = false,
                )
            )

            else -> last?.let { PathPlanResult.Planned(it) }
                ?: PathPlanResult.NoRoute("no certified motion from the start state")
        }
    }

    private fun publishedPath(
        seed: MotionPlanResult.Success,
        route: CoarseRoutePlan,
        profile: PlayerPhysicsProfile,
        id: Long,
        planMillis: Long,
        partial: Boolean,
    ) = PathingManager.PublishedPath(
        route = route,
        plan = TrajectoryPlan.fromWalkingSeed(TrajectoryPlanId(id), seed, profile),
        profile = profile,
        parameters = seed.parameters,
        attempts = seed.attempts.size,
        planMillis = planMillis,
        finalGoal = route.goal,
        controlSegments = seed.controlSegments,
        spliceFrames = seed.spliceFrames,
        launchMarginFrames = seed.launchMarginFrames,
        partial = partial,
    )

    internal fun boundsCovering(start: Stance, goal: Stance) = SimulationSnapshotBounds(
        minX = minOf(start.x, goal.x) - MARGIN,
        minY = minOf(start.y, goal.y) - VERTICAL_EXCURSION - FALL_OBSERVATION_DEPTH,
        minZ = minOf(start.z, goal.z) - MARGIN,
        maxX = maxOf(start.x, goal.x) + MARGIN,
        maxY = maxOf(start.y, goal.y) + VERTICAL_EXCURSION + SimulationSnapshotBounds.CEILING_REACH,
        maxZ = maxOf(start.z, goal.z) + MARGIN,
    )

    internal fun SnapshotSimulationEnvironment.withinBudget(start: Stance, goal: Stance): CoarseVoxelView {
        val floor = minOf(start.y, goal.y) - VERTICAL_EXCURSION
        val ceiling = maxOf(start.y, goal.y) + VERTICAL_EXCURSION
        val budgeted = maxOf(floor, simulableStanceY.first)..minOf(ceiling, simulableStanceY.last)
        return object : CoarseVoxelView {
            override val simulableStanceY = budgeted
            override fun voxel(x: Int, y: Int, z: Int) = this@withinBudget.voxel(x, y, z)
            override fun collisionShape(x: Int, y: Int, z: Int) = this@withinBudget.collisionShape(x, y, z)
        }
    }

    private const val HORIZON_FRAMES = 20

    private const val HORIZON_CHUNK_FRAMES = 20


    private const val HORIZON_WINDOW_CHUNKS = 3

    private const val HORIZON_FINAL_COMMIT_CHUNKS = 2

    private const val HORIZON_BOOTSTRAP_DELAY_MS = 200L

    private const val HORIZON_MIN_COMMIT_EXPANSIONS = 400

    private const val HORIZON_EXPANSIONS = 2_000_000

    private const val FIELD_EXPANSION_TICKS = 36.0
    private val FIELD_EXPANSION_BUDGET = 60.milliseconds
    private const val FIELD_EXPANSION_NODES = 20_000

    private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"

    private const val MARGIN = 12

    private const val VERTICAL_EXCURSION = 8

    private const val FALL_OBSERVATION_DEPTH = 6
}
