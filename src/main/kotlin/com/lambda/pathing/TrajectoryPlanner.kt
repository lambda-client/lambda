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
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.trajectory.SearchClock
import com.lambda.pathing.trajectory.SystemSearchClock
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos

sealed interface PathPlanResult {
    data class Planned(val path: PathingManager.PublishedPath) : PathPlanResult
    data class Failed(val failure: PlanningFailure) : PathPlanResult
    data object Cancelled : PathPlanResult
}

internal sealed interface PlanningPreparationResult {
    data class Ready(val preparation: TrajectoryPlanningPreparation) : PlanningPreparationResult
    data class Failed(val failure: PlanningFailure) : PlanningPreparationResult
    data object Cancelled : PlanningPreparationResult
}

internal data class TrajectoryPlanningPreparation(
    val finalGoal: Stance,
    val bounds: SimulationSnapshotBounds,
    val moveOptions: SimpleMoveOptions,
    val seedConfig: MotionConstraints,
    val initial: MovementSimulationState,
    val profile: PlayerPhysicsProfile,
    val start: Stance,
    val horizonRunwayFrames: Int,
    val horizonCommitFrames: Int,
    val dumpDirectory: java.nio.file.Path?,
    val startedMillis: Long,
)

/** Persistent coarse state for one final goal; only the planner executor mutates it. */
internal class CoarsePlanningState(
	val snapshot: SnapshotSimulationEnvironment,
	moveOptions: SimpleMoveOptions,
	start: Stance,
	private val goal: Stance,
) {
    private val moves = SimpleMoveLibrary.build(costs = TrajectoryPlanner.moveCosts, options = moveOptions)
	val planner = CoarsePlanner(TrajectoryPlanner.coarseView(snapshot), moves, start, goal)

    fun repairFrom(start: Stance, changed: Set<VoxelPos>, changedChunks: Set<PathingChunk>) {
        planner.updateStart(start)
        if (changed.isNotEmpty()) planner.worldChanged(changed)
        if (changedChunks.isNotEmpty()) planner.chunksChanged(changedChunks)
        planner.advanceFrontier(FrontierAnchors.probe(snapshot, moves, start, goal))
    }
}

