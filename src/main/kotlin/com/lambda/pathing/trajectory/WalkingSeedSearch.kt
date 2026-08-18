/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.hypot

data class WalkingSeedSearchConfig(
    val maxFrames: Int = 160,
    /**
     * Largest per-frame yaw step any simulated controller may plan. Production fills
     * this from the requester's rotation-config turn speed (sampled once at capture),
     * so a tape never asks for a turn the rotation manager was not set up to deliver.
     */
    val maxYawDegreesPerFrame: Double = 30.0,
    val goalRadius: Double = 0.20,
    val stoppedSpeed: Double = 0.012,
    val stableStopFrames: Int = 3,
    val maxCorridorDeviation: Double = 1.5,
    /** Vanilla takes fall damage above three blocks. Safety is a hard gate. */
    val maxSafeFallDistance: Double = 3.0,
    val lookAheadNodes: List<Int> = listOf(1, 2, 3),
    val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
    val stepUpJumpLeadDistances: List<Double> = listOf(0.30, 0.55, 0.80, 1.05),
    val sprintModes: List<Boolean> = listOf(true, false),
    /**
     * How far back from a fall the grounded launch lattice is searched. A launch can
     * only help if it happens shortly before the body leaves the ground.
     */
    val gapLaunchWindowFrames: Int = 14,
    /** How many failed walks are backtracked into launch searches. Bounded work. */
    val maxGapSeeds: Int = 12,
    /** Maximum number of failure-discovered gap launches in one continuous tape. */
    val maxGapJumps: Int = 8,
    /** Maximum piecewise controllers expanded into one continuously replayed tape. */
    val maxContinuousSegments: Int = 32,
    /**
     * Pessimistic-first discovery: certify the corridor-adherent gait family (first
     * look-ahead, first brake) and its launch beam before paying for the full gait
     * grid. One certified stop -- or a safe moving extension for the continuous
     * expansion to build on -- ends the segment search; the full sweep is the
     * escalation for segments the adherent family genuinely cannot solve. Widening
     * beyond the corridor to find *shorter* lines is the anytime refiner's job
     * (final plan §2.4), never batch discovery's.
     */
    val corridorAdherentFirst: Boolean = true,
) {
    init {
        require(maxFrames > 0)
        require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
        require(goalRadius > 0.0 && goalRadius.isFinite())
        require(stoppedSpeed >= 0.0 && stoppedSpeed.isFinite())
        require(stableStopFrames > 0)
        require(maxCorridorDeviation > 0.0 && maxCorridorDeviation.isFinite())
        require(maxSafeFallDistance >= 0.0 && maxSafeFallDistance.isFinite())
        require(lookAheadNodes.isNotEmpty() && lookAheadNodes.all { it > 0 })
        require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
        require(stepUpJumpLeadDistances.isNotEmpty() && stepUpJumpLeadDistances.all { it > 0.0 && it.isFinite() })
        require(sprintModes.isNotEmpty())
        require(gapLaunchWindowFrames > 0)
        require(maxGapSeeds >= 0)
        require(maxGapJumps >= 0)
        require(maxContinuousSegments > 0)
    }
}

data class WalkingSeedParameters(
    val sprint: Boolean,
    val lookAheadNodes: Int,
    val brakeDistance: Double,
    val stepUpJumpLeadDistance: Double?,
    /**
     * Grounded frames on which to press jump for gaps, discovered recursively by
     * backtracking over failed traces. Empty means the nominal walk pressed none.
     *
     * These are absolute tape frames. After one launch changes the rollout, only
     * that new rollout may discover the next launch; copying timings from the
     * nominal walk would not be simulation-certified.
     */
    val gapLaunchFrames: List<Int> = emptyList(),
)

data class WalkingSeedAttempt(
    val parameters: WalkingSeedParameters,
    val simulatedFrames: Int,
    val finalGoalError: Double,
    val finalHorizontalSpeed: Double,
    /** Null when this attempt reached a stable grounded stop at the goal. */
    val diagnostic: TrajectoryDiagnostic?,
    /** Route node reached when [diagnostic] occurred; used for exact edge attribution. */
    val blockedProgress: Int = 0,
)

sealed interface WalkingSeedSearchResult {
    /**
     * What a trajectory refusal proves about [NoSafeStop.deadEdge].
     *
     * A rollout search starts from one exact body state. Exhausting its launch lattice
     * proves only that this entry state/controller family failed; it does not prove the
     * geometric coarse edge impossible for every attainable speed and alignment.
     */
    enum class EdgeFailureScope {
        CURRENT_ENTRY,
        IMPOSSIBLE_FOR_ALL_ENTRIES,
    }

    data class Extension(
        val tape: InputTape,
        val rollout: TrajectoryRollout,
        val parameters: WalkingSeedParameters,
        /** Node which becomes node zero of the next continuously simulated suffix. */
        val spliceNodeIndex: Int,
        /** Failure this worker-only prefix deliberately leaves runway before. */
        val sourceDiagnostic: TrajectoryDiagnostic,
        /** Accumulated launch runway in frames, used as a ranking/metrics margin. */
        val launchMarginFrames: Int = 0,
    )

