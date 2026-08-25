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
    /** Roll [initial] to its zero-input fixed point on the worker before searching. */
    val settleInitial: Boolean,
    val frontierProbeRange: Int,
    val frontierSweepBudget: Int,
    val bootstrapDelayMillis: Long,
    val captureRetries: Int,
    val captureRetryMillis: Long,
    val dumpDirectory: java.nio.file.Path?,
    val startedMillis: Long,
)

/**
 * The streamed world, truncated to the chunks the planner has budgeted compute for.
 *
 * Past the granted ring every answer is the one unstreamed terrain gives -- unknown
 * cells, fail-closed collision -- so the whole optimistic-frontier machinery applies
 * unchanged: routes end at the ring's edge on an optimistic edge, and granting the next
 * ring is indistinguishable from a chunk arrival. Chunk-quantized because knowledge may
 * only ever grow, and growth is delivered through the same incremental repair real
 * arrivals use.
 */
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

/** Persistent coarse state for one final goal; only the planner executor mutates it. */
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
        // The ring follows the body: chunks it newly covers enter the graph through the
        // same repair path a streamed arrival does, so the field extends instead of
        // rebuilding, and everything already paid for stays paid for.
        //
        // Deliberately NO frontier probe here. Anchors are a fallback for when routing
        // through real terrain fails, not a standing feature: probed eagerly, a capture
        // hole inside streamed terrain masquerades as the world's edge, gets an anchor,
        // and D* routes the walk into a frontier that does not exist (live: a two-node
        // stub route onto an anchor four blocks from a fully streamed goal).
        val revealed = grantChunksAround(start)
        if (changed.isNotEmpty()) planner.worldChanged(changed)
        val arrivals = changedChunks + revealed
        if (arrivals.isNotEmpty()) planner.chunksChanged(arrivals)
    }

    /** Folds one drained event batch into the coarse graph. */
    fun applyEvents(changedChunks: Set<PathingChunk>) {
        if (changedChunks.isNotEmpty()) planner.chunksChanged(changedChunks)
    }

    private fun advanceFrontierFrom(start: Stance): Boolean = planner.advanceFrontier(
        FrontierAnchors.probe(planner.view, moves, start, goal, maxSteps = frontierProbeRange),
    )

    /**
     * Extracts a route, resolving the fiction its terminal stands on first.
     *
     * A route that ends on the optimistic frontier commits the walk toward terrain the
     * planner has never priced -- and walking is irreversible in a way planning is not:
     * a drop taken into what reveal later shows to be a one-way pocket leaves the body
     * with no coarse route at all (live: a bedrock-field leg parked in exactly such a
     * pocket at its ring's edge). So before a frontier terminal is handed to
     * certification, the ring is granted around it and the route re-extracted; a trap
     * re-routes here, while the body is still legs away, not after it is standing in
     * one. Bounded rounds keep the grant from racing to the goal: each round advances
     * knowledge one ring along the route, which is exactly the pace the walk needs.
     */
    fun resolveRoute(
        start: Stance,
        snapshotRevision: Long,
        maxExpansions: Int,
        world: PathingWorld? = null,
        cancelled: () -> Boolean = { false },
    ): CoarseRoutePlan? {
        // Real terrain first. Only when it cannot route does the optimistic frontier
        // come out, and once the goal routes for real again the fiction is retired --
        // its costs otherwise warp the value field the trajectory search steers by.
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
            // Pull knowledge toward the proposed terminal before committing to it: the
            // route is only as long as what is captured, and without this a walk over
            // fully loaded terrain chops into body-ring-sized legs.
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
            // Retirement can only remove fictional shortcuts; the real route stands,
            // but its cached costs must be re-extracted against the cleaned field.
            route = extractRoute(snapshotRevision, cancelled) ?: route
        }
        return route
    }

    private fun extractRoute(snapshotRevision: Long, cancelled: () -> Boolean): CoarseRoutePlan? =
        planner.routePlan(snapshotRevision, cancelled = cancelled)
            ?: planner.resynchronizedRoutePlan(snapshotRevision, cancelled = cancelled)
            // No route through what is streamed: before giving up, look for reachable
            // frontier the ray probe could not see -- the walk that "stops at the chunk
            // border" is usually this, not a real dead end.
            ?: run {
                if (planner.discoverReachableFrontier(cancelled)) {
                    planner.routePlan(snapshotRevision, cancelled = cancelled)
                } else null
            }

    private companion object {
        /** Ring-grants per plan toward a frontier terminal; each is one ring of reveal. */
        const val TERMINAL_GRANT_ROUNDS = 4

        const val TERMINAL_INTEREST_BLOCKS = 32
        const val TERMINAL_INTEREST_Y_BLOCKS = 16

        /** Per-round wait for terminal-area capture; ends early when capture idles. */
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

    /**
     * A goal naming a floor shape's own cell means standing on it, which is the cell above.
     *
     * A body on a carpet has its feet at y.0625, so the stance it occupies is the cell
     * *over* the carpet -- but a caller pointing at the carpet naturally names the
     * carpet's cell, which no body can ever occupy. Left unresolved that goal is simply
     * unreachable, and refusing it is technically right and practically useless. Only
     * shapes below the free step height resolve upward; a full block keeps the existing
     * convention that the named cell is where the body stands.
     */
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
        /**
         * Roots the plan at this state instead of the live player -- used to search the
         * successor leg from a running tape's certified terminal stop before the body
         * has arrived there.
         */
        initialOverride: MovementSimulationState? = null,
        /**
         * Settle [initialOverride] to its zero-input fixed point on the worker: a
         * certified stop still carries residual velocity the live body sheds before the
         * successor leg installs, and frame zero is compared at replay tolerance.
         */
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

        // The system property lets the gametest harness force dumps on without a
        // settings handle: a refusal in a live run then becomes a JVM fixture.
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
                // Knowledge is pulled by interest and captured on the client tick;
                // the worker waits on events -- bounded -- for the stances that decide
                // the first route, instead of blocking inside reads.
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

                // Published before the route is asked for, so the view survives the case
                // it is most wanted in: no route at all. The graph then shows exactly how
                // far the expansion reached and where its frontier stalled.
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

                // The search is continuous: world events fold into the coarse layer
                // between expansions, the route and guide field extend in place, and a
                // rollout that reads unknown terrain parks until knowledge arrives.
                val field = planner.valueField()
                val worldSync: (CoarseRoutePlan) -> com.lambda.pathing.trajectory.WorldSyncResult = sync@{ current ->
                    val batch = world.drainEvents()
                    if (batch.isEmpty) return@sync com.lambda.pathing.trajectory.WorldSyncResult.Quiet
                    // The coarse graph always absorbs events; the running search is
                    // only disturbed when they can matter to it. Knowledge ARRIVALS
                    // matter only while the route is still short of the true goal --
                    // a fully-routed walk over known terrain cannot be improved by
                    // more knowledge, only perturbed. MUTATIONS near the route always
                    // matter: the terrain under the plan changed.
                    coarseState.applyEvents(batch.changedChunkSet())
                    val extending = current.goal != preparation.finalGoal
                    val mutated = batch.mutations.isNotEmpty() || batch.chunks.isNotEmpty()
                    // Arrivals near a SETTLED route (already reaching the final goal
                    // through known terrain) wake blocked attempts and nothing else:
                    // new knowledge cannot improve that route, and evicting correct
                    // memos + resetting sweep state on every capture batch made the
                    // search churn instead of search. Only a still-extending route or
                    // an actual mutation earns the full treatment.
                    if (!routeNeighborhoodTouched(current, batch) || (!extending && !mutated)) {
                        return@sync com.lambda.pathing.trajectory.WorldSyncResult.Woken
                    }
                    field.invalidate(batch.sections)
                    // Re-extraction is expensive and resets the search's finish
                    // machinery: it happens only when the route itself is affected --
                    // still growing toward the goal, or events touching the sections
                    // the route's own edges actually read.
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
                            // The route and the anchors are journey state a fresh replay
                            // cannot reconstruct -- refusals that replayed clean from
                            // their dump turned out to differ exactly here.
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

    /**
     * The refusal's own evidence, flattened into the failure text.
     *
     * "No certified motion" reproduced live but not from its plan dump -- the cause
     * lives in state the dump cannot carry (capture availability, wall-clock pacing).
     * The search already knows how many rollouts it burned, how deep it got, and what
     * killed its nearest miss; a refusal that does not say so costs a reproduction run.
     */
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

    /** Whether any event section lies within the route's own neighbourhood. */
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

    /** How far past the route's own sections an event still concerns the search. */
    private const val ROUTE_NEIGHBORHOOD_SECTIONS = 2

    /** Conservative section-to-chunk mapping for coarse resynchronization. */
    private fun com.lambda.pathing.world.WorldEventBatch.changedChunkSet(): Set<PathingChunk> =
        chunks + sections.mapTo(HashSet()) { PathingChunk(it.x, it.z) }

    /**
     * Waits -- bounded -- for the knowledge the first route extraction needs.
     *
     * The body's supporting terrain is required (a start the graph cannot stand on
     * routes nowhere); the goal's is merely preferred, because an unstreamed goal is
     * exactly what the optimistic frontier exists for.
     */
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
        // The goal wait applies only when the goal could actually be captured: on a
        // live server events flow constantly, so "capture went idle" never breaks a
        // wait -- each wait must watch its own condition or it burns its full budget
        // on every leg.
        if (world.chunkCapturable(goal.x shr 4, goal.z shr 4)) {
            val goalDeadline = System.nanoTime() + COLD_START_GOAL_WAIT_MILLIS * 1_000_000L
            while (!cancellation.isCancelled && System.nanoTime() < goalDeadline &&
                !FrontierAnchors.goalResolved(snapshot, goal)
            ) {
                if (!world.awaitEvents(world.revision, 50)) break
            }
        }
        // The search must begin on complete local knowledge: URGENT capture still in
        // flight means the start neighbourhood has holes, and a search started into
        // holes drains into knowledge-waits while its publication clock runs -- the
        // first thing that certifies after the wake gets committed prematurely.
        // Streaming-ahead interest (body ring, corridor) never holds this up.
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

    /**
     * Rolls a certified stop to its true rest state: zero input until nothing changes.
     *
     * Vanilla clamps sub-0.003 velocities to zero, so the residual speed a stable stop
     * may legally carry (up to 0.012 b/t) dies within a few ticks -- but not before
     * sliding the body a few milliblocks past the tape's terminal position. A successor
     * leg rooted at the raw terminal fails its frame-zero comparison by exactly that
     * slide. No fixed point within the budget (ice, an unsettled fall) returns the
     * state unchanged; the hand-off then falls back to an ordinary replan.
     */
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

    /** Vanilla's velocity clamp settles a certified stop within a few ticks of this. */
    private const val SETTLE_ROLLOUT_TICKS = 40

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
