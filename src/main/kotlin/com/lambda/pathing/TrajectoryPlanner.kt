package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveRates
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
import com.lambda.pathing.world.PathingWorld
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

internal class PlanningHorizonView(
    private val backing: CoarseVoxelView,
    private val granted: Set<PathingChunk>,
) : CoarseVoxelView {
    private fun grantedAt(x: Int, z: Int): Boolean = PathingChunk(x shr 4, z shr 4) in granted

    override val simulableStanceY: IntRange get() = backing.simulableStanceY

    override fun isKnown(x: Int, y: Int, z: Int): Boolean = grantedAt(x, z) && backing.isKnown(x, y, z)

    override fun voxel(x: Int, y: Int, z: Int): com.lambda.pathing.world.CoarseVoxel =
        if (grantedAt(x, z)) backing.voxel(x, y, z) else com.lambda.pathing.world.CoarseVoxel.UNKNOWN

    override fun collisionShape(x: Int, y: Int, z: Int): net.minecraft.util.shape.VoxelShape? =
        if (grantedAt(x, z)) backing.collisionShape(x, y, z) else net.minecraft.util.shape.VoxelShapes.fullCube()

    override fun collisionClass(x: Int, y: Int, z: Int): com.lambda.pathing.world.CollisionClass =
        if (grantedAt(x, z)) backing.collisionClass(x, y, z) else com.lambda.pathing.world.CollisionClass.FULL
}

