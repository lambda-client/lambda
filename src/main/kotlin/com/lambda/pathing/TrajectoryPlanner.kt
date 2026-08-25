package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.movement.CoarseMoveRates
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.DebugChannelProbe
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.trajectory.SearchClock
import com.lambda.pathing.trajectory.SearchProbe
import com.lambda.pathing.trajectory.SystemSearchClock
import com.lambda.pathing.trajectory.WorldSyncResult
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.trajectory.PublishedPath
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
    data class Planned(val path: PublishedPath) : PathPlanResult
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
    val planningHorizonChunks: Int,
    val horizonRunwayFrames: Int,
    val horizonCommitFrames: Int,
    val coarseExpansionBudget: Int,
    val trajectoryExpansionBudget: Int,

    val settleInitial: Boolean,
    val frontierProbeRange: Int,
    val frontierSweepBudget: Int,
    val bootstrapDelayMillis: Long,
    val captureRetries: Int,
    val captureRetryMillis: Long,
    val dumpDirectory: java.nio.file.Path?,
    val startedMillis: Long,
)

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


    internal fun coarseState(
        preparation: TrajectoryPlanningPreparation,
        snapshot: SnapshotSimulationEnvironment,
    ) = CoarsePlanningState(
        snapshot, preparation.moveOptions, preparation.start, preparation.finalGoal,
        horizonChunks = preparation.planningHorizonChunks,
        frontierProbeRange = preparation.frontierProbeRange,
        frontierSweepBudget = preparation.frontierSweepBudget,
    )


    internal fun resolveStartStance(initial: MovementSimulationState): Stance {
        val base = Stance.of(initial.position, initial.onGround)
        if (!initial.onGround) return base
        val support = initial.supportingBlockPos ?: return base
        return if (support.x != base.x || support.z != base.z) {
            Stance(support.x, support.y + 1, support.z)
        } else base
    }

    internal fun resolveGoalStance(player: ClientPlayerEntity, goal: Stance): Stance {
        val world = player.entityWorld
        if (!world.isChunkLoaded(goal.x shr 4, goal.z shr 4)) return goal
        val pos = BlockPos(goal.x, goal.y, goal.z)
        val shape = world.getBlockState(pos).getCollisionShape(world, pos)
        if (shape.isEmpty) return goal
        val surface = shape.getMax(net.minecraft.util.math.Direction.Axis.Y)
        return if (surface > 0.0 && surface <= CoarseMoveRates.FREE_STEP_RISE) {
            goal.offset(0, 1, 0)
        } else goal
    }

    internal fun prepare(
        player: ClientPlayerEntity,
        goal: Stance,
        config: PathingConfig,
        turnSpeed: Double,
        cancellation: PlanningCancellation,

        initialOverride: MovementSimulationState? = null,

        settleInitial: Boolean = false,
    ): PlanningPreparationResult {
        val started = System.currentTimeMillis()
        @Suppress("NAME_SHADOWING")
        val goal = resolveGoalStance(player, goal)
        val moveOptions = SimpleMoveOptions(
            allowDiagonal = config.allowDiagonal,
            allowStepUp = config.allowStepUp,
            maxWalkOffDepth = config.maxWalkOffDepth,
            maxDropSpan = config.maxDropSpan,
            allowClimbing = config.allowClimbing,
            allowJumpCandidates = config.allowJumpCandidates,
            maxJumpSpan = config.maxJumpSpan,
            maxJumpDrop = config.maxJumpDrop,
            allowOffAxisJumps = config.allowOffAxisJumps,
            allowSlimeBounces = config.allowSlimeBounces,
            maxBounceDrop = config.maxBounceDrop,
        )
        val seedConfig = MotionConstraints(
            maxFrames = config.maxFrames,
            maxYawDegreesPerFrame = turnSpeed,
            goalRadius = config.goalRadius,
            maxSafeFallDistance = config.maxSafeFallDistance,
            sprintModes = if (config.allowSprint) listOf(true, false) else listOf(false),
        )
        val initial = initialOverride ?: MovementSimulationState.from(player)
        val profile = PlayerPhysicsProfile.capture(player)
        val start = resolveStartStance(initial)

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

        val dumpDirectory = if (config.dumpFailedPlans || java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")) {
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
                planningHorizonChunks = config.planningHorizonChunks,
                horizonRunwayFrames = config.horizonRunwayFrames,
                horizonCommitFrames = config.horizonCommitFrames,
                coarseExpansionBudget = config.coarseExpansionBudget,
                trajectoryExpansionBudget = config.trajectoryExpansionBudget,
                settleInitial = settleInitial,
                frontierProbeRange = config.frontierProbeRange,
                frontierSweepBudget = config.frontierSweepBudget,
                bootstrapDelayMillis = config.bootstrapDelayMillis.toLong(),
                captureRetries = config.captureRetries,
                captureRetryMillis = config.captureRetryMillis.toLong(),
                dumpDirectory = dumpDirectory,
                startedMillis = started,
            )
        )
    }

    internal fun planAsync(
        preparation: TrajectoryPlanningPreparation,
        world: PathingWorld,
        onSafePrefix: (PublishedPath) -> Unit,
        cursorFrame: () -> Int?,
        onImprovement: (PublishedPath) -> Unit,
        cancellation: PlanningCancellation,
        planningGeneration: Long,
        snapshotRevision: Long,
        coarseState: CoarsePlanningState = coarseState(preparation, world.snapshot),
    ): CompletableFuture<PathPlanResult> {
        if (cancellation.isCancelled) return CompletableFuture.completedFuture(PathPlanResult.Cancelled)
        val snapshot = world.snapshot
        val goal = preparation.finalGoal
        val start = preparation.start
        val profile = preparation.profile
        val moveOptions = preparation.moveOptions
        val seedConfig = preparation.seedConfig
        val dumpDirectory = preparation.dumpDirectory
        val started = preparation.startedMillis

        return CompletableFuture.supplyAsync({
            try {
                if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled

                awaitStartKnowledge(world, snapshot, start, goal, cancellation)
                val batch = world.drainEvents()
                val initial =
                    if (preparation.settleInitial) settleToRest(preparation.initial, profile, snapshot)
                    else preparation.initial
                coarseState.repairFrom(start, emptySet(), batch.changedChunkSet())
                val planner = coarseState.planner
                val coarseStarted = System.nanoTime()
                val coarse = planner.repair(
                    timeBudget = Duration.INFINITE,
                    maxExpansions = preparation.coarseExpansionBudget,
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
                            "coarse search did not converge within ${preparation.coarseExpansionBudget} expansions"
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

                PlanningDebugChannel.publishGraph(planner, initial.position)

                val route = coarseState.resolveRoute(
                    start, snapshotRevision, preparation.coarseExpansionBudget, world,
                ) { cancellation.isCancelled }
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

                val field = planner.valueField()
                val probe = DebugChannelProbe()
                val worldSync = ContinuousSyncPolicy(
                    world = world,
                    coarseState = coarseState,
                    field = field,
                    start = start,
                    finalGoal = preparation.finalGoal,
                    snapshotRevision = snapshotRevision,
                    coarseExpansionBudget = preparation.coarseExpansionBudget,
                    cancelled = { cancellation.isCancelled },
                    probe = probe,
                )
                var activeRoute = route
                val rerouter = RefusalRerouter(
                    planner = planner,
                    field = field,
                    reroute = {
                        coarseState.resolveRoute(
                            start, snapshotRevision, preparation.coarseExpansionBudget, world,
                        ) { cancellation.isCancelled }
                            ?.also { PlanningDebugChannel.publishRoute(it) }
                    },
                    cancelled = { cancellation.isCancelled },
                )
                val outcome = rerouter.walk(route) { attempted ->
                    activeRoute = attempted
                    walkHorizon(
                        attempted, planner, initial, profile, snapshot, seedConfig, cursorFrame,
                        publish = { path, running -> if (running) onImprovement(path) else onSafePrefix(path) },
                        started = started,
                        lookahead = preparation.horizonRunwayFrames,
                        commitFrames = preparation.horizonCommitFrames,
                        maxExpansions = preparation.trajectoryExpansionBudget,
                        bootstrapDelayMillis = preparation.bootstrapDelayMillis,
                        cancelled = { cancellation.isCancelled },
                        planningGeneration = planningGeneration,
                        finalGoal = preparation.finalGoal,
                        worldWait = { timeout -> world.awaitEvents(world.revision, timeout) },
                        worldSync = worldSync,
                        sectionCapturable = { sx, sz -> world.chunkCapturable(sx, sz) },
                        field = field,
                        probe = probe,
                    )
                }

                if (outcome is PathPlanResult.Failed) {
                    dumpDirectory?.let { directory ->
                        runCatching {

                            val journeyNote = buildString {
                                append(outcome.failure.message)
                                append("; route ").append(activeRoute.nodes.joinToString(" "))
                                val anchors = planner.optimisticAnchors
                                if (anchors.isNotEmpty()) {
                                    append("; anchors ").append(
                                        anchors.entries.joinToString(" ") {
                                            "${it.key}=%.1f".format(it.value)
                                        },
                                    )
                                }
                            }
                            PlanDump.write(
                                directory, snapshot, start, goal, initial, profile,
                                moveOptions, seedConfig, note = journeyNote,
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
        publish: (PublishedPath, Boolean) -> Unit,
        started: Long,
        lookahead: Int = HORIZON_FRAMES,
        commitFrames: Int = HORIZON_CHUNK_FRAMES,
        maxExpansions: Int = HORIZON_EXPANSIONS,
        bootstrapDelayMillis: Long = HORIZON_BOOTSTRAP_DELAY_MS,
        worldWait: ((Long) -> Boolean)? = null,
        worldSync: ((CoarseRoutePlan) -> WorldSyncResult)? = null,
        sectionCapturable: ((Int, Int) -> Boolean)? = null,
        clock: SearchClock = SystemSearchClock(),
        cancelled: () -> Boolean = { false },
        planningGeneration: Long = 0L,
        finalGoal: Stance = route.goal,
        field: CoarseValueField = planner.valueField(),
        probe: SearchProbe = SearchProbe.NONE,
    ): PathPlanResult {
        var published = 0
        var last: PublishedPath? = null

        val result = ValueFieldAnchorSearch.search(
            route, planner.moves.catalog, field, initial, profile, snapshot, seedConfig,
            ValueFieldSearchConfig(
                safePrefixFrames = commitFrames,
                safePrefixDelayMillis = bootstrapDelayMillis,
                horizonCommitFrames = commitFrames,
                horizonRunwayFrames = lookahead,
                localHorizonFrames = lookahead + commitFrames * HORIZON_WINDOW_CHUNKS +
                    climbHorizonFrames(route),
                maxExpansions = maxExpansions,
                minCommitExpansions = HORIZON_MIN_COMMIT_EXPANSIONS,
                maxFinalCommitFrames = commitFrames * HORIZON_FINAL_COMMIT_CHUNKS,
            ),
            onSafePrefix = { step ->
                if (!cancelled()) {
                    val sequence = published + 1
                    val path = publishedPath(
                        step, step.sourceRoute, profile, planIds.incrementAndGet(),
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
            worldWait = worldWait,
            worldSync = worldSync,
            sectionCapturable = sectionCapturable,
            probe = probe,
        )

        return when (result) {
            MotionPlanResult.Cancelled -> PathPlanResult.Cancelled
            is MotionPlanResult.Success -> PathPlanResult.Planned(
                publishedPath(
                    result, result.sourceRoute, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = false,
                    finalGoal = finalGoal,
                    planningGeneration = planningGeneration,
                    publicationSequence = published + 1,
                )
            )

            else -> last?.let { PathPlanResult.Planned(it) }
                ?: PathPlanResult.Failed(
                    run {
                        val stallIndex = (result as? MotionPlanResult.NoSafeStop)?.blockedProgress
                        PlanningFailure.NoCertifiedMotion(
                            "no certified motion from the start state (${describeRefusal(result)})",
                            stalledFrom = stallIndex?.let { route.nodes.getOrNull(it) },
                            stalledTo = stallIndex?.let { route.nodes.getOrNull(it + 1) },
                        )
                    }
                )
        }
    }

    private fun describeRefusal(result: MotionPlanResult): String = when (result) {
        is MotionPlanResult.NoSafeStop -> buildString {
            append(result.attemptCount).append(" attempts")
            result.blockedProgress?.let { append(", deepest route index ").append(it) }
            result.remainingStart?.let { append(", stalled at ").append(it) }
            result.nearest?.let { nearest ->
                append(
                    ", nearest miss %.2f blocks after %d frames"
                        .format(nearest.finalGoalError, nearest.simulatedFrames),
                )
                nearest.diagnostic?.let { append(" (").append(it).append(")") }
            }
        }
        is MotionPlanResult.UnstableReplay -> "unstable replay: ${result.reason}"
        is MotionPlanResult.UnsupportedRoute -> "unsupported movements ${result.movements}"
        else -> result::class.simpleName ?: "unknown"
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
    ) = PublishedPath(
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


    private fun awaitStartKnowledge(
        world: PathingWorld,
        snapshot: SnapshotSimulationEnvironment,
        start: Stance,
        goal: Stance,
        cancellation: PlanningCancellation,
    ) {
        val bodyDeadline = System.nanoTime() + COLD_START_BODY_WAIT_MILLIS * 1_000_000L
        while (!cancellation.isCancelled && System.nanoTime() < bodyDeadline &&
            !snapshot.isKnown(start.x, start.y - 1, start.z)
        ) {
            if (!world.awaitEvents(world.revision, 50)) break
        }

        if (world.chunkCapturable(goal.x shr 4, goal.z shr 4)) {
            val goalDeadline = System.nanoTime() + COLD_START_GOAL_WAIT_MILLIS * 1_000_000L
            while (!cancellation.isCancelled && System.nanoTime() < goalDeadline &&
                !FrontierAnchors.goalResolved(snapshot, goal)
            ) {
                if (!world.awaitEvents(world.revision, 50)) break
            }
        }

        val interestDeadline = System.nanoTime() + COLD_START_INTEREST_WAIT_MILLIS * 1_000_000L
        while (!cancellation.isCancelled && System.nanoTime() < interestDeadline &&
            world.pendingDemand > 0
        ) {
            if (!world.awaitEvents(world.revision, 50)) break
        }
    }

    private const val COLD_START_BODY_WAIT_MILLIS = 1_500L

    private const val COLD_START_GOAL_WAIT_MILLIS = 500L

    private const val COLD_START_INTEREST_WAIT_MILLIS = 1_500L

    private fun settleToRest(
        initial: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
    ): MovementSimulationState {
        var settled = initial
        val simulator = com.lambda.util.player.prediction.MovementSimulator(
            profile = profile,
            environment = snapshot,
            initialState = initial,
            skipEntityCollisions = true,
        )
        repeat(SETTLE_ROLLOUT_TICKS) {
            val step = simulator.tryTickMovement(
                com.lambda.util.player.prediction.MovementSimulationInput(),
            )
            if (step !is com.lambda.util.player.prediction.MovementSimulationStepResult.Advanced) {
                return initial
            }
            val next = simulator.state
            if (next.position == settled.position && next.velocity == settled.velocity) return settled
            settled = next
        }
        return initial
    }

    private const val SETTLE_ROLLOUT_TICKS = 40

    private fun climbHorizonFrames(route: CoarseRoutePlan): Int =
        route.edges.count { it.movement == MovementId.CLIMB } * CLIMB_HORIZON_FRAMES_PER_EDGE

    private const val CLIMB_HORIZON_FRAMES_PER_EDGE = 10

    private const val FIELD_EXPANSION_TICKS = 36.0
    private val FIELD_EXPANSION_BUDGET = 60.milliseconds
    private const val FIELD_EXPANSION_NODES = 20_000

    private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"

}