    data class Success(
        val sourceRoute: CoarseRoutePlan,
        val tape: InputTape,
        val rollout: TrajectoryRollout,
        val parameters: WalkingSeedParameters,
        /** Union of coarse template reads and every snapshot voxel read by simulation. */
        val dependencies: Set<VoxelPos>,
        val attempts: List<WalkingSeedAttempt>,
        /** Piecewise control programs concatenated into this one replayable tape. */
        val controlSegments: Int = 1,
        /** Frame boundaries between worker-local controllers; execution ignores them. */
        val spliceFrames: List<Int> = emptyList(),
        /** Accumulated launch runway in frames across the certified tape. */
        val launchMarginFrames: Int = 0,
    ) : WalkingSeedSearchResult

    data class UnsupportedRoute(val edgeKinds: Set<CoarseMoveKind>) : WalkingSeedSearchResult

    data class NoSafeStop(
        val attempts: List<WalkingSeedAttempt>,
        /** Farthest safe moving prefix available for continuous expansion. */
        val extension: Extension? = null,
        /** Moving controller prefixes already flattened before this refusal. */
        val completedSegments: Int = 0,
        val spliceFrames: List<Int> = emptyList(),
        /** Coarse suffix on which continuous expansion finally ran out of options. */
        val remainingStart: Stance? = null,
        val remainingGoal: Stance? = null,
        val remainingMoveSummary: String = "",
        /** Route-node index of the diagnostic in the local suffix. */
        val blockedProgress: Int? = null,
        /**
         * The first coarse edge of the suffix this exact entry state could not get past.
         * Null when no edge remained to blame.
         */
        val deadEdge: CoarseEdge? = null,
        /**
         * Whether the failure is local to the simulated entry or proves the edge
         * impossible for the complete bounded entry family. The rollout search never
         * upgrades this itself; doing so would turn a controller/search-budget failure
         * into false negative feedback for D*.
         */
        val edgeFailureScope: EdgeFailureScope = EdgeFailureScope.CURRENT_ENTRY,
    ) : WalkingSeedSearchResult {
        /** The failure the search got closest to solving; the first thing M4 should attack. */
        val nearest: WalkingSeedAttempt? get() = attempts.minByOrNull { it.finalGoalError }
    }

    /**
     * The accepted tape did not reproduce on a fresh replay. This must be impossible
     * -- same snapshot, same profile, same inputs -- so it is a typed result rather
     * than a crash in a worker thread, per invariant 6.
     */
    data class UnstableReplay(val reason: String) : WalkingSeedSearchResult
}

/**
 * Seed smooth corridor followers, simulate each, and publish only a stopped,
 * collision-free input tape. Typed step-ups use geometric lead schedules; gap and
 * rising-jump launches are discovered by backtracking over failed simulated traces.
 * Multiple hazards are added one rollout at a time so every downstream launch frame
 * belongs to the state sequence produced by the launches before it.
 */
