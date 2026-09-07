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
import com.lambda.pathing.session.ContinuousSyncPolicy
import com.lambda.pathing.session.PlanningCancellation
import com.lambda.pathing.world.changedChunkSet
import com.lambda.pathing.debug.DebugChannelProbe
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.trajectory.MotionPlanResult
import com.lambda.pathing.trajectory.SearchClock
import com.lambda.pathing.trajectory.SearchProbe
import com.lambda.pathing.trajectory.SearchExhaustion
import com.lambda.pathing.trajectory.SystemSearchClock
import com.lambda.pathing.trajectory.WorldSyncResult
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import com.lambda.pathing.trajectory.FrontierDomination

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
        capturable: (Int, Int) -> Boolean = FrontierAnchors.NOTHING_CAPTURABLE,
    ) = CoarsePlanningState(
        snapshot, preparation.moveOptions, preparation.start, preparation.finalGoal,
        horizonChunks = preparation.planningHorizonChunks,
        frontierSweepBudget = preparation.frontierSweepBudget,
        capturable = capturable,
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
            allowDeepDropJumps = config.allowDeepDropJumps,
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
                frontierSweepBudget = config.frontierSweepBudget,
                bootstrapDelayMillis = config.bootstrapDelayMillis.toLong(),
                plannerThreads = config.plannerThreads,
                improvementBudget = config.improvementBudget,
                momentumGait = config.momentumGait,
                momentumSkips = config.momentumSkips,
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
        adoptedSequenceProvider: () -> Long,
        onImprovement: (PublishedPath) -> Unit,
        cancellation: PlanningCancellation,
        planningGeneration: Long,
        snapshotRevision: Long,
        coarseState: CoarsePlanningState =
            coarseState(preparation, world.snapshot, world::chunkCapturable),
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

                val knowledgeStarted = System.nanoTime()
                awaitStartKnowledge(world, snapshot, start, goal, cancellation)
                val knowledgeMillis = (System.nanoTime() - knowledgeStarted) / 1_000_000L
                val batch = world.drainEvents()
                val initial = preparation.initial

                // A refused route and a refused walk write the same replayable dump.
                fun dumpFailure(kind: String, note: String) {
                    val directory = dumpDirectory ?: return
                    runCatching {
                        PlanDump.write(
                            directory, snapshot, start, goal, initial, profile,
                            moveOptions, seedConfig, note = note,
                        )
                    }.onFailure { LOG.error("Could not write the $kind dump", it) }
                }
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

                val fieldStarted = System.nanoTime()
                planner.expandField(
                    extraTicks = FIELD_EXPANSION_TICKS,
                    timeBudget = FIELD_EXPANSION_BUDGET,
                    maxExpansions = FIELD_EXPANSION_NODES,
                    cancelled = { cancellation.isCancelled },
                )
                val fieldMillis = (System.nanoTime() - fieldStarted) / 1_000_000L
                if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled

                PlanningDebugChannel.publishGraph(planner, initial.position)

                val routeStarted = System.nanoTime()
                val route = coarseState.resolveRoute(
                    start, snapshotRevision, preparation.coarseExpansionBudget, world,
                ) { cancellation.isCancelled }
                    ?: run {
                        if (cancellation.isCancelled) return@supplyAsync PathPlanResult.Cancelled
                        LOG.warn(
                            "No coarse route {} -> {} after {} expansions: {}",
                            start, goal, coarse.processedNodes, planner.routeFailureReport(),
                        )
                        dumpFailure("no-route", "no coarse route; ${planner.routeFailureReport()}")
                        return@supplyAsync PathPlanResult.Failed(
                            PlanningFailure.NoRoute("no coarse route to the goal")
                        )
                    }
                val routeMillis = (System.nanoTime() - routeStarted) / 1_000_000L
                PlanningDebugChannel.publishRoute(route)

                // Startup ledger: knowledge-wait is capture pacing, route covers
                // resolveRoute's grant rounds. See docs/decisions/startup.md.
                LOG.info(
                    "Planning startup {} -> {}: knowledge-wait={} ms, coarse={} ms, " +
                        "field={} ms, route={} ms ({}), since-request={} ms; capture so far: {}",
                    start, goal, knowledgeMillis, coarseMillis, fieldMillis, routeMillis,
                    coarseState.lastResolveReport,
                    System.currentTimeMillis() - started, world.captureLedger(),
                )

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
                val outcome = walkHorizon(
                    route, planner, initial, profile, snapshot, seedConfig, cursorFrame,
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
                    adoptedSequence = adoptedSequenceProvider,
                    probe = probe,
                    // Logged on both outcomes so two sessions at one goal compare field by field.
                    onExhaustion = { LOG.info("Trajectory search {} -> {}: {}", start, goal, it) },
                    parallelism = preparation.plannerThreads,
                    improvementBudget = preparation.improvementBudget,
                    momentumGait = preparation.momentumGait,
                    momentumSkips = preparation.momentumSkips,
                )

                if (outcome is PathPlanResult.Failed) {
                    val journeyNote = buildString {
                        append(outcome.failure.message)
                        append("; route ").append(route.nodes.joinToString(" "))
                        val anchors = planner.optimisticAnchors
                        if (anchors.isNotEmpty()) {
                            append("; anchors ").append(
                                anchors.entries.joinToString(" ") {
                                    "${it.key}=%.1f".format(it.value)
                                },
                            )
                        }
                    }
                    dumpFailure("failed plan", journeyNote)
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
        adoptedSequence: () -> Long = { Long.MAX_VALUE },
        probe: SearchProbe = SearchProbe.NONE,
        onExhaustion: ((SearchExhaustion) -> Unit)? = null,
        parallelism: Int = 1,
        maxTemperature: Double = 1.0,
        improvementBudget: Int = 0,
        frontierPerKey: Int = 3,
        branchExpansionHeadroomExpansions: Int = 1560,
        momentumSkips: Boolean = false,
        momentumGait: Boolean = false,
        /**
         * Wall-time cap on each mid-walk guide expansion. Virtual-clock harnesses must pass
         * [Duration.INFINITE]; the expansion-count cap still binds. See docs/decisions/determinism.md.
         */
        fieldExpansionBudget: kotlin.time.Duration = FIELD_EXPANSION_BUDGET,
        frontierDomination: FrontierDomination =
            FrontierDomination.FULL,
    ): PathPlanResult {
        var published = 0
        var last: PublishedPath? = null

        // Rollout workers for batch-parallel expansion. Daemon threads, owned by this
        // walk: the coordinator selects and applies, the workers only simulate.
        val pool = if (parallelism > 1) {
            java.util.concurrent.Executors.newFixedThreadPool(parallelism) { runnable ->
                Thread(runnable, "PathPlanner-rollout").apply { isDaemon = true }
            }
        } else null
        try {

        val result = ValueFieldAnchorSearch.search(
            route, planner.moves.catalog, field, initial, profile, snapshot, seedConfig,
            ValueFieldSearchConfig(
                safePrefixFrames = commitFrames,
                safePrefixDelayMillis = bootstrapDelayMillis,
                horizonCommitFrames = commitFrames,
                horizonRunwayFrames = lookahead,
                localHorizonFrames = lookahead + commitFrames * HORIZON_WINDOW_CHUNKS +
                    climbHorizonFrames(route),
                maxExpansions = minOf(maxExpansions, PER_WINDOW_EXPANSIONS),
                minCommitExpansions = HORIZON_MIN_COMMIT_EXPANSIONS,
                maxFinalCommitFrames = commitFrames * HORIZON_FINAL_COMMIT_CHUNKS,
                maxTemperature = maxTemperature,
                improvementBudget = improvementBudget,
                frontierPerKey = frontierPerKey,
                branchExpansionHeadroomExpansions = branchExpansionHeadroomExpansions,
                momentumSkips = momentumSkips,
                momentumGait = momentumGait,
                frontierDomination = frontierDomination,
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
            expandGuide = { marginTicks ->
                planner.expandField(
                    extraTicks = FIELD_EXPANSION_TICKS + marginTicks,
                    timeBudget = fieldExpansionBudget,
                    maxExpansions = FIELD_EXPANSION_NODES,
                )
            },
            adoptedSequence = adoptedSequence,
            finalGoal = finalGoal,
            probe = probe,
            onExhaustion = onExhaustion,
            parallelism = parallelism,
            executor = pool,
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

            else -> PathPlanResult.Failed(
                PlanningFailure.NoCertifiedMotion(
                    "the search dead-ended (${describeRefusal(result)})",
                )
            )
        }

        } finally {
            pool?.shutdownNow()
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
            result.exhaustion?.let { append("; ").append(it) }
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
        segments = seed.segments,
        arrivalTicksEstimate = seed.arrivalTicksEstimate,
        comparedRunningArrivalTicks = seed.comparedRunningArrivalTicks,
        comparedRunningSequence = seed.comparedRunningSequence,
    )

    private const val HORIZON_FRAMES = 20

    private const val HORIZON_CHUNK_FRAMES = 20

    // Exploration window past the committed root, in commit chunks; 2 is too little.
    // See docs/decisions/startup.md.
    private const val HORIZON_WINDOW_CHUNKS = 3

    private const val HORIZON_FINAL_COMMIT_CHUNKS = 2

    private const val HORIZON_BOOTSTRAP_DELAY_MS = 200L

    // Expansion budget per publication window (reset on publish and tape restart).
    private const val PER_WINDOW_EXPANSIONS = 120_000

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

    private fun climbHorizonFrames(route: CoarseRoutePlan): Int =
        route.edges.count { it.movement == MovementId.CLIMB } * CLIMB_HORIZON_FRAMES_PER_EDGE

    private const val CLIMB_HORIZON_FRAMES_PER_EDGE = 10

    private const val FIELD_EXPANSION_TICKS = 36.0
    private val FIELD_EXPANSION_BUDGET = 60.milliseconds
    private const val FIELD_EXPANSION_NODES = 20_000

    private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"

}