internal class CoarsePlanningState(
    val snapshot: SnapshotSimulationEnvironment,
    moveOptions: SimpleMoveOptions,
    start: Stance,
    private val goal: Stance,
    val horizonChunks: Int = 0,
    private val frontierProbeRange: Int = 512,
    frontierSweepBudget: Int = 40_000,
) {
    private val moves = SimpleMoveLibrary.build(costs = TrajectoryPlanner.moveCosts, options = moveOptions)

    private val grantedChunks = HashSet<PathingChunk>()

    private val view: CoarseVoxelView =
        if (horizonChunks <= 0) TrajectoryPlanner.coarseView(snapshot)
        else PlanningHorizonView(TrajectoryPlanner.coarseView(snapshot), grantedChunks)

    init {
        grantChunksAround(start)
    }

    val planner = CoarsePlanner(view, moves, start, goal, sweepBudget = frontierSweepBudget)

    fun repairFrom(start: Stance, changed: Set<VoxelPos>, changedChunks: Set<PathingChunk>) {
        planner.updateStart(start)

        val revealed = grantChunksAround(start)
        if (changed.isNotEmpty()) planner.worldChanged(changed)
        val arrivals = changedChunks + revealed
        if (arrivals.isNotEmpty()) planner.chunksChanged(arrivals)
    }

    fun applyEvents(changedChunks: Set<PathingChunk>) {
        if (changedChunks.isNotEmpty()) planner.chunksChanged(changedChunks)
    }

    private fun advanceFrontierFrom(start: Stance): Boolean = planner.advanceFrontier(
        FrontierAnchors.probe(planner.view, moves, start, goal, maxSteps = frontierProbeRange),
    )

    fun resolveRoute(
        start: Stance,
        snapshotRevision: Long,
        maxExpansions: Int,
        world: PathingWorld? = null,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {

        var route = extractRoute(snapshotRevision, cancelled)
        if (route == null && advanceFrontierFrom(start)) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )
            route = extractRoute(snapshotRevision, cancelled)
        }
        if (route == null) return null
        var rounds = 0
        while (route!!.goal != goal && rounds++ < TERMINAL_GRANT_ROUNDS) {
            if (cancelled()) return route
            val terminal = route.goal

            world?.let { w ->
                w.interestBlocks(
                    terminal.x - TERMINAL_INTEREST_BLOCKS, terminal.y - TERMINAL_INTEREST_Y_BLOCKS,
                    terminal.z - TERMINAL_INTEREST_BLOCKS,
                    terminal.x + TERMINAL_INTEREST_BLOCKS, terminal.y + TERMINAL_INTEREST_Y_BLOCKS,
                    terminal.z + TERMINAL_INTEREST_BLOCKS,
                    com.lambda.pathing.world.InterestTier.DEMAND,
                )
                var waited = 0L
                while (waited < TERMINAL_KNOWLEDGE_WAIT_MILLIS && !cancelled() && w.pendingDemand > 0) {
                    if (!w.awaitEvents(w.revision, 50)) break
                    waited += 50
                }
                val batch = w.drainEvents()
                if (!batch.isEmpty) {
                    planner.chunksChanged(batch.chunks + batch.sections.mapTo(HashSet()) { PathingChunk(it.x, it.z) })
                }
            }
            val revealed = grantChunksAround(terminal)
            if (revealed.isEmpty() && world == null) break
            if (revealed.isNotEmpty()) planner.chunksChanged(revealed)
            advanceFrontierFrom(start)
            planner.repair(
                timeBudget = Duration.INFINITE,
                maxExpansions = maxExpansions,
                cancelled = cancelled,
            )
            route = extractRoute(snapshotRevision, cancelled) ?: return null
            if (route.goal == terminal) break
        }
        if (route.goal == goal && planner.retireAllAnchors()) {
            planner.repair(
                timeBudget = Duration.INFINITE, maxExpansions = maxExpansions, cancelled = cancelled,
            )

            route = extractRoute(snapshotRevision, cancelled) ?: route
        }
        return route
    }

    private fun extractRoute(snapshotRevision: Long, cancelled: () -> Boolean): CoarseRoutePlan? =
        planner.routePlan(snapshotRevision, cancelled = cancelled)
            ?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled)

            ?: run {
                if (planner.discoverReachableFrontier(cancelled)) {
                    planner.routePlan(snapshotRevision, cancelled = cancelled)
                } else null
            }

    private companion object {

        const val TERMINAL_GRANT_ROUNDS = 4

        const val TERMINAL_INTEREST_BLOCKS = 32
        const val TERMINAL_INTEREST_Y_BLOCKS = 16

        const val TERMINAL_KNOWLEDGE_WAIT_MILLIS = 400L
    }

    private fun grantChunksAround(start: Stance): Set<PathingChunk> {
        if (horizonChunks <= 0) return emptySet()
        val revealed = HashSet<PathingChunk>()
        val centerX = start.x shr 4
        val centerZ = start.z shr 4
        for (dx in -horizonChunks..horizonChunks) {
            for (dz in -horizonChunks..horizonChunks) {
                val chunk = PathingChunk(centerX + dx, centerZ + dz)
                if (grantedChunks.add(chunk)) revealed += chunk
            }
        }
        return revealed
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
    ) = CoarsePlanningState(
        snapshot, preparation.moveOptions, preparation.start, preparation.finalGoal,
        horizonChunks = preparation.planningHorizonChunks,
        frontierProbeRange = preparation.frontierProbeRange,
        frontierSweepBudget = preparation.frontierSweepBudget,
    )

    internal fun coarseView(snapshot: SnapshotSimulationEnvironment): CoarseVoxelView = snapshot

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
        onSafePrefix: (PathingManager.PublishedPath) -> Unit,
        cursorFrame: () -> Int?,
        onImprovement: (PathingManager.PublishedPath) -> Unit,
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
                val worldSync: (CoarseRoutePlan) -> com.lambda.pathing.trajectory.WorldSyncResult = sync@{ current ->
                    val batch = world.drainEvents()
                    if (batch.isEmpty) return@sync com.lambda.pathing.trajectory.WorldSyncResult.Quiet

                    coarseState.applyEvents(batch.changedChunkSet())
                    val extending = current.goal != preparation.finalGoal
                    val mutated = batch.mutations.isNotEmpty() || batch.chunks.isNotEmpty()

                    if (!routeNeighborhoodTouched(current, batch) || (!extending && !mutated)) {
                        return@sync com.lambda.pathing.trajectory.WorldSyncResult.Woken
                    }
                    field.invalidate(batch.sections)

                    val routeSections = current.dependencies.mapTo(HashSet()) {
                        com.lambda.pathing.world.PathingSection.containing(it)
                    }
                    val routeAffected = extending ||
                        batch.mutations.any { it in routeSections } ||
                        batch.sections.any { it in routeSections }
                    if (java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")) {
                        LOG.info(
                            "[sync] sections={} mutations={} chunks={} routeAffected={} extending={}",
                            batch.sections.size, batch.mutations.size, batch.chunks.size,
                            routeAffected, extending,
                        )
                    }
                    val next = if (routeAffected) {
                        coarseState.resolveRoute(
                            start, snapshotRevision, preparation.coarseExpansionBudget,
                        ) { cancellation.isCancelled }
                    } else null
                    next?.goal?.let { terminal ->
                        world.interestBlocks(
                            terminal.x - 32, terminal.y - 16, terminal.z - 32,
                            terminal.x + 32, terminal.y + 16, terminal.z + 32,
                            com.lambda.pathing.world.InterestTier.CORRIDOR,
                        )
                    }
                    com.lambda.pathing.trajectory.WorldSyncResult.Changed(next)
                }
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
                )

                if (outcome is PathPlanResult.Failed) {
                    dumpDirectory?.let { directory ->
                        runCatching {

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
        publish: (PathingManager.PublishedPath, Boolean) -> Unit,
        started: Long,
        lookahead: Int = HORIZON_FRAMES,
        commitFrames: Int = HORIZON_CHUNK_FRAMES,
        maxExpansions: Int = HORIZON_EXPANSIONS,
        bootstrapDelayMillis: Long = HORIZON_BOOTSTRAP_DELAY_MS,
        worldWait: ((Long) -> Boolean)? = null,
        worldSync: ((CoarseRoutePlan) -> com.lambda.pathing.trajectory.WorldSyncResult)? = null,
        sectionCapturable: ((Int, Int) -> Boolean)? = null,
        clock: SearchClock = SystemSearchClock(),
        cancelled: () -> Boolean = { false },
        planningGeneration: Long = 0L,
        finalGoal: Stance = route.goal,
        field: com.lambda.pathing.coarse.CoarseValueField = planner.valueField(),
    ): PathPlanResult {
        var published = 0
        var last: PathingManager.PublishedPath? = null

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
            worldWait = worldWait,
            worldSync = worldSync,
            sectionCapturable = sectionCapturable,
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
                    PlanningFailure.NoCertifiedMotion(
                        "no certified motion from the start state (${describeRefusal(result)})"
                    )
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

    private fun routeNeighborhoodTouched(
        route: CoarseRoutePlan,
        batch: com.lambda.pathing.world.WorldEventBatch,
    ): Boolean {
        if (batch.sections.isEmpty() && batch.chunks.isEmpty()) return false
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
        route.nodes.forEach { node ->
            minX = minOf(minX, node.x shr 4); maxX = maxOf(maxX, node.x shr 4)
            minY = minOf(minY, node.y shr 4); maxY = maxOf(maxY, node.y shr 4)
            minZ = minOf(minZ, node.z shr 4); maxZ = maxOf(maxZ, node.z shr 4)
        }
        val m = ROUTE_NEIGHBORHOOD_SECTIONS
        val sectionHit = batch.sections.any {
            it.x in (minX - m)..(maxX + m) && it.y in (minY - m)..(maxY + m) && it.z in (minZ - m)..(maxZ + m)
        }
        if (sectionHit) return true
        return batch.chunks.any { it.x in (minX - m)..(maxX + m) && it.z in (minZ - m)..(maxZ + m) }
    }

    private const val ROUTE_NEIGHBORHOOD_SECTIONS = 2

    private fun com.lambda.pathing.world.WorldEventBatch.changedChunkSet(): Set<PathingChunk> =
        chunks + sections.mapTo(HashSet()) { PathingChunk(it.x, it.z) }

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