object WalkingSeedSearch {
    /**
     * Expands a trajectory through exact predicted splice states until the final
     * stable stop is certified, then replays the concatenated tape from frame zero.
     *
     * A failed local controller may contribute its safe moving prefix. The next
     * controller starts from that prefix's simulated final state and a coarse suffix;
     * execution never observes the boundary and never stops there. Publication only
     * happens after one fresh replay certifies the entire concatenated tape.
     */
    fun searchContinuously(
        route: CoarseRoutePlan,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
    ): WalkingSeedSearchResult {
        var suffix = route
        var spliceState = initialState
        val inputs = ArrayList<MovementSimulationInput>()
        val attempts = ArrayList<WalkingSeedAttempt>()
        val globalLaunches = ArrayList<Int>()
        val spliceFrames = ArrayList<Int>()
        var representativeParameters: WalkingSeedParameters? = null
        var segmentCount = 0
        var launchMarginFrames = 0
        var deadEdge: CoarseEdge? = null
        var deadProgress: Int? = null

        fun refusal() = WalkingSeedSearchResult.NoSafeStop(
            attempts = attempts.toList(),
            completedSegments = segmentCount,
            spliceFrames = spliceFrames.toList(),
            remainingStart = suffix.nodes.first(),
            remainingGoal = suffix.goal,
            remainingMoveSummary = suffix.edges.groupingBy { edge ->
                val span = maxOf(
                    kotlin.math.abs(edge.to.x - edge.from.x),
                    kotlin.math.abs(edge.to.z - edge.from.z),
                )
                "${edge.kind}(span=$span,dy=${edge.to.y - edge.from.y})"
            }.eachCount().entries.joinToString { (move, count) -> "$count $move" },
            blockedProgress = deadProgress,
            deadEdge = deadEdge,
        )

        repeat(config.maxContinuousSegments) {
            when (val result = search(suffix, spliceState, profile, environment, config)) {
                is WalkingSeedSearchResult.Success -> {
                    val frameOffset = inputs.size
                    inputs += result.tape.asList()
                    globalLaunches += result.parameters.gapLaunchFrames
                        .filter { it < result.tape.frameCount }
                        .map { frameOffset + it }
                    attempts += result.attempts
                    representativeParameters = representativeParameters ?: result.parameters
                    segmentCount++
                    launchMarginFrames += result.launchMarginFrames

                    val tape = InputTape(inputs)
                    val tracked = environment.trackingView()
                    val certified = TrajectoryRolloutEngine.rollout(
                        initialState = initialState,
                        profile = profile,
                        environment = tracked,
                        program = tape,
                        frameCount = tape.frameCount,
                    )
                    if (!certified.completed || certified.frames.size != tape.frameCount) {
                        return WalkingSeedSearchResult.UnstableReplay(
                            "expanded tape did not reproduce: ${certified.termination}"
                        )
                    }
                    return WalkingSeedSearchResult.Success(
                        sourceRoute = route,
                        tape = tape,
                        rollout = certified,
                        parameters = checkNotNull(representativeParameters).copy(
                            gapLaunchFrames = globalLaunches.toList(),
                        ),
                        dependencies = route.dependencies + tracked.dependencies(),
                        attempts = attempts.toList(),
                        controlSegments = segmentCount,
                        spliceFrames = spliceFrames.toList(),
                        launchMarginFrames = launchMarginFrames,
                    )
                }

                is WalkingSeedSearchResult.NoSafeStop -> {
                    attempts += result.attempts
                    deadEdge = result.deadEdge
                    deadProgress = result.blockedProgress
                    val extension = result.extension
                        ?: return refusal()
                    if (extension.spliceNodeIndex !in 1..suffix.nodes.lastIndex) {
                        return refusal()
                    }

                    val frameOffset = inputs.size
                    inputs += extension.tape.asList()
                    globalLaunches += extension.parameters.gapLaunchFrames
                        .filter { it < extension.tape.frameCount }
                        .map { frameOffset + it }
                    representativeParameters = representativeParameters ?: extension.parameters
                    launchMarginFrames += extension.launchMarginFrames
                    segmentCount++
                    spliceFrames += inputs.size
                    spliceState = extension.rollout.finalState
                    suffix = suffix.suffix(extension.spliceNodeIndex)
                }

                is WalkingSeedSearchResult.UnsupportedRoute -> return result
                is WalkingSeedSearchResult.UnstableReplay -> return result
            }
        }

        return refusal()
    }

    fun search(
        route: CoarseRoutePlan,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
    ): WalkingSeedSearchResult {
        val supportedKinds = setOf(
            CoarseMoveKind.WALK,
            CoarseMoveKind.STEP_UP,
            CoarseMoveKind.WALK_OFF,
            CoarseMoveKind.JUMP_CANDIDATE,
        )
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }.filterTo(HashSet()) { it !in supportedKinds }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        val nodes = route.nodes.map { it.center() }
        val goal = nodes.last()
        val attempts = ArrayList<WalkingSeedAttempt>()
        val jumpLeadDistances: List<Double?> = if (CoarseMoveKind.STEP_UP in route.edges.map { it.kind }) {
            config.stepUpJumpLeadDistances
        } else {
            listOf(null)
        }

        var best: Candidate? = null
        var bestExtension: ExtensionCandidate? = null
        val launchSeeds = ArrayList<LaunchSeed>()
        val triedParameters = HashSet<WalkingSeedParameters>()

        fun retainExtension(
            parameters: WalkingSeedParameters,
            rollout: TrajectoryRollout,
            evaluation: Evaluation,
            launchMargin: Int = 0,
        ) {
            val candidate = extensionCandidate(
                parameters, rollout, evaluation, route, nodes, config, launchMargin,
            ) ?: return
            val previous = bestExtension
            if (previous == null || candidate.betterThan(previous)) {
                bestExtension = candidate
            }
        }

        /** One full simulated rollout; returns the launch seed when an obstacle blocked it. */
        fun attempt(parameters: WalkingSeedParameters, launchMargin: Int): LaunchSeed? {
            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = initialState,
                profile = profile,
                environment = environment,
                program = CorridorFollowerProgram(route.nodes, parameters, config),
                frameCount = config.maxFrames,
            )
            val evaluation = evaluate(rollout, nodes, goal, config)
            val final = rollout.finalState
            val blockedProgress = diagnosticProgress(rollout, evaluation, nodes)
            retainExtension(parameters, rollout, evaluation, launchMargin)
            PlanningDebugChannel.publishAttempt(
                rollout, evaluation.stopFrame != null, evaluation.diagnostic,
            )
            val goalError = hypot(final.position.x - goal.x, final.position.z - goal.z)
            attempts += WalkingSeedAttempt(
                parameters = parameters,
                simulatedFrames = rollout.frames.size,
                finalGoalError = goalError,
                finalHorizontalSpeed = final.velocity.horizontalLength(),
                diagnostic = evaluation.diagnostic,
                blockedProgress = blockedProgress,
            )

