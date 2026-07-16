/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotOutOfBoundsException
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.math.hypot

data class WalkingSeedSearchConfig(
    val maxFrames: Int = 160,
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
         * The first coarse edge of the suffix the search could not get past. When it is a
         * permissive [CoarseMoveKind.JUMP_CANDIDATE] -- one the mask admitted but no launch
         * certifies -- the coordinator may retire it and reroute (M6 negative feedback),
         * rather than failing the whole plan. Null when no edge remained to blame.
         */
        val deadEdge: CoarseEdge? = null,
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

        for (sprint in config.sprintModes) {
            for (lookAhead in config.lookAheadNodes) {
                for (brakeDistance in config.brakeDistances) {
                    for (jumpLeadDistance in jumpLeadDistances) {
                        val parameters = WalkingSeedParameters(sprint, lookAhead, brakeDistance, jumpLeadDistance)
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = initialState,
                            profile = profile,
                            environment = environment,
                            program = CorridorWalkingProgram(route.nodes, parameters, config),
                            frameCount = config.maxFrames,
                        )

                        val evaluation = evaluate(rollout, nodes, goal, config)
                        val final = rollout.finalState
                        val blockedProgress = diagnosticProgress(rollout, evaluation, nodes)
                        retainExtension(parameters, rollout, evaluation)
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            finalHorizontalSpeed = final.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = blockedProgress,
                        )

                        val blocked = launchSeedFrame(evaluation.diagnostic)
                        if (blocked != null) {
                            launchSeeds += LaunchSeed(
                                parameters = parameters,
                                rollout = rollout,
                                blockedFrame = blocked,
                                blockedProgress = blockedProgress,
                                goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            )
                        }

                        val frame = evaluation.stopFrame ?: continue
                        val candidate = candidate(
                            parameters, rollout, frame, route, nodes, launchMargin = 0,
                        )
                        val incumbent = best
                        if (incumbent == null || candidate.rank < incumbent.rank) {
                            best = candidate
                        }
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
        if (best == null && launchSeeds.isNotEmpty() && config.maxGapJumps > 0) {
            var frontier: List<LaunchSeed> = launchSeeds
            val triedParameters = HashSet<WalkingSeedParameters>()
            var jumpCount = 0
            while (best == null && frontier.isNotEmpty() && jumpCount < config.maxGapJumps) {
                val nextFrontier = ArrayList<LaunchSeed>()
                for (seed in selectGapSeeds(frontier, config.maxGapSeeds)) {
                    val previousLaunch = seed.parameters.gapLaunchFrames.lastOrNull() ?: -1
                    for (launch in launchLattice(seed.rollout, seed.blockedFrame, config)) {
                        // A later hazard must have a later grounded launch. This also
                        // prevents repeatedly assigning extra presses to one failed arc.
                        if (launch <= previousLaunch) continue
                        val launches = seed.parameters.gapLaunchFrames + launch
                        val parameters = seed.parameters.copy(gapLaunchFrames = launches)
                        if (!triedParameters.add(parameters)) continue
                        // Runway this launch left: frames before the hazard the nominal walk
                        // hit. An earlier launch clears the obstacle with margin to spare.
                        val margin = seed.marginSoFar + (seed.blockedFrame - launch).coerceAtLeast(0)
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = initialState,
                            profile = profile,
                            environment = environment,
                            program = CorridorWalkingProgram(route.nodes, parameters, config),
                            frameCount = config.maxFrames,
                        )
                        val evaluation = evaluate(rollout, nodes, goal, config)
                        val final = rollout.finalState
                        val blockedProgress = diagnosticProgress(rollout, evaluation, nodes)
                        retainExtension(parameters, rollout, evaluation, margin)
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                            finalHorizontalSpeed = final.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = blockedProgress,
                        )

                        val blocked = launchSeedFrame(evaluation.diagnostic)
                        if (blocked != null) {
                            nextFrontier += LaunchSeed(
                                parameters = parameters,
                                rollout = rollout,
                                blockedFrame = blocked,
                                blockedProgress = blockedProgress,
                                goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                                marginSoFar = margin,
                            )
                        }

                        val frame = evaluation.stopFrame ?: continue
                        val candidate = candidate(
                            parameters, rollout, frame, route, nodes, launchMargin = margin,
                        )
                        val incumbent = best
                        if (incumbent == null || candidate.rank < incumbent.rank) {
                            best = candidate
                        }
                    }
                }
                frontier = nextFrontier
                jumpCount++
            }
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
        val progress = IntArray(last + 1) { frame -> routeProgress(rollout, frame, nodes) }
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
        return routeProgress(rollout, throughFrame, nodes)
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

    private class Evaluation(val stopFrame: Int?, val diagnostic: TrajectoryDiagnostic?)

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
        val groundedMovingFrames = (safeLast downTo 0).filter { frame ->
            val state = rollout.frames[frame].state
            state.onGround && state.velocity.horizontalLength() > config.stoppedSpeed
        }
        fun isValidSuffixStart(frame: Int): Boolean {
            val progress = routeProgress(rollout, frame, nodes)
            if (progress <= 0) return false
            val state = rollout.frames[frame].state
            val node = nodes[progress]
            return hypot(state.position.x - node.x, state.position.z - node.z) <= config.maxCorridorDeviation
        }
        // A deliberately short worker has no spare ticks for a suffix which begins
        // outside its own corridor: that retry immediately reports LeftCorridor and
        // used to make the failed hop before this boundary permanent. Longer workers
        // retain the established runway boundary for multi-rise/gap throughput.
        val spliceCandidates = if (config.maxFrames <= SHORT_HORIZON_SUFFIX_FRAMES) {
            groundedMovingFrames.filter(::isValidSuffixStart)
        } else {
            groundedMovingFrames
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
        val spliceNodeIndex = routeProgress(rollout, spliceFrame, nodes)
        if (spliceNodeIndex <= 0) return null

        val node = nodes[spliceNodeIndex]
        val goalError = hypot(state.position.x - node.x, state.position.z - node.z)
        // Nearest-node projection advances at the midpoint. That is suitable for a
        // corridor suffix, but the terminal node has no suffix to correct residual
        // error: splicing it while still outside the goal radius strands the next
        // controller on a singleton route. Only name the goal once it is truly reached.
        if (spliceNodeIndex == nodes.lastIndex && goalError > config.goalRadius) return null
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

    /**
     * Same monotone local progress rule as the controller; never snap across a folded route.
     *
     * [throughFrame] may name a frame that was never recorded: a rejected step (lava, an
     * uncaptured block) reports the frame it *died on*, and the rollout stops one short of
     * it. Progress through a frame that does not exist is progress through the last one
     * that does.
     */
    private fun routeProgress(
        rollout: TrajectoryRollout,
        throughFrame: Int,
        nodes: List<HorizontalPoint>,
    ): Int {
        var progress = 0
        for (frame in 0..minOf(throughFrame, rollout.frames.lastIndex)) {
            val state = rollout.frames[frame].state
            val limit = minOf(nodes.lastIndex, progress + 2)
            var best = progress
            var bestSquared = spliceDistanceSquared(nodes[progress], state)
            for (index in progress + 1..limit) {
                val squared = spliceDistanceSquared(nodes[index], state)
                if (squared < bestSquared) {
                    best = index
                    bestSquared = squared
                }
            }
            progress = best
        }
        return progress
    }

    private fun spliceDistanceSquared(node: HorizontalPoint, state: MovementSimulationState): Double {
        val dx = state.position.x - node.x
        val dy = state.position.y - node.y
        val dz = state.position.z - node.z
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Walks the rollout once and reports the **first** thing that went wrong, so the
     * diagnostic names the earliest cause rather than a downstream symptom.
     */
    private fun evaluate(
        rollout: TrajectoryRollout,
        nodes: List<HorizontalPoint>,
        goal: HorizontalPoint,
        config: WalkingSeedSearchConfig,
    ): Evaluation {
        val floor = nodes.minOf { it.y } - FALL_TOLERANCE
        var stable = 0

        // Vanilla's fall distance is the drop from the apex of the current airborne
        // arc, and it resets on every landing -- so a chain of short walk-offs is
        // safe while one long one is not.
        var apex = rollout.initialState.position.y

        // A head bonk is remembered but not fatal: an arc can graze a ceiling and
        // still land where it meant to. It is only *reported* if the walk never
        // reaches a stable stop.
        var blocker: TrajectoryDiagnostic? = null

        rollout.frames.forEach { frame ->
            val state = frame.state
            val index = frame.index

            if (state.onGround) {
                val fallDistance = apex - state.position.y
                if (fallDistance > config.maxSafeFallDistance) {
                    return Evaluation(null, TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
                }
                apex = state.position.y
            } else {
                apex = maxOf(apex, state.position.y)
            }

            // A head bonk is a rising body meeting a ceiling; it kills the arc family,
            // so it must be told apart from an ordinary landing.
            val before = if (index == 0) rollout.initialState else rollout.frames[index - 1].state
            if (blocker == null && state.verticalCollision && before.velocity.y > 0.0 && !state.onGround) {
                blocker = TrajectoryDiagnostic.HeadBonk(index, state.position)
            }

            // Whether a wall matters depends on whether the body was *walking* into it.
            //
            // Grounded: the ground path is blocked. Fatal -- and it is exactly the
            // signal that a launch might be needed, so it seeds jump discovery.
            //
            // Airborne: a rising jump scrapes the very lip it is clearing. Vanilla
            // slides along it and the arc completes. Vetoing that would make every
            // rising jump uncertifiable, which is how the step-up route broke the
            // moment the coarse layer started preferring a rising jump over a step.
            if (state.horizontalCollision && state.onGround) {
                return Evaluation(null, TrajectoryDiagnostic.HorizontalCollision(index, state.position))
            }
            if (state.position.y < floor) {
                return Evaluation(null, TrajectoryDiagnostic.FellBelowRoute(index, floor - state.position.y))
            }
            val deviation = horizontalDistanceToPolyline(state.position.x, state.position.z, nodes)
            if (deviation > config.maxCorridorDeviation) {
                return Evaluation(null, TrajectoryDiagnostic.LeftCorridor(index, deviation))
            }

            val atGoal = hypot(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
                kotlin.math.abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
            val stopped = state.velocity.horizontalLength() <= config.stoppedSpeed
            stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
            if (stable >= config.stableStopFrames) return Evaluation(index, null)
        }

        (rollout.termination as? TrajectoryRolloutTermination.Rejected)?.let { rejected ->
            return Evaluation(
                null,
                when (val failure = rejected.failure) {
                    // Terrain we never captured is our bug, not the world's; it must not
                    // be counted alongside genuine lava/ladder refusals.
                    is SimulationSnapshotOutOfBoundsException ->
                        TrajectoryDiagnostic.OutsideSnapshot(rejected.frame, failure.pos)

                    else ->
                        TrajectoryDiagnostic.UnsupportedPhysics(rejected.frame, failure.message ?: "unsupported")
                },
            )
        }

        // Never got there. If something blocked it on the way, that is the actionable
        // cause; a bare "did not stop" is only the truth when nothing was in the way.
        blocker?.let { return Evaluation(null, it) }

        val final = rollout.finalState
        return Evaluation(
            null,
            TrajectoryDiagnostic.NoStop(
                frame = rollout.frames.size,
                goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
                speed = final.velocity.horizontalLength(),
            ),
        )
    }

    private class CorridorWalkingProgram(
        stanceNodes: List<Stance>,
        private val parameters: WalkingSeedParameters,
        private val config: WalkingSeedSearchConfig,
    ) : ControlProgram {
        private val maxYawChange = config.maxYawDegreesPerFrame
        private val nodes = stanceNodes.map { it.center() }
        /**
         * Rises this program will jump on its step-up schedule.
         *
         * Empty when no lead distance was chosen: the route's rises are then a gap
         * launch's business, not the step-up schedule's. Deriving these from node
         * geometry alone left `nextRise` stuck at zero -- `shouldJump` bails before
         * advancing it -- so a rise stayed permanently "pending" and the brake, which
         * waits for pending rises, never latched. The body then walked at full
         * throttle through the goal forever.
         */
        private val rises = if (parameters.stepUpJumpLeadDistance == null) {
            emptyList()
        } else {
            stanceNodes.zipWithNext().mapIndexedNotNull { index, (from, to) ->
                index.takeIf { to.y > from.y }
            }
        }

        /** Polyline distance from each node to the goal; the tail of the route. */
        private val distanceToGoal = DoubleArray(nodes.size).also { suffix ->
            for (index in nodes.lastIndex - 1 downTo 0) {
                suffix[index] = suffix[index + 1] + horizontalDistance(nodes[index], nodes[index + 1])
            }
        }

        /**
         * Pure-pursuit progress. It only ever advances, and by at most
         * [MAX_PROGRESS_ADVANCE] nodes per tick: a route that doubles back passes
         * close to its own earlier nodes, and a global nearest-node search would
         * snap the target across the fold and steer straight through the obstacle.
         */
        private var progressIndex = 0
        private var braking = false
        private var terminalApproach = false
        private var terminalReleased = false
        private var nextRise = 0
        private var jumpWasAirborne = false

        override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
            advanceProgress(observed)

            // Resolves this tick's launch and retires a rise once its landing is
            // observed, so it must run before the brake decision consumes it.
            // A gap launch is a *discovered* frame, not a geometric lead: backtracking
            // over a failed walk is what found it, so it is replayed by index.
            val gapLaunch = frame in parameters.gapLaunchFrames && observed.onGround
            val jump = shouldJump(observed) || gapLaunch

            // Brake on distance remaining *along the route*, not straight-line
            // distance to the goal: around an obstacle the player can be a stride
            // from the goal as the crow flies while most of the path is still
            // ahead, and braking there stalls short of the corner. A pending rise
            // also holds the brake off -- a coasting player has no momentum to
            // clear a step-up, so a rise on the final edge could never launch.
            val risePending = nextRise < rises.size
            val gapPending = parameters.gapLaunchFrames.any { frame <= it }

            // Once the body has stopped short of a tight goal, closing the last fraction
            // of a block is its own mode -- not more braking. The old code set the flag
            // and then fell straight back into the brake test below (remaining distance
            // is *inside* brakeDistance, that is what "short" means), so it re-braked to a
            // standstill every tick and never actually moved. The body then sat one
            // stride from the goal until the frame budget expired: "no stable stop after
            // 160 frames" with the closest attempt 0.23 blocks out.
            if (terminalApproach) return terminalApproachInput(observed, jump)

            if (braking) {
                val stoppedShort = observed.onGround &&
                    observed.velocity.horizontalLength() <= config.stoppedSpeed &&
                    hypot(
                        nodes.last().x - observed.position.x,
                        nodes.last().z - observed.position.z,
                    ) > config.goalRadius
                if (stoppedShort) {
                    braking = false
                    terminalApproach = true
                    return terminalApproachInput(observed, jump)
                }
                return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
            }
            if (!risePending && !gapPending &&
                remainingPathDistance(observed) <= parameters.brakeDistance
            ) braking = true
            if (braking) {
                return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
            }

            val target = nodes[minOf(nodes.lastIndex, progressIndex + parameters.lookAheadNodes)]
            val desiredYaw = Math.toDegrees(atan2(target.z - observed.position.z, target.x - observed.position.x)) - 90.0
            val yawDelta = Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
            return MovementSimulationInput(
                forward = 1.0,
                sprint = parameters.sprint,
                jump = jump,
                rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
            )
        }

        /**
         * Closes the last fraction of a block to a goal the body stopped short of.
         *
         * A plain non-sprint walk that releases -- and *latches* released
         * ([terminalReleased]) -- the moment either the body enters the goal radius or its
         * committed momentum will coast it there. Each piece is forced on us:
         *
         * - **Not sneak.** The manager writes its input after vanilla's `input.tick()` has
         *   already built the movement vector, so a pressed sneak never slows the live body;
         *   the simulator would certify a slowdown the client cannot reproduce (a live
         *   differential caught exactly this). Only a full walk is honest.
         * - **Release inside the radius, do not steer to the centre.** A walk from rest
         *   barely reaches the near edge, but chasing the centre overshoots it -- and once
         *   past, `atan2` flips 180° while the 30 deg/tick yaw cap cannot turn the body
         *   around, so it drives far past and slowly loops back. That loop *is* the
         *   circle-around-the-goal. Stopping at the near edge is inside the radius and does
         *   not trigger it.
         * - **The coast term** ([TERMINAL_COAST_PER_SPEED]) only matters for a faster entry:
         *   release early enough that friction lands the body in the radius rather than
         *   through it.
         * - **The latch** keeps a post-release drift from re-pressing and reopening the loop.
         */
        private fun terminalApproachInput(
            observed: MovementSimulationState,
            jump: Boolean,
        ): MovementSimulationInput {
            val goal = nodes.last()
            val dx = goal.x - observed.position.x
            val dz = goal.z - observed.position.z
            val distance = hypot(dx, dz)

            // Speed already committed toward the goal, and how far that coasts once the key
            // is released -- a decaying-friction geometric series, ~2.2 blocks per b/t.
            val speedTowardGoal = if (distance <= 1e-9) 0.0
            else (observed.velocity.x * dx + observed.velocity.z * dz) / distance
            val coast = speedTowardGoal * TERMINAL_COAST_PER_SPEED

            if (terminalReleased || distance <= config.goalRadius || coast >= distance) {
                terminalReleased = true
                return MovementSimulationInput(rotation = observed.rotation, sprint = false, jump = jump)
            }
            val desiredYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
            val yawDelta = Rotation.wrap(desiredYaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
            return MovementSimulationInput(
                forward = 1.0,
                sprint = false,
                jump = jump,
                rotation = Rotation(observed.rotation.yaw + yawDelta, observed.rotation.pitch),
            )
        }

        private fun advanceProgress(observed: MovementSimulationState) {
            val limit = minOf(nodes.lastIndex, progressIndex + MAX_PROGRESS_ADVANCE)
            var best = progressIndex
            var bestSquared = horizontalDistanceSquared(nodes[progressIndex], observed)
            for (index in progressIndex + 1..limit) {
                val squared = horizontalDistanceSquared(nodes[index], observed)
                if (squared < bestSquared) {
                    bestSquared = squared
                    best = index
                }
            }
            progressIndex = best
        }

        private fun remainingPathDistance(observed: MovementSimulationState): Double {
            val next = minOf(nodes.lastIndex, progressIndex + 1)
            val toNext = hypot(nodes[next].x - observed.position.x, nodes[next].z - observed.position.z)
            return toNext + distanceToGoal[next]
        }

        private fun shouldJump(observed: MovementSimulationState): Boolean {
            val leadDistance = parameters.stepUpJumpLeadDistance ?: return false
            if (nextRise >= rises.size) return false
            if (!observed.onGround) {
                jumpWasAirborne = true
                return false
            }
            if (jumpWasAirborne) {
                nextRise++
                jumpWasAirborne = false
                if (nextRise >= rises.size) return false
            }
            val takeoff = nodes[rises[nextRise]]
            return hypot(takeoff.x - observed.position.x, takeoff.z - observed.position.z) <= leadDistance
        }

        private fun horizontalDistanceSquared(node: HorizontalPoint, observed: MovementSimulationState): Double {
            val dx = node.x - observed.position.x
            val dz = node.z - observed.position.z
            return dx * dx + dz * dz
        }

        private companion object {
            /** A tick advances well under one block, so two nodes is generous headroom. */
            const val MAX_PROGRESS_ADVANCE = 2

            /**
             * Blocks a released walk coasts per b/t of committed speed. On ground the
             * next tick still advances by the full velocity before friction (~0.546)
             * decays it, so the coast sums to speed / (1 - 0.546) ~= 2.2. The terminal
             * approach releases when this coast reaches the goal, so a faster entry drifts
             * to rest in the radius rather than through it.
             */
            const val TERMINAL_COAST_PER_SPEED = 2.2
        }
    }

    private data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

    private fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

    private fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
        hypot(to.x - from.x, to.z - from.z)

    private fun horizontalDistanceToPolyline(x: Double, z: Double, nodes: List<HorizontalPoint>): Double {
        if (nodes.size == 1) return hypot(x - nodes[0].x, z - nodes[0].z)
        var bestSquared = Double.POSITIVE_INFINITY
        for (index in 0 until nodes.lastIndex) {
            val a = nodes[index]
            val b = nodes[index + 1]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val lengthSquared = dx * dx + dz * dz
            val projection = if (lengthSquared == 0.0) 0.0 else
                (((x - a.x) * dx + (z - a.z) * dz) / lengthSquared).coerceIn(0.0, 1.0)
            val ex = x - (a.x + projection * dx)
            val ez = z - (a.z + projection * dz)
            bestSquared = minOf(bestSquared, ex * ex + ez * ez)
        }
        return kotlin.math.sqrt(bestSquared)
    }

    private const val VERTICAL_GOAL_TOLERANCE = 0.05

    /** Below the lowest route node by this much means the body left the route downward. */
    private const val FALL_TOLERANCE = 0.6

    /** Worker-only overlap retained before a collision/fall-driven controller handoff. */
    private const val HAZARD_SPLICE_RUNWAY_FRAMES = 16

    /** Below this budget, every published suffix must already be inside its corridor. */
    private const val SHORT_HORIZON_SUFFIX_FRAMES = 40

}
