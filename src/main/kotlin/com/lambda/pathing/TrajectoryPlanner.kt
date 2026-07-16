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
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveKind
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
import com.lambda.pathing.world.CoarseVoxelView
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
            is TrajectoryDiagnostic.OutsideSnapshot ->
                "read outside the captured snapshot at frame $frame near " +
                    "(${position.x}, ${position.y}, ${position.z}) -- the capture is too small, not the world"
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
            val moves = SimpleMoveLibrary.build(costs = moveCosts, options = moveOptions)
            val planner = CoarsePlanner(snapshot.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) {
                return@supplyAsync PathPlanResult.NoRoute("D* did not converge")
            }

            // Local controllers are only search pieces. A failed piece may publish a
            // safe *simulated* moving prefix to the next worker-local expansion, but
            // never to execution. The final result is one concatenated tape replayed
            // from the original state through the final stable stop. There are no
            // runtime stop-and-replan legs hidden behind trajectory windowing.
            val outcome = searchWithRerouting(planner, snapshot.capturedWorldTime) { route ->
                WalkingSeedSearch.searchContinuously(route, initial, profile, snapshot, seedConfig)
            } ?: return@supplyAsync PathPlanResult.NoRoute("no coarse route to the goal")

            when (val seed = outcome.result) {
                is WalkingSeedSearchResult.Success -> PathPlanResult.Planned(
                    PathingManager.PublishedPath(
                        route = outcome.route,
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
                        reroutes = outcome.reroutes,
                        launchMarginFrames = seed.launchMarginFrames,
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

    /** A completed search together with how many infeasible-jump reroutes it took. */
    internal data class FeedbackSearchOutcome(
        val route: CoarseRoutePlan,
        val result: WalkingSeedSearchResult,
        val reroutes: Int,
    )

    /**
     * Runs [search] on the current coarse route; if it refuses because a
     * `JUMP_CANDIDATE` edge cannot be certified, retires that edge in D* and reroutes,
     * up to [maxReroutes] times (M6 negative feedback, plan §5.3 / anytime design §5).
     *
     * Today a single infeasible jump the permissive mask admitted fails the whole plan,
     * because the coarse layer publishes one route with no alternative. Retiring the dead
     * edge and repairing lets D* find a detour instead. Only a jump is retired: a walk
     * that could not certify usually has no alternative and blacklisting it would spiral.
     *
     * Returns null only when there is no coarse route at all; otherwise it returns the
     * last result (a [WalkingSeedSearchResult.Success], an unsupported/unstable refusal,
     * or the closest [WalkingSeedSearchResult.NoSafeStop] once reroutes are exhausted).
     */
    internal fun searchWithRerouting(
        planner: CoarsePlanner,
        snapshotRevision: Long,
        maxReroutes: Int = MAX_REROUTES,
        search: (CoarseRoutePlan) -> WalkingSeedSearchResult,
    ): FeedbackSearchOutcome? {
        var reroutes = 0
        var lastRefusal: FeedbackSearchOutcome? = null
        while (true) {
            // A reroute may blacklist the graph into a corner with no route left; then the
            // best we can report is the closest refusal we already have.
            val route = planner.routePlan(snapshotRevision) ?: return lastRefusal
            when (val result = search(route)) {
                is WalkingSeedSearchResult.NoSafeStop -> {
                    lastRefusal = FeedbackSearchOutcome(route, result, reroutes)
                    val dead = result.deadEdge
                    if (dead == null || dead.kind != CoarseMoveKind.JUMP_CANDIDATE || reroutes >= maxReroutes) {
                        return lastRefusal
                    }
                    planner.blacklistEdge(dead.from, dead.to)
                    reroutes++
                }

                else -> return FeedbackSearchOutcome(route, result, reroutes)
            }
        }
    }

    /**
     * The snapshot must cover the whole search region plus jump/fall headroom.
     *
     * The vertical budget is two independent things, and conflating them is what made a
     * plain staircase unplannable: a route leaves the band its endpoints sit in
     * ([VERTICAL_EXCURSION]), *and* the simulator reads past whatever stance the route
     * reaches. Sized from the endpoints alone, a route climbing three blocks got one
     * block of jump headroom -- so every candidate that pressed jump on the high ground
     * died reading terrain nobody captured, while the same route entered one step higher
     * fit and certified.
     */
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
        }
    }

    /**
     * How many infeasible coarse jumps one plan may reroute around before giving up.
     * Each reroute is a cheap incremental D* repair; the bound keeps a pathological graph
     * (every jump admitted, none feasible) from turning one plan into an unbounded search.
     */
    private const val MAX_REROUTES = 8

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