            evaluation.stopFrame?.let { frame ->
                val candidate = candidate(parameters, rollout, frame, route, nodes, launchMargin)
                val incumbent = best
                if (incumbent == null || candidate.rank < incumbent.rank) {
                    best = candidate
                }
            }

            val blocked = launchSeedFrame(evaluation.diagnostic) ?: return null
            return LaunchSeed(
                parameters = parameters,
                rollout = rollout,
                blockedFrame = blocked,
                blockedProgress = blockedProgress,
                goalError = goalError,
                marginSoFar = launchMargin,
            )
        }

        fun sweep(
            gaits: List<Pair<Int, Double>>,
            sprintModes: List<Boolean> = config.sprintModes,
        ) {
            for (sprint in sprintModes) {
                for ((lookAhead, brakeDistance) in gaits) {
                    for (jumpLeadDistance in jumpLeadDistances) {
                        val parameters = WalkingSeedParameters(sprint, lookAhead, brakeDistance, jumpLeadDistance)
                        if (!triedParameters.add(parameters)) continue
                        attempt(parameters, launchMargin = 0)?.let { launchSeeds += it }
                    }
                }
            }
        }

        // Failure-directed branching (§7.3): each failed rollout identifies one
        // obstacle. Backtrack over its grounded trace, add one launch, then simulate
        // again. If that rollout reaches another obstacle it becomes the next
        // frontier. This is deliberately recursive: a nominal trace has no valid
        // timing for hazards beyond the first jump because the first arc changes all
        // later states. The bounded beam produces one continuously certified tape,
        // rather than forcing the manager to stop and replan after every gap.
        fun beam() {
            if (launchSeeds.isEmpty() || config.maxGapJumps == 0) return
            var frontier: List<LaunchSeed> = launchSeeds
            var jumpCount = 0
            while (best == null && frontier.isNotEmpty() && jumpCount < config.maxGapJumps) {
                val nextFrontier = ArrayList<LaunchSeed>()
                for (seed in selectGapSeeds(frontier, config.maxGapSeeds)) {
                    val previousLaunch = seed.parameters.gapLaunchFrames.lastOrNull() ?: -1
                    val lattice = launchLattice(seed.rollout, seed.blockedFrame, config)
                        .orderedByHint(route, seed)
                    for (launch in lattice) {
                        // A later hazard must have a later grounded launch. This also
                        // prevents repeatedly assigning extra presses to one failed arc.
                        if (launch <= previousLaunch) continue
                        val launches = seed.parameters.gapLaunchFrames + launch
                        val parameters = seed.parameters.copy(gapLaunchFrames = launches)
                        if (!triedParameters.add(parameters)) continue
                        // Runway this launch left: frames before the hazard the nominal walk
                        // hit. A couple of frames of runway is real safety against clipping
                        // the lip; more than that is noise, and *unbounded* margin made the
                        // ranking race to the earliest certifiable frame -- the reported
                        // "jumps too early". Capped, equal-margin candidates tie and the
                        // probe's hinted launch ordering decides among them.
                        val margin = seed.marginSoFar +
                            (seed.blockedFrame - launch).coerceIn(0, LAUNCH_MARGIN_FRAME_CAP)
                        attempt(parameters, margin)?.let { nextFrontier += it }
                    }
                }
                frontier = nextFrontier
                jumpCount++
            }
        }

        // Pessimistic first (the anytime discipline applied to discovery): the
        // corridor-adherent tier -- tightest node tracking, latest brake -- runs with
        // its launch beam alone, and only a segment it truly cannot solve pays for
        // the full gait grid. A safe moving extension counts as solved: the next
        // continuous segment retries the obstacle from a *known moving entry state*,
        // which is exactly where escalation is cheapest and most honest.
        val gaits = config.lookAheadNodes.flatMap { lookAhead ->
            config.brakeDistances.map { brake -> lookAhead to brake }
        }
        val tiers = if (config.corridorAdherentFirst && gaits.size > 1) {
            listOf(gaits.subList(0, 1), gaits.subList(1, gaits.size))
        } else {
            listOf(gaits)
        }
        // Staging is intentionally limited to local/simple suffixes. On a long
        // hazard chain the alternate sprint family contributes landing-margin
        // diversity at later jumps; stopping after the first success improved time
        // but lost one certified runway frame in the live ascent+gap corpus.
        val stagePreferredSprint = config.corridorAdherentFirst &&
            route.edges.count { it.kind == CoarseMoveKind.JUMP_CANDIDATE } <= STAGED_BEAM_MAX_JUMP_EDGES
        for ((tierIndex, tier) in tiers.withIndex()) {
            if (stagePreferredSprint && tierIndex == 0) {
                // The old order deliberately ran both sprint families into the same
                // obstacle before using either diagnostic.  Search the preferred
                // family's failure-directed beam immediately instead.  If it
                // certifies (the common case), the second deliberate fall and its
                // whole launch lattice disappear; if it cannot, the other family is
                // still tried before the gait grid widens.
                for (sprint in config.sprintModes) {
                    sweep(tier, listOf(sprint))
                    if (best == null) beam()
                    if (best != null || bestExtension != null) break
                }
            } else {
                sweep(tier)
                if (best == null) beam()
            }
            if (best != null || bestExtension != null) break
        }

        val winner = best ?: run {
            val blocker = attempts.maxWithOrNull(
                compareBy<WalkingSeedAttempt> { it.blockedProgress }
                    .thenBy { -it.finalGoalError },
            )
            val progress = blocker?.blockedProgress
            return WalkingSeedSearchResult.NoSafeStop(
                attempts = attempts.toList(),
                extension = bestExtension?.toPublished(),
                blockedProgress = progress,
                deadEdge = progress?.let(route.edges::getOrNull),
            )
        }

        // Only the winner is replayed for dependencies. Probing after the stop, and
        // every rejected candidate, must not inflate the plan's correctness-bearing
        // read set -- so the tape is re-run alone on a fresh tracker.
        val tape = InputTape(winner.rollout.frames.take(winner.stopFrame + 1).map { it.input })
        val tracked = environment.trackingView()
        val certified = TrajectoryRolloutEngine.rollout(
            initialState = initialState,
            profile = profile,
            environment = tracked,
            program = tape,
            frameCount = tape.frameCount,
        )
        if (!certified.completed || certified.frames.size != tape.frameCount) {
            return WalkingSeedSearchResult.UnstableReplay(
                "accepted tape did not reproduce: ${certified.termination}"
            )
        }

        return WalkingSeedSearchResult.Success(
            sourceRoute = route,
            tape = tape,
            rollout = certified,
            parameters = winner.parameters,
            dependencies = route.dependencies + tracked.dependencies(),
            attempts = attempts.toList(),
            launchMarginFrames = winner.rank.launchMargin,
        )
    }

    /**
     * The frame a launch would have to beat, or null if no launch could help.
     *
     * `UnsupportedPhysics` belongs here even though it reads like a refusal about the
     * world. A body that walks off a ledge into lava is *falling*; the snapshot throws
     * during the step, so that frame is never recorded and [evaluate] never reaches its
     * below-route test, and the hazard surfaces as a physics problem instead of as the
     * fall it is. To a solver both mean the one thing that matters: the body needed to
     * leave the ground here. Without this, a route with lava under it dies at search
     * depth zero having never once tried pressing jump.
     *
     * [TrajectoryDiagnostic.OutsideSnapshot] is deliberately *not* a seed. That one is a
     * bug in our capture, and no launch can fix a block we never read.
     */
    private fun launchSeedFrame(diagnostic: TrajectoryDiagnostic?): Int? = when (diagnostic) {
        is TrajectoryDiagnostic.FellBelowRoute -> diagnostic.frame
        is TrajectoryDiagnostic.HorizontalCollision -> diagnostic.frame
        is TrajectoryDiagnostic.UnsupportedPhysics -> diagnostic.frame
        else -> null
    }

    private class Candidate(
        val parameters: WalkingSeedParameters,
        val rollout: TrajectoryRollout,
        val stopFrame: Int,
        val rank: TrajectoryRank,
    )

    /**
     * Scores the moving checkpoint at the farthest certified route horizon. The accepted
     * rollout still runs through [stopFrame], so the frames after the checkpoint are its
     * certified brake tail; stopping is a publication invariant, not the objective.
     */
    private fun candidate(
        parameters: WalkingSeedParameters,
        rollout: TrajectoryRollout,
        stopFrame: Int,
        route: CoarseRoutePlan,
        nodes: List<HorizontalPoint>,
        launchMargin: Int,
    ): Candidate {
        val last = minOf(stopFrame, rollout.frames.lastIndex)
        val progress = routeProgressByFrame(rollout, last, nodes)
        val horizon = progress.maxOrNull() ?: 0
        var elapsedPlusTail = Double.POSITIVE_INFINITY
        for (frame in 0..last) {
            if (progress[frame] != horizon) continue
            val tail = tailLowerBound(rollout.frames[frame].state, route, horizon)
            elapsedPlusTail = minOf(elapsedPlusTail, frame + 1.0 + tail.ticks)
        }

        return Candidate(
            parameters = parameters,
            rollout = rollout,
            stopFrame = stopFrame,
            rank = TrajectoryRank(
                certifiedAndSafe = true,
                certifiedHorizon = horizon,
                elapsedPlusTail = elapsedPlusTail,
                collisionEvents = collisionEvents(rollout, stopFrame),
                launchMargin = launchMargin,
                inputSwitches = inputSwitches(rollout, stopFrame),
            ),
        )
    }

    /** Counts contacts, not the number of frames spent sliding along one obstacle. */
    private fun collisionEvents(rollout: TrajectoryRollout, throughFrame: Int): Int {
        var previous = rollout.initialState.horizontalCollision
        var events = 0
        for (frame in 0..minOf(throughFrame, rollout.frames.lastIndex)) {
            val colliding = rollout.frames[frame].state.horizontalCollision
            if (colliding && !previous) events++
            previous = colliding
        }
        return events
    }

    private fun inputSwitches(rollout: TrajectoryRollout, throughFrame: Int): Int {
        val last = minOf(throughFrame, rollout.frames.lastIndex)
        var switches = 0
        for (frame in 1..last) {
            if (rollout.frames[frame - 1].input != rollout.frames[frame].input) switches++
        }
        return switches
    }

    /**
     * Index of the route **edge being crossed** when the rollout failed (C3).
     *
     * The nearest-node projection alone misattributes mid-edge failures: a long jump
     * that lands short is already nearer its landing node than its takeoff, so the
     * projection reports the landing node "reached" and edge attribution then blames
     * the *next* edge -- which, when that next edge is not a jump, silently disables
     * the M6 retire-and-reroute for the jump that actually failed. So after
     * projecting, the failure position decides: not yet arrived at the projected node
     * and still nearer the previous node means the previous edge is the blocker.
     */
    private fun diagnosticProgress(
        rollout: TrajectoryRollout,
        evaluation: Evaluation,
        nodes: List<HorizontalPoint>,
    ): Int {
        val diagnostic = evaluation.diagnostic
        // Attribute the edge from the state *entering* the failed step. At a gap, the
        // post-step body can already be geometrically closer to the landing node even
        // though it is falling short of it; using that state blames the edge after the
        // jump (or no edge at all). The pre-step state names the stance actually left.
        val failureEntryFrame = when (diagnostic) {
            is TrajectoryDiagnostic.FellBelowRoute,
            is TrajectoryDiagnostic.HorizontalCollision,
            is TrajectoryDiagnostic.HeadBonk,
            is TrajectoryDiagnostic.UnsupportedPhysics,
            is TrajectoryDiagnostic.OutsideSnapshot -> diagnostic.frame - 1

            else -> diagnostic?.frame ?: evaluation.stopFrame ?: rollout.frames.lastIndex
        }
        val throughFrame = if (diagnostic is TrajectoryDiagnostic.FellBelowRoute ||
            diagnostic is TrajectoryDiagnostic.HeadBonk ||
            diagnostic is TrajectoryDiagnostic.UnsupportedPhysics ||
            diagnostic is TrajectoryDiagnostic.OutsideSnapshot
        ) {
            // A failed jump can be horizontally closer to its landing while airborne.
            // The last grounded entry state is the proof of which route edge was launched.
            (minOf(failureEntryFrame, rollout.frames.lastIndex) downTo 0)
                .firstOrNull { rollout.frames[it].state.onGround }
                ?: failureEntryFrame
        } else {
            failureEntryFrame
        }
        val projected = routeProgress(rollout, throughFrame, nodes)
        val state = rollout.frames.getOrNull(minOf(throughFrame, rollout.frames.lastIndex))?.state
            ?: rollout.initialState
        if (projected > 0 && spliceDistanceSquared(nodes[projected], state) > EDGE_ARRIVAL_RADIUS_SQ) {
            val towardPrevious = spliceDistanceSquared(nodes[projected - 1], state)
            val towardNext = if (projected < nodes.lastIndex) {
                spliceDistanceSquared(nodes[projected + 1], state)
            } else {
                Double.POSITIVE_INFINITY
            }
            if (towardPrevious < towardNext) return projected - 1
        }
        return projected
    }

    private class ExtensionCandidate(
        val parameters: WalkingSeedParameters,
        val rollout: TrajectoryRollout,
        val spliceNodeIndex: Int,
        val goalError: Double,
        val sourceDiagnostic: TrajectoryDiagnostic,
        val elapsedPlusTail: Double,
        val collisionEvents: Int,
        val launchMargin: Int,
        val inputSwitches: Int,
    ) {
        fun betterThan(other: ExtensionCandidate): Boolean =
            compareValuesBy(
                this,
                other,
                ExtensionCandidate::elapsedPlusTail,
                ExtensionCandidate::collisionEvents,
                { -it.launchMargin },
                { -it.spliceNodeIndex },
                ExtensionCandidate::inputSwitches,
                ExtensionCandidate::goalError,
            ) < 0

        fun toPublished(): WalkingSeedSearchResult.Extension {
            val tape = InputTape(rollout.frames.map { it.input })
            return WalkingSeedSearchResult.Extension(
                tape, rollout, parameters, spliceNodeIndex, sourceDiagnostic, launchMargin,
            )
        }
    }

    /**
     * A walk that could not get past a point *on the ground*. The raw material of
     * jump discovery -- and the obstacle can be either kind:
     *
     * - it fell into a hole (`FellBelowRoute`), or
     * - it ran into a lip (`HorizontalCollision`).
     *
     * Both mean the same thing to a solver: the body needed to leave the ground here.
     * Seeding only from falls misses every rising jump, because a rise is a wall.
     */
    private class LaunchSeed(
        val parameters: WalkingSeedParameters,
        val rollout: TrajectoryRollout,
        val blockedFrame: Int,
        val blockedProgress: Int,
        val goalError: Double,
        /** Runway accumulated by launches already scheduled before this seed's hazard. */
        val marginSoFar: Int = 0,
    )

    /**
     * Keeps the failure beam behaviorally diverse.
     *
     * Before the final approach, every brake distance produces the same trace. The
     * old `sortedBy(goalError).take(6)` therefore spent the entire beam on six brake
     * variants of one sprint/lookahead controller and silently discarded the other
     * five gait families. Prefer the failures that reached furthest along the route,
     * then retain at most one launch schedule per behavioral family.
     */
    private fun selectGapSeeds(frontier: List<LaunchSeed>, limit: Int): List<LaunchSeed> =
        frontier.sortedWith(
            compareByDescending<LaunchSeed> { it.blockedProgress }
                .thenByDescending { it.blockedFrame }
                .thenBy { it.goalError },
        ).distinctBy {
            Triple(
                it.parameters.sprint,
                it.parameters.lookAheadNodes,
                it.parameters.stepUpJumpLeadDistance,
            )
        }.take(limit)

    /**
     * The grounded ticks shortly before the body left the route, latest first.
     *
     * Latest first because the last stance before the edge is the one with the most
     * run-up committed; earlier launches are the fallback when it overshoots.
     */
    private fun launchLattice(
        rollout: TrajectoryRollout,
        blockedFrame: Int,
        config: WalkingSeedSearchConfig,
    ): List<Int> {
        val earliest = maxOf(0, blockedFrame - config.gapLaunchWindowFrames)
        return (blockedFrame downTo earliest)
            .filter { launchFrame ->
                // Input f is evaluated against the state *before* frame f. The old
                // test inspected frames[f] (the state after the failed walking
                // input) and also excluded blockedFrame itself. That off-by-one is
                // mostly invisible on a long flat run, but it removes the only
                // viable launch when a continuously expanded suffix begins close
                // to a rising lip.
                if (launchFrame == 0) rollout.initialState.onGround
                else rollout.frames.getOrNull(launchFrame - 1)?.state?.onGround == true
            }
    }

    /**
     * The coarse probe's launch prior (C1): grounded frames nearest the hinted launch
     * point are tried first, so the beam lands its first attempts where the measured
     * reach arithmetic says the launch belongs. Purely an ordering -- every lattice
     * frame is still tried, and certification still decides -- but under a bounded
     * refinement budget (Phase 2) order is what makes the budget go far.
     */
    private fun List<Int>.orderedByHint(route: CoarseRoutePlan, seed: LaunchSeed): List<Int> {
        val edge = route.edges.getOrNull(seed.blockedProgress) ?: return this
        val hint = edge.jumpHint ?: return this
        val dx = (edge.to.x - edge.from.x).toDouble()
        val dz = (edge.to.z - edge.from.z).toDouble()
        val length = hypot(dx, dz)
        if (length == 0.0) return this
        val targetX = edge.from.x + 0.5 + dx / length * hint.launchOffsetBlocks
        val targetZ = edge.from.z + 0.5 + dz / length * hint.launchOffsetBlocks
        return sortedBy { frame ->
            val state = if (frame == 0) seed.rollout.initialState else seed.rollout.frames[frame - 1].state
            val ex = state.position.x - targetX
            val ez = state.position.z - targetZ
            ex * ex + ez * ez
        }
    }


    /**
     * Turns a failed rollout into a certified moving prefix. The diagnostic frame is
     * excluded; the last grounded state before it is an exact splice state, not a
     * place where execution will stop. Requiring progress by at least one coarse node
     * prevents a suffix retry loop at the same obstacle.
     */
    private fun extensionCandidate(
        parameters: WalkingSeedParameters,
        rollout: TrajectoryRollout,
        evaluation: Evaluation,
        route: CoarseRoutePlan,
        nodes: List<HorizontalPoint>,
        config: WalkingSeedSearchConfig,
        launchMargin: Int,
    ): ExtensionCandidate? {
        val diagnostic = evaluation.diagnostic ?: return null
        var safeLast = minOf(rollout.frames.lastIndex, diagnostic.frame - 1)
        if (config.maxFrames <= SHORT_HORIZON_SUFFIX_FRAMES &&
            diagnostic is TrajectoryDiagnostic.HorizontalCollision
        ) {
            parameters.gapLaunchFrames.lastOrNull()?.let { failedLaunch ->
                // A short worker which launched and still reached a grounded wall did
                // not solve that wall. Do not bake the failed press into the moving
                // prefix; the suffix must rediscover one real launch with its retained
                // momentum instead of producing a preparatory hop plus a late jump.
                safeLast = minOf(safeLast, failedLaunch - 1)
            }
        }
        if (safeLast < 0) return null
        val progressByFrame = routeProgressByFrame(rollout, safeLast, nodes)
        fun isValidSuffixStart(frame: Int): Boolean {
            val progress = progressByFrame[frame]
            if (progress <= 0) return false
            val state = rollout.frames[frame].state
            val node = nodes[progress]
            val nodeError = hypot(state.position.x - node.x, state.position.z - node.z)
            // Nearest-node projection advances at the midpoint. That is suitable for a
            // corridor suffix, but the terminal node has no suffix to correct residual
            // error: splicing it while still outside the goal radius strands the next
            // controller on a singleton route. An out-of-frames rollout that *nearly*
            // arrived (the long-route field failure: closest 0.39 blocks out at
            // 0.104 b/t after 160 frames) instead splices one node back, where the
            // next segment has a real suffix and fresh frames to certify the stop.
            if (progress == nodes.lastIndex && nodeError > config.goalRadius) return false
            // A deliberately short worker has no spare ticks for a suffix which begins
            // outside its own corridor: that retry immediately reports LeftCorridor.
            // Longer workers retain the established runway boundary; globally forcing
            // them to converge first cost a certified launch-margin frame on the live
            // ascent-to-gap chain.
            if (config.maxFrames <= SHORT_HORIZON_SUFFIX_FRAMES &&
                nodeError > config.maxCorridorDeviation
            ) return false
            return true
        }
        val spliceCandidates = (safeLast downTo 0).filter { frame ->
            val state = rollout.frames[frame].state
            state.onGround && state.velocity.horizontalLength() > config.stoppedSpeed &&
                isValidSuffixStart(frame)
        }
        val latestGrounded = spliceCandidates.firstOrNull() ?: return null
        val spliceFrame = when (diagnostic) {
            is TrajectoryDiagnostic.HorizontalCollision,
            is TrajectoryDiagnostic.FellBelowRoute,
            is TrajectoryDiagnostic.UnsupportedPhysics -> {
                // A failure boundary is not a horizon boundary. Handing off at the
                // last safe ground tick puts the new controller directly on the
                // obstacle, often with landing cooldown and no distance in which to
                // discover a span-4 launch. Keep an already-simulated moving runway
                // before the hazard; execution still sees one continuous tape.
                spliceCandidates.firstOrNull {
                    it <= safeLast - HAZARD_SPLICE_RUNWAY_FRAMES
                } ?: latestGrounded
            }

            else -> latestGrounded
        }
        val state = rollout.frames[spliceFrame].state
        val spliceNodeIndex = progressByFrame[spliceFrame]
        val node = nodes[spliceNodeIndex]
        val goalError = hypot(state.position.x - node.x, state.position.z - node.z)
        val prefixFrames = rollout.frames.take(spliceFrame + 1)
        val tail = tailLowerBound(state, route, spliceNodeIndex)
        return ExtensionCandidate(
            parameters = parameters,
            rollout = TrajectoryRollout(
                initialState = rollout.initialState,
                frames = prefixFrames,
                termination = TrajectoryRolloutTermination.Completed,
            ),
            spliceNodeIndex = spliceNodeIndex,
            goalError = goalError,
            sourceDiagnostic = diagnostic,
            elapsedPlusTail = spliceFrame + 1.0 + tail.ticks,
            collisionEvents = collisionEvents(rollout, spliceFrame),
            launchMargin = launchMargin,
            inputSwitches = inputSwitches(rollout, spliceFrame),
        )
    }





    /** Worker-only overlap retained before a collision/fall-driven controller handoff. */
    private const val HAZARD_SPLICE_RUNWAY_FRAMES = 16

    /**
     * Frames of pre-hazard launch runway that still count as safety margin. Roughly one
     * stride -- enough to clear a lip without scraping it. Anything earlier trades
     * landing depth for nothing and must not be rewarded.
     */
    private const val LAUNCH_MARGIN_FRAME_CAP = 3

    /** Within this of a route node, the body counts as arrived there for attribution. */
    private const val EDGE_ARRIVAL_RADIUS_SQ = 0.9 * 0.9

    /** Beyond this, both sprint families are retained for downstream-margin diversity. */
    private const val STAGED_BEAM_MAX_JUMP_EDGES = 2

    /** Below this budget, every published suffix must already be inside its corridor. */
    private const val SHORT_HORIZON_SUFFIX_FRAMES = 40

}
