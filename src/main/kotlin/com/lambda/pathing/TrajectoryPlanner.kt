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
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.neural.NeuralPolicyHolder
import com.lambda.pathing.neural.NeuralTrajectoryDiscovery
import com.lambda.pathing.trajectory.MotionAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.InputTape
import com.lambda.pathing.trajectory.TrajectoryRolloutEngine
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.trajectory.SegmentCost
import com.lambda.pathing.trajectory.SegmentCosts
import com.lambda.pathing.trajectory.TrajectoryDecision
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TrajectoryPlanDecisions
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryPlanId
import com.lambda.pathing.trajectory.WalkingSeedSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
        onSafePrefix: ((PathingManager.PublishedPath) -> Unit)? = null,
        /**
         * Frame the executor is about to run, or null when nothing is executing yet.
         * Refinement needs it: only the tape *ahead* of the body may be replaced.
         */
        cursorFrame: (() -> Int?)? = null,
        /** Receives each successively better plan while the body is already walking. */
        onImprovement: ((PathingManager.PublishedPath) -> Unit)? = null,
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
            maxYawDegreesPerFrame = turnSpeed,
            goalRadius = config.goalRadius,
            maxCorridorDeviation = config.maxCorridorDeviation,
            sprintModes = if (config.allowSprint) listOf(true, false) else listOf(false),
            corridorAdherentFirst = config.corridorAdherentFirst,
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

        // Per request, so two overlapping plans cannot hand each other their refinement.
        val refinement = AtomicReference<(() -> Unit)?>(null)

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
            val neuralPolicy = if (config.neuralDiscovery) {
                NeuralPolicyHolder.policy(config.neuralModelPath)
                    ?: return@supplyAsync PathPlanResult.NoRoute(
                        NeuralPolicyHolder.lastFailure() ?: "neural policy unavailable",
                    )
            } else {
                null
            }

            // A value-steered search only goes where the field has labels, so the field
            // has to be wider than the one corridor `computeShortestPath` settles for.
            // Bounded: it is paid once per plan, before any rollout.
            //
            // Refinement is value-steered too, whichever engine produced the first tape.
            // Without this it ran against a field holding only the corridor D* settled
            // for, so every anchor that stepped off that line was refused as unmapped and
            // the improvement pass silently found nothing at all -- which is why walking
            // never got better under the default config, not because there was nothing to
            // find.
            if (config.valueFieldSearch || onImprovement != null) {
                planner.expandField(
                    extraTicks = FIELD_EXPANSION_TICKS,
                    timeBudget = FIELD_EXPANSION_BUDGET,
                    maxExpansions = FIELD_EXPANSION_NODES,
                )
            }

            // A horizon walk publishes as it goes and only completes when it arrives, so
            // it owns the rest of this task rather than returning a plan to be executed.
            if (config.recedingHorizon && onSafePrefix != null && cursorFrame != null) {
                val route = planner.routePlan(snapshot.capturedWorldTime)
                    ?: return@supplyAsync PathPlanResult.NoRoute("no coarse route to the goal")
                return@supplyAsync walkHorizon(
                    route, planner, initial, profile, snapshot, seedConfig, cursorFrame,
                    publish = { path, running -> if (running) onImprovement?.invoke(path) else onSafePrefix(path) },
                    started = started,
                    lookahead = config.horizonRunwayFrames,
                    commitFrames = config.horizonCommitFrames,
                )
            }

            val outcome = searchWithRerouting(planner, snapshot.capturedWorldTime) { route ->
                if (neuralPolicy != null) {
                    // The policy proposes; the simulator in this rollout still certifies
                    // every frame, so the published tape is exactly as safe as the search's.
                    NeuralTrajectoryDiscovery.search(
                        route, initial, profile, snapshot, neuralPolicy,
                        maxFrames = config.maxFrames,
                        goalRadius = config.goalRadius,
                    )
                } else if (config.valueFieldSearch) {
                    ValueFieldAnchorSearch.search(
                        route, planner.valueField(), initial, profile, snapshot, seedConfig,
                        onSafePrefix = onSafePrefix?.let { publish ->
                            { success ->
                                publish(
                                    publishedPath(
                                        success, route, profile, planIds.incrementAndGet(),
                                        System.currentTimeMillis() - started, reroutes = 0,
                                        partial = true, field = planner.valueField(),
                                    )
                                )
                            }
                        },
                    )
                } else if (config.anchorSearch) {
                    MotionAnchorSearch.search(route, initial, profile, snapshot, seedConfig)
                } else {
                    WalkingSeedSearch.searchContinuously(route, initial, profile, snapshot, seedConfig)
                }
            } ?: return@supplyAsync PathPlanResult.NoRoute("no coarse route to the goal")

            (outcome.result as? WalkingSeedSearchResult.NoSafeStop)?.let { refusal ->
                dumpDirectory?.let { directory ->
                    runCatching {
                        PlanDump.write(
                            directory, snapshot, start, goal, initial, profile,
                            moveOptions, seedConfig,
                            note = "refused after ${refusal.attempts.size} attempts, " +
                                "blocked at ${refusal.blockedProgress}",
                        )
                    }.onFailure { LOG.error("Could not write the failed plan dump", it) }
                }
            }

            // Refinement improves the tape the body is *walking*, so it can only start
            // once there is one. Running it here, before the result is returned, meant it
            // asked for the executor's frame before the plan it was refining had even been
            // published: `cursorFrame()` was null and every pass returned immediately.
            // Anytime mode hid this -- there a partial tape is already running by now --
            // which is why improvements only ever appeared with the value field on.
            (outcome.result as? WalkingSeedSearchResult.Success)?.let { first ->
                if (onImprovement != null && cursorFrame != null) {
                    refinement.set {
                        refineWhileWalking(
                            first, outcome.route, planner, profile, snapshot, seedConfig,
                            cursorFrame, onImprovement, started,
                        )
                    }
                }
            }

            when (val seed = outcome.result) {
                is WalkingSeedSearchResult.Success -> PathPlanResult.Planned(
                    publishedPath(
                        seed, outcome.route, profile, planIds.incrementAndGet(),
                        System.currentTimeMillis() - started, outcome.reroutes, partial = false,
                        field = planner.valueField(),
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
        }.also { planned ->
            // Handed to a second worker *after* the manager has been given the plan, so
            // the body is walking by the time the first pass looks for the cursor.
            planned.thenRunAsync({ refinement.get()?.invoke() }, REFINEMENT_EXECUTOR)
        }
    }

    /**
     * How hard the last journey's improvement loop worked, for the completion report.
     *
     * "No improvements" has several very different causes, and only the attempt count
     * separates "the loop never ran" from "it ran hundreds of times and the tape was
     * already the best it could find".
     */
    @Volatile
    var attempts: Int = 0
        private set

    @Volatile
    var improvements: Int = 0
        private set

    /** Why attempts did not land, for benches; keyed by the stage that refused them. */
    @Volatile
    var refusals: Map<String, Int> = emptyMap()
        private set

    private val refusalCounts = HashMap<String, Int>()

    private fun refuse(stage: String) {
        refusalCounts[stage] = (refusalCounts[stage] ?: 0) + 1
        refusals = HashMap(refusalCounts)
    }

    private val REFINEMENT_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lambda-pathing-refine").apply { isDaemon = true }
    }

    /** A completed search together with how many proven-all-entry reroutes it took. */
    internal data class FeedbackSearchOutcome(
        val route: CoarseRoutePlan,
        val result: WalkingSeedSearchResult,
        val reroutes: Int,
    )

    /**
     * Runs [search] on the current coarse route. A jump is retired only when the result
     * explicitly proves it impossible for the complete bounded entry family, never
     * merely because one exact entry state/controller search exhausted its budget.
     *
     * Retiring a genuinely impossible jump lets D* find a detour. Misclassifying an
     * entry-state failure here is worse than refusing: it deletes an ordinary gap from
     * every later route, which produced the misleading "infeasible coarse jump" live
     * diagnostics on traversable bedrock.
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
            PlanningDebugChannel.publishRoute(route)
            when (val result = search(route)) {
                is WalkingSeedSearchResult.NoSafeStop -> {
                    lastRefusal = FeedbackSearchOutcome(route, result, reroutes)
                    val dead = result.deadEdge
                    if (result.edgeFailureScope !=
                        WalkingSeedSearchResult.EdgeFailureScope.IMPOSSIBLE_FOR_ALL_ENTRIES ||
                        dead == null ||
                        dead.kind != CoarseMoveKind.JUMP_CANDIDATE ||
                        reroutes >= maxReroutes
                    ) {
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
    /**
     * Keeps improving the published trajectory while the body walks it.
     *
     * The rule that makes this sound is that only the tape *ahead* of the executor may
     * change: the frames already pressed are history, and a plan that would have driven
     * them differently cannot be spliced onto them. So each pass re-searches from the
     * exact simulated state at a frame safely ahead of the cursor and keeps everything
     * before it verbatim — which is also why the improvement is guaranteed to be
     * adoptable, rather than being rejected for diverging behind the cursor the way a
     * fresh whole-route search always would be.
     *
     * Passes widen as they go. The first plan is produced by a *diving* search, which is
     * fast but commits to its early choices; refinement can afford the expensive
     * exploring settings precisely because a safe tape already exists and the body is
     * already making progress on it.
     */
    internal fun refineWhileWalking(
        first: WalkingSeedSearchResult.Success,
        route: CoarseRoutePlan,
        planner: CoarsePlanner,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        seedConfig: WalkingSeedSearchConfig,
        cursorFrame: () -> Int?,
        onImprovement: (PathingManager.PublishedPath) -> Unit,
        started: Long,
    ) {
        var best = first
        // The first plan's field is a thin sleeve: FIELD_EXPANSION_TICKS of manoeuvring
        // room, on a budget sized so the body starts moving quickly. Refinement is not on
        // that clock, and a field that cannot price a stance refuses every search that
        // reaches it -- so the sleeve is widened as the walk goes on, which is the only
        // supported way to buy room to improve in. `valueField()` reads D*'s live labels,
        // so re-taking it after an expansion is what makes the new ground visible; the
        // field caches what it has already been asked, infinities included.
        // Frames is not the only thing that makes a tape good. An improvement that saves a
        // tick by scraping a wall is not an improvement -- it is the "it lands badly and
        // has to collide" complaint, arriving one frame sooner. Collisions may go down or
        // stay level, never up, whichever improver produced the tape.
        var bestCollisions = collisionFrames(first)
        // What the executor is actually running. The internal best may sit level with it
        // after a sideways move, and only a strictly shorter tape is worth a swap.
        var publishedFrames = first.tape.frameCount
        var horizon = FIELD_EXPANSION_TICKS
        var field = planner.valueField()
        var sinceWidened = 0
        attempts = 0
        improvements = 0
        refusalCounts.clear()
        refusals = emptyMap()

        // The plan has just been handed to the manager, which installs it on its next
        // client tick. Give that handshake a moment to happen; without the wait the first
        // pass reads a null cursor and abandons refinement for the whole journey.
        var waited = 0
        while (cursorFrame() == null && waited < INSTALL_WAIT_MILLIS) {
            Thread.sleep(INSTALL_POLL_MILLIS.toLong())
            waited += INSTALL_POLL_MILLIS
        }

        // Keep asking, for as long as the body is walking.
        //
        // The hard part is having something new to ask. Enumerating cut points is a finite
        // and *shrinking* supply -- the set only loses members as the cursor advances --
        // so a single sweep exhausts it and the rest of the walk is dead time. Measured on
        // the live corpus: 57 sweeps producing 8 searches, and a 652-frame bedrock route
        // that adopted one improvement in its first second and then coasted for thirty.
        //
        // So the question is *sampled* instead of enumerated: a random cut point, and a
        // randomly drawn search that is genuinely a different search rather than the same
        // one re-run. Greediness, how many survivors a dominance bucket keeps and how
        // hard a sibling is penalised all change which line the search finds, so drawing
        // them per attempt makes repeats informative instead of wasted. That supply never
        // runs out, which is the property the enumeration never had.
        val random = java.util.Random(first.tape.frameCount.toLong() * 31 + started)
        val deadline = System.nanoTime() +
            (first.tape.frameCount + REFINEMENT_DEADLINE_MARGIN_FRAMES) * NANOS_PER_TICK
        while (System.nanoTime() < deadline) {
            val executing = cursorFrame() ?: return

            // Nothing landing means either the tape is already good or the field is too
            // narrow to hold anything better. Widening is cheap here and distinguishes
            // the two by making the second case possible.
            if (sinceWidened >= WIDEN_AFTER_ATTEMPTS && horizon < MAX_REFINEMENT_HORIZON_TICKS) {
                horizon += REFINEMENT_HORIZON_STEP_TICKS
                planner.expandField(
                    extraTicks = horizon,
                    timeBudget = REFINEMENT_FIELD_BUDGET,
                    maxExpansions = REFINEMENT_FIELD_NODES,
                )
                field = planner.valueField()
                sinceWidened = 0
            }

            val candidates = spliceCandidates(best, executing + REFINEMENT_LEAD_FRAMES, field)
            if (candidates.isEmpty()) {
                // No cut point the field can price. Widening may create one; giving up
                // here is what left long walks improving two or three times and then
                // coasting.
                if (horizon >= MAX_REFINEMENT_HORIZON_TICKS) return
                sinceWidened = WIDEN_AFTER_ATTEMPTS
                continue
            }
            // Aim where the time is actually being lost. Sampling cut points uniformly
            // spends most of the budget re-asking about stretches that were already fine;
            // the attribution says which stretch spent frames without buying progress, and
            // that is the one worth cutting in front of.
            val splice = weightedCut(candidates, segmentCosts(best, field), random)

            val tape = best.tape.asList()
            val entry = best.rollout.frames[splice - 1].state
            PlanningDebugChannel.publishCut(entry.position)
            attempts++
            sinceWidened++

            // Two ways to ask for something better, and they cost wildly different
            // amounts. Re-searching the suffix re-derives every frame from the cut to the
            // goal, so it must beat the whole remainder to be worth anything -- a hard bar
            // that a few hundred attempts clear a handful of times. Nudging the *decisions*
            // and re-running them costs one rollout, and only has to beat the tape by a
            // frame. Cheap questions are asked far more often; the expensive one is kept
            // because it is the only one that can find a genuinely different line.
            val nudging = random.nextInt(100) < nudgeSharePercent(best.tape.frameCount)
            val edited = if (nudging) {
                perturbedTail(best, splice, entry, route, field, profile, snapshot, seedConfig, random)
            } else {
                null
            }
            if (nudging && edited == null) refuse("nudge-rerun-failed")
            if (edited != null && collisionFrames(edited) > bestCollisions) refuse("nudge-collides")
            if (edited != null && collisionFrames(edited) <= bestCollisions) {
                best = edited
                bestCollisions = collisionFrames(edited)
                if (edited.tape.frameCount >= publishedFrames) refuse("nudge-level")
                if (edited.tape.frameCount <= publishedFrames - MIN_REFINEMENT_GAIN) {
                    publishedFrames = edited.tape.frameCount
                    improvements++
                    sinceWidened = 0
                    onImprovement(
                        publishedPath(
                            edited, route, profile, planIds.incrementAndGet(),
                            System.currentTimeMillis() - started, reroutes = 0, partial = false,
                            field = field,
                        )
                    )
                }
                continue
            }
            if (nudging) continue

            val suffixRoute = route.suffix(routeIndexFor(route, entry))
            val refined = ValueFieldAnchorSearch.search(
                suffixRoute, field, entry, profile, snapshot, seedConfig,
                sampledConfig(random),
            ) as? WalkingSeedSearchResult.Success ?: run { refuse("search-failed"); continue }

            if (splice + refined.tape.frameCount > best.tape.frameCount - MIN_REFINEMENT_GAIN) {
                refuse("search-no-gain")
                continue
            }

            val combined = tape.take(splice) + refined.tape.asList()
            val certified = certifyCombined(
                combined, first, profile, snapshot, refined,
                spliceDecisions(
                    best, splice, refined.planDecisions,
                    refined.planDecisions?.boundaries.orEmpty(),
                ),
            ) ?: run { refuse("search-join-failed"); continue }
            if (collisionFrames(certified) > bestCollisions) {
                refuse("search-collides")
                continue
            }
            best = certified
            bestCollisions = collisionFrames(certified)
            publishedFrames = certified.tape.frameCount
            improvements++
            sinceWidened = 0
            onImprovement(
                publishedPath(
                    certified, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, reroutes = 0, partial = false,
                    field = field,
                )
            )
        }
    }

    /**
     * One draw from the family of searches worth trying on a suffix.
     *
     * Every knob here changes *which* line the search prefers, not whether the result is
     * safe: the tape is certified end to end either way. Sampling them is what makes a
     * second look at the same cut point a new question rather than the same one.
     *
     * The budget is deliberately large. Refinement runs while the body is already walking
     * a certified tape, so its cost is not paid in latency the way the first plan's is.
     */
    private fun sampledConfig(random: java.util.Random) = ValueFieldSearchConfig(
        // From diving, which commits early and is fast, out to full breadth.
        siblingPenaltyTicks = random.nextDouble() * MAX_SAMPLED_SIBLING_PENALTY,
        // How greedily the frontier chases the coarse estimate.
        tailWeight = 1.0 + random.nextDouble() * MAX_SAMPLED_TAIL_WEIGHT_BONUS,
        // How many near-duplicate states a dominance bucket keeps alive.
        frontierPerKey = 1 + random.nextInt(MAX_SAMPLED_FRONTIER_PER_KEY),
        // Affordable here and nowhere else; see the field's own note.
        offerBrakeTicks = true,
        maxExpansions = REFINEMENT_EXPANSIONS,
        stallExpansions = REFINEMENT_STALL,
    )

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
        seedConfig: WalkingSeedSearchConfig,
        cursorFrame: () -> Int?,
        publish: (PathingManager.PublishedPath, Boolean) -> Unit,
        started: Long,
        lookahead: Int = HORIZON_FRAMES,
        commitFrames: Int = HORIZON_CHUNK_FRAMES,
    ): PathPlanResult = walkHorizon(
        route, planner, initial, profile, snapshot, seedConfig, cursorFrame, publish, started,
        lookahead, commitFrames,
    )

    private fun walkHorizon(
        route: CoarseRoutePlan,
        planner: CoarsePlanner,
        initial: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        seedConfig: WalkingSeedSearchConfig,
        cursorFrame: () -> Int?,
        publish: (PathingManager.PublishedPath, Boolean) -> Unit,
        started: Long,
        lookahead: Int = HORIZON_FRAMES,
        commitFrames: Int = HORIZON_CHUNK_FRAMES,
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
                safePrefixDelayMillis = 0,
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
            ),
            onSafePrefix = { step ->
                val path = publishedPath(
                    step, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, reroutes = 0, partial = true, field = field,
                )
                publish(path, published > 0)
                last = path
                published++
                improvements++
            },
            cursorFrame = cursorFrame,
        )
        attempts = published

        return when (result) {
            is WalkingSeedSearchResult.Success -> PathPlanResult.Planned(
                publishedPath(
                    result, route, profile, planIds.incrementAndGet(),
                    System.currentTimeMillis() - started, reroutes = 0, partial = false, field = field,
                )
            )

            // Nothing more could be committed. The body is already holding a certified
            // brake, so it stops there and the manager plans the next leg from rest --
            // the behaviour that existed before horizons, kept as the failure mode.
            else -> last?.let { PathPlanResult.Planned(it) }
                ?: PathPlanResult.NoRoute("no certified motion from the start state")
        }
    }

    /**
     * Picks a cut, favouring the ones standing in front of an expensive stretch.
     *
     * Weighted rather than greedy: the worst stretch is the best guess, not a certainty,
     * and always attacking it would re-ask the same question forever once it turns out to
     * be irreducible. Every candidate keeps a floor weight so nothing is unreachable.
     */
    private fun weightedCut(
        candidates: List<Int>,
        costs: List<SegmentCost>,
        random: java.util.Random,
    ): Int {
        if (costs.isEmpty()) return candidates[random.nextInt(candidates.size)]
        val excessAt = HashMap<Int, Double>()
        costs.forEach { cost -> if (!cost.terminal) excessAt[cost.fromFrame] = cost.excessTicks }
        val weights = candidates.map { CUT_WEIGHT_FLOOR + (excessAt[it] ?: 0.0) }
        var draw = random.nextDouble() * weights.sum()
        weights.forEachIndexed { index, weight ->
            draw -= weight
            if (draw <= 0.0) return candidates[index]
        }
        return candidates.last()
    }

    /** Ticks the body spends in contact with something it is trying to move through. */
    private fun collisionFrames(seed: WalkingSeedSearchResult.Success): Int =
        seed.rollout.frames.count { it.state.horizontalCollision }

    /** Attribution for a tape, recomputed only when the tape or the field changes. */
    private fun segmentCosts(
        seed: WalkingSeedSearchResult.Success,
        field: CoarseValueField,
    ): List<SegmentCost> {
        val cached = costCache
        if (cached != null && cached.first === seed && cached.second === field) return cached.third
        val boundaries = seed.planDecisions?.boundaries ?: seed.spliceFrames
        val costs = SegmentCosts.attribute(
            seed.rollout.initialState, seed.rollout.frames, boundaries, field,
        )
        costCache = Triple(seed, field, costs)
        return costs
    }

    private var costCache: Triple<WalkingSeedSearchResult.Success, CoarseValueField, List<SegmentCost>>? = null

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

    private const val HORIZON_POLL_MILLIS = 20L

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

    /** Exploration a commitment must be backed by, so its quality does not vary with load. */
    private const val HORIZON_MIN_COMMIT_EXPANSIONS = 400

    /** Expansions one horizon search may spend across an entire walk. */
    private const val HORIZON_EXPANSIONS = 2_000_000

    /** How far past the wanted commit length a step will look for somewhere to halt. */
    private const val HORIZON_GROWTH_REACH = 60
    private const val HORIZON_GROWTH_STEP = 6

    /** Frames of slack over a step's own cost, so the next one is never a photo finish. */
    private const val HORIZON_KEEP_UP_MARGIN = 10

    /** Keeps every cut reachable, however good the stretch in front of it looks. */
    private const val CUT_WEIGHT_FLOOR = 0.5

    /**
     * Every frame ahead of the cursor a refinement could cut at, cheapest first.
     *
     * Cheapest is *latest*: a cut near the end leaves a short suffix to re-search, so many
     * more questions get asked per second than if the sweep always started at the body.
     * Cuts are spaced out because two a few frames apart pose almost the same problem and
     * would answer it twice.
     */
    private fun spliceCandidates(
        seed: WalkingSeedSearchResult.Success,
        earliest: Int,
        field: CoarseValueField,
    ): List<Int> {
        val frames = seed.rollout.frames
        val boundaries = seed.planDecisions?.boundaries?.toHashSet().orEmpty()
        val latest = frames.size - MIN_REFINABLE_SUFFIX
        val grounded = ArrayList<Int>()
        for (frame in maxOf(earliest, 2)..latest) {
            if (!frames[frame - 1].state.onGround || !frames[frame - 2].state.onGround) continue
            // A value-steered search only expands where the field has a cost-to-go, and
            // refuses every successor that lands off it. Cutting somewhere unlabelled
            // therefore produces a search that admits its root and dies -- cheap enough
            // that sampling ran twelve thousand of them on one walk and landed nothing.
            if (!field.guide(ValueFieldAnchorSearch.stanceOf(frames[frame - 1].state)).isFinite()) continue
            grounded += frame
        }
        // Cutting anywhere else loses the decision list: the head could no longer be
        // described as whole decisions, and an editable plan is what every cheaper
        // improver is built on. Grounded-but-unboundaried frames are only used when the
        // tape records no decisions at all.
        val onBoundary = grounded.filter { it in boundaries }
        val preferred = if (boundaries.isEmpty()) grounded else onBoundary
        val spaced = ArrayList<Int>()
        preferred.forEach { frame ->
            if (spaced.isEmpty() || frame - spaced.last() >= SPLICE_SPACING_FRAMES) spaced += frame
        }
        return spaced.asReversed()
    }

    /** Nearest route node to a mid-tape state; the suffix search starts from there. */
    private fun routeIndexFor(route: CoarseRoutePlan, state: MovementSimulationState): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        route.nodes.forEachIndexed { index, node ->
            val dx = node.x + 0.5 - state.position.x
            val dz = node.z + 0.5 - state.position.z
            val dy = node.y - state.position.y
            val distance = dx * dx + dy * dy + dz * dz
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /**
     * Replays a spliced tape from the original frame zero.
     *
     * The prefix was certified in an earlier pass and the suffix in this one, but they
     * have never been simulated as one thing, and only a single replay of the whole tape
     * proves the join. Nothing is published that has not been through this.
     */
    private fun certifyCombined(
        inputs: List<MovementSimulationInput>,
        original: WalkingSeedSearchResult.Success,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        refined: WalkingSeedSearchResult.Success,
        decisions: TrajectoryPlanDecisions?,
    ): WalkingSeedSearchResult.Success? {
        val tape = InputTape(inputs)
        val tracked = snapshot.trackingView()
        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = original.rollout.initialState,
            profile = profile,
            environment = tracked,
            program = tape,
            frameCount = tape.frameCount,
        )
        if (!rollout.completed || rollout.frames.size != tape.frameCount) return null
        return original.copy(
            tape = tape,
            rollout = rollout,
            parameters = refined.parameters.copy(
                gapLaunchFrames = rollout.frames.filter { it.input.jump }.map { it.index },
            ),
            dependencies = original.dependencies + refined.dependencies + tracked.dependencies(),
            controlSegments = original.controlSegments + refined.controlSegments,
            // Without this the improved tape still carries the decisions of the tape it
            // replaced, and every later edit is described against a plan that no longer
            // exists. Null when the join cannot be expressed as whole decisions -- honest
            // is better than stale.
            planDecisions = decisions,
        )
    }

    /**
     * One nudged copy of the plan's tail, certified end to end, or null if it is no good.
     *
     * The tail is stored as the decisions that produced it, so it can be *edited* rather
     * than only replayed: change a launch by a tick, let a corner be taken tighter, drop a
     * sprint, and re-run the rest from the body's real state. The controllers absorb the
     * difference, because they steer at world targets and fire on grounded ticks rather
     * than fixed frames, and the whole tape is then replayed once to prove the join.
     *
     * Nothing here is trusted: a nudge that breaks the tail simply fails to certify and is
     * thrown away for the price of a single rollout.
     */
    private fun perturbedTail(
        best: WalkingSeedSearchResult.Success,
        splice: Int,
        entry: MovementSimulationState,
        route: CoarseRoutePlan,
        field: CoarseValueField,
        profile: PlayerPhysicsProfile,
        snapshot: SnapshotSimulationEnvironment,
        seedConfig: WalkingSeedSearchConfig,
        random: java.util.Random,
    ): WalkingSeedSearchResult.Success? {
        val tail = best.planDecisions?.suffixFrom(splice) ?: run { refuse("no-decisions"); return null }
        if (tail.decisions.isEmpty()) { refuse("empty-tail"); return null }

        val decisions = ArrayList(tail.decisions)
        val intended = ArrayList(tail.intended)

        // Nudging a decision changes how it is executed; it cannot change what the plan
        // *is*, and a plan of the same shape takes the same number of ticks. Measured on a
        // 433-frame walk: 322 of 426 attempts re-ran to exactly the same length. Getting
        // shorter needs a structural edit -- one fewer decision, or one aimed further
        // ahead -- which is also what "it obviously could have cut that corner" means.
        if (decisions.size > MIN_TAIL_DECISIONS && random.nextInt(100) < STRUCTURAL_EDIT_PERCENT) {
            // Later edits have fewer decisions depending on them, so they survive the
            // re-run more often; the square biases toward the tail end without ever
            // excluding the early ones, where the biggest detours usually are.
            val spread = random.nextDouble() * random.nextDouble()
            val index = ((1.0 - spread) * (decisions.size - 2)).toInt().coerceIn(0, decisions.size - 2)

            // Merge a decision into its successor: aim it where the successor was aimed
            // and drop the successor. One run straight at the later target instead of two
            // through the bend -- the corner-cutting move, and the reason a path can look
            // obviously improvable while every parameter of it is already fine.
            //
            // Merging rather than simply deleting matters. Deleting skips a whole stance
            // transition, which leaves the body a block or more from where the rest of the
            // plan expects it -- far outside the quarter-block a tail was measured to
            // absorb, and it took the re-run failures from a third of attempts to two
            // thirds. A merge lands near where the successor would have, so the decisions
            // after it face a perturbation they can actually steer out of.
            // Only merges the coarse layer already believes in. Most straight lines
            // between two targets run through terrain, and the body finds out by failing a
            // safety gate mid-rollout -- measured at 2,424 gate failures across 544
            // attempts, which is the whole budget spent proving corners cannot be cut.
            // The coarse layer answers the same question for free, before any simulation.
            val from = if (index == 0) entry else intended.getOrNull(index - 1) ?: return null
            val target = decisions[index + 1].step ?: return null
            if (!mergeable(field, ValueFieldAnchorSearch.stanceOf(from), target)) {
                refuse("merge-implausible")
                return null
            }
            decisions[index] = retarget(decisions[index], decisions[index + 1]) ?: return null
            decisions.removeAt(index + 1)
            if (index < intended.size) intended.removeAt(index)
        } else {
            repeat(1 + random.nextInt(MAX_DECISION_EDITS)) {
                val index = random.nextInt(decisions.size)
                decisions[index] = nudge(decisions[index], random)
            }
        }

        val nudged = TrajectoryPlanDecisions(
            decisions = decisions,
            intended = intended,
            boundaries = tail.boundaries,
            terminal = tail.terminal,
        )

        val segments = ValueFieldAnchorSearch.rerunSegments(
            nudged, entry, route, field, profile, snapshot, seedConfig, skipFailures = true,
        ) ?: run { refuse("rerun-null"); return null }
        // The terminal approach contributes a segment but is not a decision.
        val decisionSegments = segments.take(decisions.size)
        if (decisionSegments.size != decisions.size) return null
        var running = 0
        val boundaries = decisionSegments.map { segment -> running += segment.size; running }

        val combined = best.tape.asList().take(splice) + segments.flatten()
        // Equal length counts. Frames are integers, so most nudges land exactly level, and
        // rejecting those leaves the improver re-rolling the same plan forever. A tape of
        // the same length built from different decisions is a different place to nudge
        // *from*, which is how a plateau gets crossed to the next step down. It is only
        // ever adopted internally; publishing still demands a strictly shorter tape.
        if (combined.size > best.tape.frameCount) { refuse("nudge-longer"); return null }
        return certifyCombined(
            combined, best, profile, snapshot, best,
            spliceDecisions(
                best, splice,
                TrajectoryPlanDecisions(decisions, tail.intended, boundaries, tail.terminal),
                boundaries,
            ),
        )
    }

    /**
     * Whether the coarse layer thinks [target] can be reached from [from] in one move.
     *
     * Cheap and conservative: a direct edge means a merge is at least geometrically
     * possible, and its absence means the two decisions exist separately for a reason.
     * A false negative only costs a shortcut nobody tried; a false positive costs a whole
     * simulated rollout, so the test leans this way deliberately.
     */
    private fun mergeable(field: CoarseValueField, from: Stance, target: Stance): Boolean =
        from != target && field.edgesFrom(from).any { it.to == target }

    /** [decision], aimed at whatever [successor] was aiming at. */
    private fun retarget(
        decision: TrajectoryDecision,
        successor: TrajectoryDecision,
    ): TrajectoryDecision? {
        val step = successor.step ?: return null
        return when (decision) {
            is TrajectoryDecision.Walk -> decision.copy(step = step)
            is TrajectoryDecision.Launch -> decision.copy(step = step)
            is TrajectoryDecision.Heading -> decision.copy(step = step)
        }
    }

    /**
     * One decision, moved slightly. Never into something unsafe -- the replay gate decides
     * that -- only into something *different* worth simulating once.
     */
    private fun nudge(decision: TrajectoryDecision, random: java.util.Random): TrajectoryDecision =
        when (decision) {
            is TrajectoryDecision.Walk -> when (random.nextInt(3)) {
                0 -> decision.copy(sprint = !decision.sprint)
                1 -> decision.copy(easeTurns = !decision.easeTurns)
                else -> decision.copy(
                    lookAheadNodes = (decision.lookAheadNodes + random.nextInt(3) - 1).coerceIn(1, 8),
                )
            }

            is TrajectoryDecision.Launch -> when (random.nextInt(2)) {
                0 -> decision.copy(
                    delayFrames = (decision.delayFrames + random.nextInt(3) - 1).coerceAtLeast(0),
                )
                // Where it takes off, and how fast: the two things that decide where an
                // arc lands, so both are worth nudging.
                else -> decision.copy(
                    brakeTicks = (decision.brakeTicks + random.nextInt(3) - 1).coerceIn(0, 2),
                )
            }

            is TrajectoryDecision.Heading -> when (random.nextInt(3)) {
                0 -> decision.copy(offsetDegrees = decision.offsetDegrees + (random.nextDouble() - 0.5) * 12.0)
                1 -> decision.copy(
                    delayFrames = decision.delayFrames?.let { (it + random.nextInt(3) - 1).coerceAtLeast(0) },
                )
                // How long the bearing is held. A committed run that outlives its
                // usefulness is the single most repeated waste the attribution finds.
                else -> decision.copy(
                    commitFrames = ((decision.commitFrames ?: DEFAULT_COMMIT_FRAMES) +
                        random.nextInt(9) - 4).coerceIn(MIN_COMMIT_FRAMES, MAX_COMMIT_FRAMES),
                )
            }
        }

    /** The decisions of [best] up to [splice], with a re-derived tail spliced on. */
    private fun spliceDecisions(
        best: WalkingSeedSearchResult.Success,
        splice: Int,
        tail: TrajectoryPlanDecisions?,
        tailBoundaries: List<Int>,
    ): TrajectoryPlanDecisions? {
        val head = best.planDecisions ?: return null
        if (tail == null || head.boundaries.size != head.decisions.size) return null
        val kept = head.boundaries.count { it <= splice }
        if (head.boundaries.getOrNull(kept - 1) != splice) return null
        if (tail.decisions.size != tailBoundaries.size) return null
        return TrajectoryPlanDecisions(
            decisions = head.decisions.take(kept) + tail.decisions,
            intended = head.intended.take(kept) + tail.intended,
            boundaries = head.boundaries.take(kept) + tailBoundaries.map { it + splice },
            terminal = tail.terminal,
        )
    }

    /**
     * Sibling penalties for successive refinement passes: from the diving value that
     * found the first tape, down toward full expansion, which measured the best quality
     * and was previously unaffordable because it delayed the first step.
     */
    private const val NANOS_PER_TICK = 50_000_000L

    /**
     * Slack over the walk's own length before refinement gives up on the cursor.
     *
     * The cursor going null when the walk ends is the normal exit; this is the backstop,
     * because a worker spinning forever is worse than a walk that stops improving.
     */
    private const val REFINEMENT_DEADLINE_MARGIN_FRAMES = 200

    /**
     * How much of the budget goes to nudging decisions rather than re-searching a suffix,
     * as a function of how long the tape is.
     *
     * The two improvers have opposite economics. Re-searching a suffix is thorough but
     * costs the whole remainder, so on a short tape it is cheap enough to run hundreds of
     * times and it wins outright -- measured, giving it the whole budget on the live
     * corpus beat an even split by four or five frames, well outside the run-to-run spread
     * of one or two. On a long tape the same search re-derives hundreds of frames per
     * attempt and almost never beats a tape that has already been improved, while a nudge
     * still costs one rollout. So the split follows the length rather than being guessed
     * once.
     */
    private fun nudgeSharePercent(frames: Int): Int = when {
        frames <= NUDGE_MIN_FRAMES -> 0
        frames >= NUDGE_FULL_FRAMES -> MAX_NUDGE_SHARE_PERCENT
        else -> MAX_NUDGE_SHARE_PERCENT * (frames - NUDGE_MIN_FRAMES) /
            (NUDGE_FULL_FRAMES - NUDGE_MIN_FRAMES)
    }

    private const val NUDGE_MIN_FRAMES = 150
    private const val NUDGE_FULL_FRAMES = 400
    private const val MAX_NUDGE_SHARE_PERCENT = 80

    private const val MAX_DECISION_EDITS = 3

    /** Share of nudges that change the plan's shape rather than a decision's parameters. */
    private const val STRUCTURAL_EDIT_PERCENT = 60

    private const val DEFAULT_COMMIT_FRAMES = 12
    private const val MIN_COMMIT_FRAMES = 4
    private const val MAX_COMMIT_FRAMES = 24

    /** Below this a tail is too short for dropping a decision to mean anything. */
    private const val MIN_TAIL_DECISIONS = 3

    /** Attempts without a win before the field is widened to make room for one. */
    private const val WIDEN_AFTER_ATTEMPTS = 40
    private const val REFINEMENT_HORIZON_STEP_TICKS = 48.0
    private const val MAX_REFINEMENT_HORIZON_TICKS = 600.0
    private val REFINEMENT_FIELD_BUDGET = 250.milliseconds
    private const val REFINEMENT_FIELD_NODES = 120_000

    private const val MAX_SAMPLED_SIBLING_PENALTY = 2.0
    private const val MAX_SAMPLED_TAIL_WEIGHT_BONUS = 0.6
    private const val MAX_SAMPLED_FRONTIER_PER_KEY = 5

    private const val REFINEMENT_EXPANSIONS = 24_000
    private const val REFINEMENT_STALL = 8_000

    /** Minimum gap between two cut points; closer than this they pose the same question. */
    private const val SPLICE_SPACING_FRAMES = 8


    /** How long refinement waits for the manager to install the plan it is refining. */
    private const val INSTALL_WAIT_MILLIS = 1_000
    private const val INSTALL_POLL_MILLIS = 10

    /** Frames ahead of the executor a refinement may start; below this it cannot be adopted. */
    private const val REFINEMENT_LEAD_FRAMES = 12

    /** A suffix shorter than this is the braking tail; there is nothing to gain there. */
    private const val MIN_REFINABLE_SUFFIX = 16

    /** Frames an improvement must save to be worth a splice. */
    private const val MIN_REFINEMENT_GAIN = 1

    /** One place that turns a certified search result into something publishable. */
    private fun publishedPath(
        seed: WalkingSeedSearchResult.Success,
        route: CoarseRoutePlan,
        profile: PlayerPhysicsProfile,
        id: Long,
        planMillis: Long,
        reroutes: Int,
        partial: Boolean,
        field: CoarseValueField? = null,
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
        reroutes = reroutes,
        launchMarginFrames = seed.launchMarginFrames,
        partial = partial,
        segmentCosts = field?.let { segmentCosts(seed, it) }.orEmpty(),
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
     * How many infeasible coarse jumps one plan may reroute around before giving up.
     * Each reroute is a cheap incremental D* repair; the bound keeps a pathological graph
     * (every jump admitted, none feasible) from turning one plan into an unbounded search.
     */
    private const val MAX_REROUTES = 8

    /**
     * How far past the optimal cost the value field is labelled for a value-steered
     * search. Roughly ten blocks of detour room — enough to cut a corner or take a
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
