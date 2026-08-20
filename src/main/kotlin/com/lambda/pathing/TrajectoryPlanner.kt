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
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
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

    /** A refusal, not a failure: no parameter set reached a certified stop. */
    data class NoSafeStop(val result: MotionPlanResult.NoSafeStop) : PathPlanResult {
        /**
         * Names *why*, not just that it refused. The dominant diagnostic is the one
         * failure-directed branching should attack first.
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
                val closest = result.nearest?.let {
                    "; closest ended %.2f blocks from the remaining goal at %.3f b/t: %s".format(
                        it.finalGoalError, it.finalHorizontalSpeed, it.diagnostic.describe(),
                    )
                } ?: ""
                return "no certified continuous trajectory from ${attempts.size} attempts: " +
                    "$byKind$closest"
            }

        private fun TrajectoryDiagnostic?.describe(): String = when (this) {
            null -> "no diagnostic"
            is TrajectoryDiagnostic.HorizontalCollision ->
                "ground collision at frame $frame near ${position.short()}"
            is TrajectoryDiagnostic.HeadBonk ->
                "head bonk at frame $frame near ${position.short()}"
            is TrajectoryDiagnostic.FellBelowRoute ->
                "fell %.2f blocks below the route at frame %d".format(depth, frame)
            is TrajectoryDiagnostic.HarmfulFall ->
                "unsafe %.2f-block fall at frame %d".format(fallDistance, frame)
            is TrajectoryDiagnostic.NoStop ->
                "no stable stop (%.2f blocks away, %.3f b/t) after %d frames"
                    .format(goalError, speed, frame)
            is TrajectoryDiagnostic.UnsupportedPhysics ->
                "unsupported physics at frame $frame: $reason"
            is TrajectoryDiagnostic.OutsideSnapshot ->
                "read outside the captured snapshot at frame $frame near " +
                    "(${position.x}, ${position.y}, ${position.z}) -- the capture is too small, not the world"
        }

        private fun net.minecraft.util.math.Vec3d.short(): String =
            "(%.2f, %.2f, %.2f)".format(x, y, z)
    }
}

/**
 * Captures an immutable snapshot on the client thread, then runs D* and the horizon
 * search off-thread. Planning never touches live world state.
 */
object TrajectoryPlanner {
    private val planIds = AtomicLong()

    /**
     * Bounds the entry velocity a plan may start from. 0.6 b/t sits above any real
     * horizontal speed, including a sprint jump, so a start inside it keeps the coarse
     * costs admissible. This is only the velocity gate now; edge costs come from
     * [CoarseMoveCosts.measured].
     */
    val envelope = CoarseKinematicEnvelope(
        maxHorizontalBlocksPerTick = 0.6,
        maxAscentBlocksPerTick = 0.5,
        maxDescentBlocksPerTick = 4.0,
    )

    /**
     * Edge costs in measured expected ticks, not the uniform-0.6 lower bound. A climb is
     * priced at its real ~10 ticks, a gap jump at its airborne time, a stride at its
     * sprinted pace -- so the coarse graph prefers genuinely fast lines instead of being
     * indifferent between equal-lower-bound routes. The per-edge toll leans off slaloms.
     */
    private val moveCosts = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0)

    /**
     * How much motion the horizon committed on the last journey, for the completion
     * report.
     */
    @Volatile
    var attempts: Int = 0
        private set

    @Volatile
    var improvements: Int = 0
        private set

    /**
     * Must be called on the client thread: it reads the live player and world.
     *
     * [config] is the requester's own planning config, snapshotted here so the worker
     * never reads a setting that could change under it. [turnSpeed] is one sample of
     * the requester's rotation turn speed, taken here for the same reason: it caps the
     * per-frame yaw step every simulated controller may plan, so the tape never asks
     * for a turn the rotation manager was not configured to deliver.
     */
    fun planAsync(
        player: ClientPlayerEntity,
        goal: Stance,
        config: PathingConfig,
        turnSpeed: Double,
        /**
         * Receives a safe partial plan as soon as one exists, so the body can start
         * walking while the rest is still being searched. Called on the worker thread;
         * the manager marshals it.
         */
        onSafePrefix: (PathingManager.PublishedPath) -> Unit,
        /** Frame the executor is about to run, or null when nothing is executing yet. */
        cursorFrame: () -> Int?,
        /** Receives each successively better plan while the body is already walking. */
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
        // Resolved on the client thread; the worker only writes to it.
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

            // A value-steered search only goes where the field has labels, so the field
            // has to be wider than the one corridor `computeShortestPath` settles for.
            // Bounded: it is paid once per plan, before any rollout.
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

    /**
     * Walks toward the goal out of one search that never stops or restarts.
     *
     * The alternative -- proving a complete tape to the goal before the body moves --
     * commits so hard that improving it later means beating the entire remainder, and
     * spends its budget certifying a future that will be re-decided long before the body
     * gets there.
     *
     * But the naive horizon is worse: searching a long lookahead, committing the first
     * twenty frames and discarding the rest, then searching again from scratch. Each step
     * costs a full search, and when a step takes longer than the motion it committed the
     * body reaches its brake and stops -- a walk that visibly ran, halted, waited, and ran
     * again.
     *
     * So the search runs *once*. It commits a little motion whenever the body is about to
     * run out, re-roots onto what it committed -- every branch leaving the walked line is
     * unadoptable anyway -- and carries on with the tree it already has. Committing is
     * deferred until the runway is short precisely because the search keeps improving
     * until then: the latest possible commitment is the best informed one.
     *
     * The safety property is unchanged and is what makes this legal: **every published
     * tape ends in a certified stop**. If the search ever fails to extend, the body runs
     * the brake it was already holding and plans again from rest.
     */
    /** Test seam: the horizon walk without the async plumbing around it. */
    internal fun walkHorizonForTest(
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
    ): PathPlanResult = walkHorizon(
        route, planner, initial, profile, snapshot, seedConfig, cursorFrame, publish, started,
        lookahead, commitFrames, clock,
    )

    private fun walkHorizon(
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
        attempts = 0
        improvements = 0

        val result = ValueFieldAnchorSearch.search(
            route, field, initial, profile, snapshot, seedConfig,
            ValueFieldSearchConfig(
                // The first commitment is small and immediate: the body has to start
                // moving. Every one after it waits for the runway to run short.
                safePrefixFrames = commitFrames,
                // Long enough for candidates to reach the horizon, so the first
                // commitment is chosen rather than taken. The body stands still for this
                // long; everything after it is decided while walking.
                safePrefixDelayMillis = HORIZON_BOOTSTRAP_DELAY_MS,
                horizonCommitFrames = commitFrames,
                horizonRunwayFrames = lookahead,
                commitContinuously = true,
                // The local window. Beyond it the search parks candidates instead of
                // expanding them: D* has already answered where to go, and re-answering it
                // here spends the budget on a future that gets re-decided anyway. The goal
                // re-enters only when the field says the body is inside braking distance,
                // which is what the terminal sweep already handles.
                localHorizonFrames = lookahead + commitFrames * HORIZON_WINDOW_CHUNKS,
                // Breadth over exactly the stretch about to be locked in, so there is
                // something to choose between when it is.
                rootFanFrames = commitFrames * HORIZON_FAN_CHUNKS,
                // One search now spans the whole walk instead of one plan's worth of
                // latency, so the budget has to span it too. The default is sized for a
                // search the body is waiting on; this one runs while the body walks, and
                // exhausting it strands the walk short of the goal.
                maxExpansions = HORIZON_EXPANSIONS,
                minCommitExpansions = HORIZON_MIN_COMMIT_EXPANSIONS,
                maxFinalCommitFrames = commitFrames * HORIZON_FINAL_COMMIT_CHUNKS,
                // Off, and only just. The headless horizon corpus prefers it on (1229 vs
                // ~1250 frames) and the live corpus prefers it off (1009 vs 1012) -- both
                // margins under a third of a percent, and they disagree because the live
                // scenarios are short, where every extra launch variant is search depth
                // taken from somewhere that needed it more. Following the shipping gate.
                offerBrakeTicks = false,
            ),
            onSafePrefix = { step ->
                val path = publishedPath(
                    step, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = true,
                )
                publish(path, published > 0)
                last = path
                published++
                improvements++
            },
            cursorFrame = cursorFrame,
            clock = clock,
        )
        attempts = published

        return when (result) {
            is MotionPlanResult.Success -> PathPlanResult.Planned(
                publishedPath(
                    result, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, partial = false,
                )
            )

            // Nothing more could be committed. The body is already holding a certified
            // brake, so it stops there and the manager plans the next leg from rest --
            // the behaviour that existed before horizons, kept as the failure mode.
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

    /**
     * Restricts the coarse search to stances this plan actually budgeted for.
     *
     * [SimulationSnapshotBounds.simulableStanceY] answers a narrower question -- can a
     * body *stand* here -- and its floor is only two blocks above the capture, because
     * that is all a grounded body reads. But a walk that leaves the route *falls*, and
     * `FellBelowRoute` can only report a frame the rollout actually recorded. A stance
     * sitting at the very bottom of the snapshot is simulable and still useless: the
     * first tick of a fall from it reads past the floor, so the frame is dropped and the
     * fall is misreported as terrain we never captured. Deepening the capture cannot fix
     * that, because the simulable floor is defined relative to it and moves down too.
     *
     * So the planner clamps the band to its own excursion budget instead, which leaves
     * [FALL_OBSERVATION_DEPTH] blocks under the lowest stance a route may use.
     */
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

    /**
     * Committed motion kept ahead of the body before another commitment is made.
     *
     * How late a decision may be left. Later is better -- every tick not yet committed is
     * a tick the search is still improving it -- and measured on the live corpus that is
     * exactly what happens: runway 25 gives 1028 frames, 40 gives 1025, 60 gives 1044,
     * 200 gives 1099. But the shorter settings leave the body catching the brake it is
     * holding when a search runs long: 25 and 40 each stopped once across the corpus,
     * where 60 stopped never. Not stopping is worth nineteen frames.
     */
    private const val HORIZON_FRAMES = 20

    /** Motion each extension commits; the granularity at which the future is decided. */
    private const val HORIZON_CHUNK_FRAMES = 20

    /**
     * Commit-lengths over which the search fans out rather than dives; zero disables it.
     *
     * Off, on measurement. Keeping branches out of each other's dominance buckets is the
     * only way to stop the population collapsing -- and it does work -- but it means
     * discarding the pruning map at every commitment, because branch identity is relative
     * to the committed end. The search then re-explores ground it had already dismissed,
     * falls behind the body, and the walk turns back into stop-and-go: 22 halts across the
     * live corpus against none without it.
     *
     * And it bought little. With it on, the number of genuinely distinct commitments
     * available was 6 and 3 at a couple of moments and *one* at the median -- because the
     * value field has already decided which way to go, so good candidates converge on the
     * same next stance. The population is real; the choice usually is not.
     *
     * Left in and switchable rather than deleted: on open terrain, where the field leaves
     * real alternatives, the trade may well go the other way.
     */
    private const val HORIZON_FAN_CHUNKS = 0

    /** Chunks of growing room past the runway; the window candidates compete inside. */
    private const val HORIZON_WINDOW_CHUNKS = 3

    /** Commit-lengths the arriving publication may cover; the endgame is committed too. */
    private const val HORIZON_FINAL_COMMIT_CHUNKS = 2

    /** How long the body waits for a population before the first line is committed. */
    private const val HORIZON_BOOTSTRAP_DELAY_MS = 200L

    /** Exploration a commitment must be backed by, so its quality does not vary with load. */
    private const val HORIZON_MIN_COMMIT_EXPANSIONS = 400

    /** Expansions one horizon search may spend across an entire walk. */
    private const val HORIZON_EXPANSIONS = 2_000_000

    /**
     * How far past the optimal cost the value field is labelled for a value-steered
     * search. Roughly ten blocks of detour room -- enough to cut a corner or take a
     * parallel line, not so much that the plan pays for labelling the whole region.
     */
    private const val FIELD_EXPANSION_TICKS = 36.0
    private val FIELD_EXPANSION_BUDGET = 60.milliseconds
    private const val FIELD_EXPANSION_NODES = 20_000

    private const val DUMP_DIRECTORY = "neolambda/pathing-dumps"

    private const val MARGIN = 12

    /** How far above or below its endpoints a coarse route may travel. */
    private const val VERTICAL_EXCURSION = 8

    /**
     * Room under the lowest usable stance for a fall to be *diagnosed* rather than
     * truncated.
     *
     * A rollout keeps simulating after it leaves the route, so a body that walks into a
     * hole falls for the rest of the tape. `FellBelowRoute` is only reported for a frame
     * that was recorded, so the capture has to outlast the first ticks of that fall.
     */
    private const val FALL_OBSERVATION_DEPTH = 6
}