object TrajectoryPlanner {
    private val planIds = AtomicLong()

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "NeoLambda-PathPlanner").apply { isDaemon = true }
    }

    val envelope = CoarseKinematicEnvelope(
        maxHorizontalBlocksPerTick = 0.6,
        maxAscentBlocksPerTick = 0.5,
        maxDescentBlocksPerTick = 4.0,
    )

    internal val moveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

    internal fun coarseState(
        preparation: TrajectoryPlanningPreparation,
        snapshot: SnapshotSimulationEnvironment,
    ) = CoarsePlanningState(snapshot, preparation.moveOptions, preparation.start, preparation.finalGoal)

    internal fun coarseView(snapshot: SnapshotSimulationEnvironment): CoarseVoxelView = snapshot

    internal fun prepare(
        player: ClientPlayerEntity,
        goal: Stance,
        config: PathingConfig,
        turnSpeed: Double,
        cancellation: PlanningCancellation,
    ): PlanningPreparationResult {
        val started = System.currentTimeMillis()
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = config.maxWalkOffDepth,
            maxDropSpan = config.maxDropSpan,
            allowClimbing = config.allowClimbing,
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
        val start = Stance.of(initial.position, initial.onGround)

        if (cancellation.isCancelled) return PlanningPreparationResult.Cancelled

        if (!envelope.contains(initial.velocity)) {
            return PlanningPreparationResult.Failed(
                PlanningFailure.InvalidRequest(
                    "entry velocity %.3f b/t is outside the coarse kinematic envelope"
                        .format(initial.velocity.horizontalLength())
                )
            )
        }

        if (!player.entityWorld.worldBorder.contains(BlockPos(goal.x, goal.y, goal.z))) {
            return PlanningPreparationResult.Failed(
                PlanningFailure.InvalidRequest("destination is outside the world border")
            )
        }

        val world = player.entityWorld
        val bounds = SimulationSnapshotBounds(
            minX = Int.MIN_VALUE,
            minY = world.bottomY,
            minZ = Int.MIN_VALUE,
            maxX = Int.MAX_VALUE,
            maxY = world.bottomY + world.height - 1,
            maxZ = Int.MAX_VALUE,
        )
        if (goal.y !in bounds.simulableStanceY) {
            return PlanningPreparationResult.Failed(
                PlanningFailure.InvalidRequest("destination is outside the simulable build height")
            )
        }

        val dumpDirectory = if (config.dumpFailedPlans) {
            MinecraftClient.getInstance().runDirectory.toPath().resolve(DUMP_DIRECTORY)
        } else {
            null
        }
        return PlanningPreparationResult.Ready(
            TrajectoryPlanningPreparation(
                finalGoal = goal,
                bounds = bounds,
                moveOptions = moveOptions,
                seedConfig = seedConfig,
                initial = initial,
                profile = profile,
                start = start,
                horizonRunwayFrames = config.horizonRunwayFrames,
                horizonCommitFrames = config.horizonCommitFrames,
                dumpDirectory = dumpDirectory,
                startedMillis = started,
            )
        )
    }

    internal fun planAsync(
        preparation: TrajectoryPlanningPreparation,
        snapshot: SnapshotSimulationEnvironment,
        onSafePrefix: (PathingManager.PublishedPath) -> Unit,
        cursorFrame: () -> Int?,
        onImprovement: (PathingManager.PublishedPath) -> Unit,
        cancellation: PlanningCancellation,
        planningGeneration: Long,
        snapshotRevision: Long,
        coarseState: CoarsePlanningState = coarseState(preparation, snapshot),
        changedVoxels: Set<VoxelPos> = emptySet(),
        changedChunks: Set<PathingChunk> = emptySet(),
        onCoarseChangesApplied: () -> Unit = {},
        retireOptimisticTerrain: (Set<PathingChunk>) -> Unit = {},
    ): CompletableFuture<PathPlanResult> {
        if (cancellation.isCancelled) return CompletableFuture.completedFuture(PathPlanResult.Cancelled)
        val goal = preparation.finalGoal
        val start = preparation.start
        val initial = preparation.initial
        val profile = preparation.profile
        val moveOptions = preparation.moveOptions
        val seedConfig = preparation.seedConfig
        val dumpDirectory = preparation.dumpDirectory
        val started = preparation.startedMillis

        return CompletableFuture.supplyAsync({
            try {
                if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled
                // Placeholder terrain is replaced here, on the thread that owns the
                // graph, so the view a search reads never changes underneath it.
                retireOptimisticTerrain(changedChunks)
                coarseState.repairFrom(start, changedVoxels, changedChunks)
                onCoarseChangesApplied()
                val planner = coarseState.planner
                val coarseStarted = System.nanoTime()
                val coarse = planner.repair(
                    timeBudget = Duration.INFINITE,
                    maxExpansions = COARSE_ROUTE_EXPANSIONS,
                    cancelled = { cancellation.isCancelled },
                )
                val coarseMillis = (System.nanoTime() - coarseStarted) / 1_000_000L
                val snapshotStats = snapshot.storageStats()
                LOG.info(
                    "Coarse D* {} -> {}: {} expansions, {} graph nodes, {} snapshot sections, {} ms, converged={}",
                    start, goal, coarse.processedNodes, planner.graphSize, snapshotStats.sections,
                    coarseMillis, coarse.converged,
                )
                if (coarse.cancelled || cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled
                if (!coarse.converged) {
                    return@supplyAsync PathPlanResult.Failed(
                        PlanningFailure.ResourceLimit(
                            "coarse search did not converge within $COARSE_ROUTE_EXPANSIONS expansions"
                        )
                    )
                }

                planner.expandField(
                    extraTicks = FIELD_EXPANSION_TICKS,
                    timeBudget = FIELD_EXPANSION_BUDGET,
                    maxExpansions = FIELD_EXPANSION_NODES,
                    cancelled = { cancellation.isCancelled },
                )
                if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled

                // Published before the route is asked for, so the view survives the case
                // it is most wanted in: no route at all. The graph then shows exactly how
                // far the expansion reached and where its frontier stalled.
                PlanningDebugChannel.publishGraph(planner, initial.position)

                val route = planner.routePlan(
                    snapshotRevision,
                    cancelled = { cancellation.isCancelled },
                )
                    ?: planner.resynchronizedRoutePlan(
                        snapshotRevision,
                        cancelled = { cancellation.isCancelled },
                    )
                    ?: run {
                        if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled
                        LOG.warn(
                            "No coarse route {} -> {} after {} expansions: {}",
                            start, goal, coarse.processedNodes, planner.routeFailureReport(),
                        )
                        return@supplyAsync PathPlanResult.Failed(
                            PlanningFailure.NoRoute("no coarse route to the goal")
                        )
                    }
                PlanningDebugChannel.publishRoute(route)

                val outcome = walkHorizon(
                    route, planner, initial, profile, snapshot, seedConfig, cursorFrame,
                    publish = { path, running -> if (running) onImprovement(path) else onSafePrefix(path) },
                    started = started,
                    lookahead = preparation.horizonRunwayFrames,
                    commitFrames = preparation.horizonCommitFrames,
                    cancelled = { cancellation.isCancelled },
                    planningGeneration = planningGeneration,
                    finalGoal = preparation.finalGoal,
                )

                if (outcome is PathPlanResult.Failed) {
                    dumpDirectory?.let { directory ->
                        runCatching {
                            PlanDump.write(
                                directory, snapshot, start, goal, initial, profile,
                                moveOptions, seedConfig, note = outcome.failure.message,
                            )
                        }.onFailure { LOG.error("Could not write the failed plan dump", it) }
                    }
                }
                outcome
            } catch (_: CancellationException) {
                PathPlanResult.Cancelled
            }
        }, executor)
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
        cancelled: () -> Boolean = { false },
        planningGeneration: Long = 0L,
        finalGoal: Stance = route.goal,
    ): PathPlanResult {
        val field = planner.valueField()
        var published = 0
        var last: PathingManager.PublishedPath? = null

        val result = ValueFieldAnchorSearch.search(
            route, planner.moves.catalog, field, initial, profile, snapshot, seedConfig,
            ValueFieldSearchConfig(
                safePrefixFrames = commitFrames,
                safePrefixDelayMillis = HORIZON_BOOTSTRAP_DELAY_MS,
                horizonCommitFrames = commitFrames,
                horizonRunwayFrames = lookahead,
                localHorizonFrames = lookahead + commitFrames * HORIZON_WINDOW_CHUNKS +
                    climbHorizonFrames(route),
                maxExpansions = HORIZON_EXPANSIONS,
                minCommitExpansions = HORIZON_MIN_COMMIT_EXPANSIONS,
                maxFinalCommitFrames = commitFrames * HORIZON_FINAL_COMMIT_CHUNKS,
            ),
            onSafePrefix = { step ->
                if (!cancelled()) {
                    val sequence = published + 1
                    val path = publishedPath(
                        step, route, profile, planIds.incrementAndGet(),
                        System.currentTimeMillis() - started, partial = true,
                        finalGoal = finalGoal,
                        planningGeneration = planningGeneration,
                        publicationSequence = sequence,
                    )
                    publish(path, published > 0)
                    last = path
                    published++
                }
            },
            cursorFrame = cursorFrame,
            clock = clock,
            cancelled = cancelled,
        )

        return when (result) {
            MotionPlanResult.Cancelled -> PathPlanResult.Cancelled
            is MotionPlanResult.Success -> PathPlanResult.Planned(
                publishedPath(
                    result, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = false,
                    finalGoal = finalGoal,
                    planningGeneration = planningGeneration,
                    publicationSequence = published + 1,
                )
            )

            else -> last?.let { PathPlanResult.Planned(it) }
                ?: PathPlanResult.Failed(
                    PlanningFailure.NoCertifiedMotion("no certified motion from the start state")
                )
        }
    }

    private fun publishedPath(
        seed: MotionPlanResult.Success,
        route: CoarseRoutePlan,
        profile: PlayerPhysicsProfile,
        id: Long,
        planMillis: Long,
        partial: Boolean,
        finalGoal: Stance,
        planningGeneration: Long,
        publicationSequence: Int,
    ) = PathingManager.PublishedPath(
        route = route,
        plan = TrajectoryPlan.fromWalkingSeed(TrajectoryPlanId(id), seed, profile),
        profile = profile,
        parameters = seed.parameters,
        safeAnchorStance = seed.safeAnchorStance,
        safeAnchorFrame = seed.safeAnchorFrame,
        remainingGuideTicks = seed.remainingGuideTicks,
        attempts = seed.attemptCount,
        planMillis = planMillis,
        finalGoal = finalGoal,
        controlSegments = seed.controlSegments,
        spliceFrames = seed.spliceFrames,
        launchMarginFrames = seed.launchMarginFrames,
        partial = partial || route.goal != finalGoal,
        planningGeneration = planningGeneration,
        publicationSequence = publicationSequence,
    )

    private const val HORIZON_FRAMES = 20

    private const val HORIZON_CHUNK_FRAMES = 20


    private const val HORIZON_WINDOW_CHUNKS = 3

    private const val HORIZON_FINAL_COMMIT_CHUNKS = 2

    private const val HORIZON_BOOTSTRAP_DELAY_MS = 200L

    private const val HORIZON_MIN_COMMIT_EXPANSIONS = 400

    private const val HORIZON_EXPANSIONS = 2_000_000

    private const val COARSE_ROUTE_EXPANSIONS = 1_000_000

    /**
     * Extra horizon the route's climbing needs, in frames.
     *
     * A leg can only be committed where it can be brought to a stable grounded stop, and a
     * ladder has no ground anywhere along it. So the whole climb has to fit inside one
     * horizon window or nothing commits at all -- and at 0.117 blocks a tick against a
     * sprint's 0.216, the shipping 80-frame window buys about ten rungs. Past that the only
     * terminal the search can certify is back at the bottom, so the body parks at the foot
     * of the ladder and the heading fan walks it off sideways.
     *
     * Widening the window for the ladder that is actually on the route is close to free.
     * The window is bounded to keep the cost of searching a leg down, and that cost is
     * branching: a walk offers some thirty decisions per anchor where a climb offers one.
     * Routes with no climbing get exactly the window they had.
     */
    private fun climbHorizonFrames(route: CoarseRoutePlan): Int =
        route.edges.count { it.movement == MovementId.CLIMB } * CLIMB_HORIZON_FRAMES_PER_EDGE

    /** A rung is ~8.5 ticks at the vanilla climb rate; rounded up for the approach. */
    private const val CLIMB_HORIZON_FRAMES_PER_EDGE = 10

    private const val FIELD_EXPANSION_TICKS = 36.0
    private val FIELD_EXPANSION_BUDGET = 60.milliseconds
    private const val FIELD_EXPANSION_NODES = 20_000

    private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"

}
