/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.movement.BrakeToStopProgram
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.CorridorFollowerProgram
import com.lambda.pathing.movement.HorizontalPoint
import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.movement.center
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.floor
import kotlin.math.hypot

data class ValueFieldSearchConfig(
    val maxExpansions: Int = 8000,
    val stallExpansions: Int = 3000,
    val maxTransitionFrames: Int = 40,
    val launchDelays: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6),
    val branchingSteps: Int = 3,
    val branchMarginTicks: Double = 4.0,
    val headingFanDegrees: List<Double> = listOf(0.0, -12.0, 12.0),
    val headingCommitFrames: Int = 12,
    val siblingPenaltyTicks: Double = 3.0,
    val safePrefixFrames: Int = 20,
    val safePrefixDelayMillis: Long = 250,
    val horizonCommitFrames: Int = 20,
    val horizonRunwayFrames: Int = 30,
    val localHorizonFrames: Int = 0,
    val minCommitExpansions: Int = 0,
    val maxFinalCommitFrames: Int = 0,
    val chainLength: Int = 6,
    val finishValueTicks: Double = 11.0,
    val maxFinishSweeps: Int = 64,
    val frontierPerKey: Int = 3,
    val speedBucketBlocks: Double = 0.05,
    val yawBucketDegrees: Double = 20.0,
) {
    init {
        require(maxExpansions > 0)
        require(stallExpansions > 0)
        require(maxTransitionFrames > 0)
        require(launchDelays.isNotEmpty() && launchDelays.all { it >= 0 })
        require(branchingSteps > 0)
        require(chainLength > 0)
        require(siblingPenaltyTicks >= 0.0)
        require(safePrefixFrames > 0)
        require(safePrefixDelayMillis >= 0)
        require(horizonCommitFrames > 0)
        require(horizonRunwayFrames >= 0)
        require(headingFanDegrees.all { it.isFinite() })
        require(headingCommitFrames > 0)
        require(finishValueTicks >= 0.0)
        require(maxFinishSweeps >= 0)
        require(frontierPerKey > 0)
        require(speedBucketBlocks > 0.0)
        require(yawBucketDegrees > 0.0)
    }
}

/** What a world-event sync meant to the running search. */
sealed interface WorldSyncResult {
    /** No events at all. */
    data object Quiet : WorldSyncResult

    /**
     * Knowledge arrived, but nothing this search steers by changed. Blocked attempts
     * still wake -- a parked rollout's terrain may be exactly what arrived, and its
     * verdict (often a hazard lesson) must reach the search either way.
     */
    data object Woken : WorldSyncResult

    /** The guide field changed; [route] carries a refreshed route when one extracted. */
    data class Changed(val route: CoarseRoutePlan?) : WorldSyncResult
}

object ValueFieldAnchorSearch {
    /**
     * Attempt-composition tally for offline search tuning; off unless a probe enables it.
     *
     * The search's cost is rollouts, and which decision family burns them is invisible
     * from the outside -- this is how the blind-hop pool was found to be 59% of all
     * attempts. Kept cheap: one map insert per rollout when enabled, a single flag
     * check when not.
     */
    internal object Tally {
        var enabled = false
        val counts = java.util.concurrent.ConcurrentHashMap<String, IntArray>()

        fun record(action: com.lambda.pathing.movement.TrajectoryDecision, rejected: Boolean, frame: Int) {
            if (!enabled) return
            val key = when (action) {
                is com.lambda.pathing.movement.TrajectoryDecision.Heading -> "Heading(delay=" + action.delayFrames + ")"
                is com.lambda.pathing.movement.TrajectoryDecision.Launch -> "Launch(" + action.movement + ",delay=" + action.delayFrames + ")"
                else -> action::class.simpleName + "(" + action.movement + ")"
            }
            val row = counts.computeIfAbsent(key) { IntArray(3) }
            row[0]++
            if (rejected) {
                row[1]++
                row[2] += frame
            }
        }
    }

    fun search(
        route: CoarseRoutePlan,
        catalog: MovementCatalog,
        field: CoarseValueField,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: MotionConstraints = MotionConstraints(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
        onSafePrefix: ((MotionPlanResult.Success) -> Unit)? = null,
        cursorFrame: (() -> Int?)? = null,
        clock: SearchClock = SystemSearchClock(),
        cancelled: () -> Boolean = { false },
        /** Blocks briefly for world knowledge; true when events arrived. Null = never wait. */
        worldWait: ((Long) -> Boolean)? = null,
        /**
         * Folds pending world events into the coarse layer. Called between expansions
         * on the search's own thread; this is what makes the search continuous instead
         * of session-frozen. The result distinguishes "nothing this search can see
         * changed" from "the field changed" -- churning the search on irrelevant
         * events (the body ring capturing terrain far off-route) measurably degrades
         * tapes on small scenarios.
         */
        worldSync: ((CoarseRoutePlan) -> WorldSyncResult)? = null,
        /** Whether a section's chunk is loaded and trusted -- capturable right now. */
        sectionCapturable: ((Int, Int) -> Boolean)? = null,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val unsupported = route.edges.mapTo(HashSet()) { it.movement }
            .filterTo(HashSet()) { !catalog.supports(it) }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return Search(
            route, catalog, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
        ).run()
    }

    private const val CANDIDATE_PUBLISH_INTERVAL = 32

    /** Per-slice and total budget for waiting on world knowledge inside one search. */
    private const val BLOCKED_WAIT_SLICE_MILLIS = 200L
    private const val MAX_BLOCKED_WAIT_MILLIS = 4_000L

    /** Expansions between world-event syncs; knowledge lag is bounded by this. */
    private const val WORLD_SYNC_INTERVAL = 64

    /** Consecutive wakes that reopened nothing before the knowledge wait gives up. */
    private const val MAX_FRUITLESS_WAKES = 2



    internal const val MAX_SHOWN_CANDIDATES = 12

    /**
     * Which coarse stance a simulated body is standing in.
     *
     * Shares [Stance.of] with the planner's entry point deliberately: the two used to derive
     * this separately, and a body on a carpet was attributed to one cell by the search and a
     * different one by the route it was meant to be walking.
     */
    internal fun stanceOf(state: MovementSimulationState): Stance =
        Stance.of(state.position, state.onGround)

    private class GatedRollout(
        val rollout: TrajectoryRollout,
        val stopFrame: Int?,
        val failed: Boolean,
    )

    private class Search(
        private var route: CoarseRoutePlan,
        private val catalog: MovementCatalog,
        private val field: CoarseValueField,
        private val initialState: MovementSimulationState,
        private val profile: PlayerPhysicsProfile,
        private val environment: SnapshotSimulationEnvironment,
        private val config: MotionConstraints,
        private val searchConfig: ValueFieldSearchConfig,
        private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
        private val cursorFrame: (() -> Int?)?,
        private val clock: SearchClock,
        private val cancelled: () -> Boolean,
        private val worldWait: ((Long) -> Boolean)?,
        private val worldSync: ((CoarseRoutePlan) -> WorldSyncResult)?,
        private val sectionCapturable: ((Int, Int) -> Boolean)?,
    ) {
        private val vocabulary = ActionSet(catalog, field, config, searchConfig)

        private var goalStance = route.goal
        private var goalPoint = goalStance.center(environment)
        private val attempts = AttemptAccumulator()

        private var routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

        private val frontier: Frontier = Frontier(
            field, config, searchConfig, routeIndex,
            reachable = { horizon.canReach(it) },
            incumbentFrames = { best?.frames },
        )

        private val horizon: HorizonController = HorizonController(
            searchConfig, field, frontier, clock, cursorFrame, onSafePrefix,
            expansions = { expansions },
            brakeFrom = ::brakeFrom,
            certify = ::certify,
        )
        private var finishSweeps = 0
        private var sweepEpoch = 0
        private var blockedWaitMillis = 0L
        private var fruitlessWakes = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0

        private var expansions = 0

        private val rollouts = AnchorRollout(
            catalog, field, config, searchConfig, environment, profile, { goalPoint },
            attempts, frontier::progressOf,
        )

        private var walking = false

        private var provenFinish: TerminalApproach? = null

        /**
         * Folds arrived world knowledge into the running search.
         *
         * The frontier re-scores and blocked attempts wake on every batch; a changed
         * route re-targets the finish machinery in place. The anchor tree always
         * survives: its physics ran only over authoritative terrain, so knowledge
         * arrivals can extend what it may do next but never falsify what it did.
         */
        private fun syncWorld() {
            when (val result = worldSync?.invoke(route) ?: return) {
                WorldSyncResult.Quiet -> return

                WorldSyncResult.Woken -> {
                    if (frontier.hasBlocked) {
                        sweepEpoch++
                        finishSweeps = 0
                        frontier.wakeBlocked()
                    }
                }

                is WorldSyncResult.Changed -> {
                    sweepEpoch++
                    finishSweeps = 0
                    val next = result.route
                    if (next != null && (next.nodes != route.nodes || next.goal != route.goal)) {
                        route = next
                        goalStance = next.goal
                        goalPoint = goalStance.center(environment)
                        routeIndex = next.nodes.withIndex().associate { (index, node) -> node to index }
                        frontier.updateRoute(routeIndex)
                    } else {
                        frontier.rescore()
                    }
                    frontier.wakeBlocked()
                }
            }
        }

        fun run(): MotionPlanResult {
            if (cancelled()) return MotionPlanResult.Cancelled
            frontier.admit(
                ValueAnchor(
                    state = initialState,
                    stance = stanceOf(initialState),
                    elapsed = 0,
                    collisionEvents = 0,
                    launchMargin = 0,
                    inputSwitches = 0,
                    parent = null,
                    inputs = emptyList(),
                    boundary = 0,
                )
            )

            horizon.begin()
            while ((!frontier.isExhausted || frontier.hasBlocked) && expansions < searchConfig.maxExpansions) {
                if (cancelled()) return MotionPlanResult.Cancelled
                val root = horizon.safeAnchor
                if (root != null) {
                    val executing = cursorFrame?.invoke()
                    if (executing != null) {
                        walking = true
                        val runway = root.elapsed - executing
                        if (runway <= searchConfig.horizonRunwayFrames) {
                            horizon.commitFromCandidates(
                                urgent = runway <= searchConfig.horizonCommitFrames,
                                along = best?.takeIf { !readyToFinish(it) }?.anchor,
                            )
                        }
                    } else if (walking) {
                        return finish(best ?: return abandoned())
                    }
                }

                if (horizon.safeAnchor == null && frontier.hasParked) horizon.commitFromCandidates(urgent = false)

                if (!frontier.hasOpen) {
                    // Everything runnable is waiting on knowledge: wait for events --
                    // bounded -- and retry the blocked attempts. Standing still until
                    // the world arrives is the correct behavior at a knowledge
                    // frontier; fanning sideways was the old, wrong one.
                    if (!frontier.hasParked && frontier.hasBlocked && worldWait != null &&
                        blockedWaitMillis < MAX_BLOCKED_WAIT_MILLIS &&
                        fruitlessWakes < MAX_FRUITLESS_WAKES
                    ) {
                        blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
                        if (worldWait.invoke(BLOCKED_WAIT_SLICE_MILLIS)) {
                            syncWorld()
                            frontier.wakeBlocked()
                            // A wake that reopened nothing means the blocked terrain is
                            // not capturable right now (unstreamed): stop burning the
                            // budget and end the session with its best -- the walk
                            // itself is what will stream it.
                            if (frontier.hasOpen) fruitlessWakes = 0 else fruitlessWakes++
                        }
                        continue
                    }
                    val toward = best?.takeIf { !readyToFinish(it) }?.anchor
                    if (!frontier.hasParked && toward == null) break
                    if (!horizon.commitFromCandidates(urgent = true, along = toward)) break
                }
                if (expansions % WORLD_SYNC_INTERVAL == 0) syncWorld()
                if (expansions % CANDIDATE_PUBLISH_INTERVAL == 0) horizon.publishCandidates()
                val entry = frontier.poll()

                if (entry.anchor.elapsed >= horizon.horizonEnd &&
                    field.guide(entry.anchor.stance) > searchConfig.finishValueTicks
                ) {
                    frontier.park(entry)
                    continue
                }

                best?.let { if (entry.bound >= it.frames && readyToFinish(it)) return finish(it) }
                if (best != null && readyToFinish(best!!) &&
                    expansionsSinceImprovement >= searchConfig.stallExpansions
                ) {
                    return finish(best!!)
                }

                val anchor = entry.anchor
                val remaining = field.guide(anchor.stance)

                if (anchor.sweptEpoch != sweepEpoch &&
                    remaining <= searchConfig.finishValueTicks &&
                    finishSweeps < searchConfig.maxFinishSweeps &&
                    best.let { it == null || anchor.elapsed + remaining < it.frames }
                ) {
                    anchor.sweptEpoch = sweepEpoch
                    finishSweeps++
                    finishFrom(anchor)?.let { retain(it) }
                }

                val action = nextAction(anchor) ?: continue
                expansions++
                clock.onExpansion()
                expansionsSinceImprovement++

                val raw = rollouts.transition(anchor, action, anchor.hazardFrame)
                // Two kinds of unknown terrain, and conflating them was the wander bug
                // in both directions. CAPTURABLE unknown (loaded chunk, copy in flight)
                // is a brief wall: learn it like a rejection -- the lesson is corrected
                // on wake within ticks, and without it small-arena tapes degrade.
                // UNSTREAMED unknown is the walking frontier: it is NOT a hazard, and
                // hazard-learning there unlocks the heading fans whose lateral commits
                // are exactly the "walking around at the edge" seen live. Pure park;
                // the walk itself is what streams it.
                val outcome = if (raw is Outcome.Blocked) {
                    frontier.parkBlocked(anchor, action)
                    val capturable = sectionCapturable?.invoke(raw.sectionX, raw.sectionZ) ?: true
                    if (java.lang.Boolean.getBoolean("lambda.pathing.dumpFailures")) {
                        com.lambda.Lambda.LOG.info(
                            "[blocked] section ({}, {}, {}) capturable={} frame={} anchor={} action={}",
                            raw.sectionX, raw.sectionY, raw.sectionZ, capturable, raw.frame,
                            anchor.stance, action.movement,
                        )
                    }
                    if (capturable) {
                        Outcome.Rejected(
                            TrajectoryDiagnostic.UnknownTerrain(raw.frame, raw.sectionX, raw.sectionY, raw.sectionZ),
                        )
                    } else raw
                } else raw
                Tally.record(action, outcome is Outcome.Rejected, (outcome as? Outcome.Rejected)?.diagnostic?.frame ?: 0)
                when (outcome) {
                    is Outcome.Anchored -> {
                        frontier.admit(outcome.anchor)
                        horizon.publishPrefix(outcome.anchor, expansions)
                    }
                    is Outcome.Arrived -> retain(
                        solutionFrom(
                            anchor,
                            outcome.frames.take(outcome.stopFrame + 1),
                            TerminalApproach(
                                action.sprint, LOOK_AHEAD_NODES,
                                config.brakeDistances.first(), null,
                            ),
                            anchor.collisionEvents + collisionEvents(
                                anchor.state,
                                outcome.frames.take(outcome.stopFrame + 1),
                            ),
                        )
                    )

                    is Outcome.Blocked -> Unit // parked above; no hazard, no penalty

                    is Outcome.Rejected -> {
                        familyOf(action)?.let { family ->
                            // A failure before this member's launch tick lies in the
                            // frames every later-launching sibling replays verbatim.
                            if (outcome.diagnostic.frame < divergenceFrame(action)) {
                                anchor.familyPrefixFailures.merge(family, outcome.diagnostic.frame, ::minOf)
                            }
                        }
                        if (action is TrajectoryDecision.Walk) {
                            anchor.hazardFrame = launchSeedFrame(outcome.diagnostic)
                                ?.let { frame -> anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
                                ?: anchor.hazardFrame
                        }
                    }
                }

                if (hasUnattemptedAction(anchor)) {
                    val penalty = when (outcome) {
                        is Outcome.Rejected -> RETRY_PENALTY_TICKS
                        is Outcome.Blocked -> 0.0
                        else -> searchConfig.siblingPenaltyTicks
                    }
                    frontier.offer(
                        Frontier.OpenEntry(
                            order = entry.order + penalty,
                            bound = entry.bound,
                            anchor = anchor,
                        )
                    )
                }
            }

            best?.let { solution ->

                while (!readyToFinish(solution) &&
                    horizon.commitFromCandidates(urgent = true, along = solution.anchor)
                ) {
                }
                return finish(solution)
            }

            return MotionPlanResult.NoSafeStop(
                attemptCount = attempts.count,
                nearest = attempts.nearest,
                blockedProgress = frontier.deepestProgress,
                deadEdge = null,
                remainingStart = route.nodes.getOrNull(frontier.deepestProgress),
                remainingGoal = goalStance,
            )
        }

        private fun actions(anchor: ValueAnchor): List<TrajectoryDecision> {
            val hazard = anchor.hazardFrame
            val cached = anchor.actions
            if (cached != null && anchor.actionsHazardFrame == hazard) return cached
            return vocabulary.actions(anchor, hazard).also {
                anchor.actions = it
                anchor.actionsHazardFrame = hazard
            }
        }

        /**
         * The next decision worth simulating, minus the ones already proven to fail.
         *
         * A sibling whose launch would fire after a recorded shared-prefix failure is
         * skipped outright: its inputs are identical to the failed member's through the
         * frame that failed, so simulating it replays the exact same frames into the exact
         * same refusal. This is a proof, not a heuristic -- ordering and outcomes are
         * untouched, only the redundant rollouts disappear.
         *
         * Softer uses of the evidence -- deferring refused families, escalating the retry
         * penalty with each refusal -- were measured on the corpus and cut attempts by only
         * ~3% while drifting tape quality, so they were backed out. The plateau of
         * near-equal anchors, not per-anchor stubbornness, is where the attempts go; see
         * the corpus notes.
         */
        private fun nextAction(anchor: ValueAnchor): TrajectoryDecision? {
            for (action in actions(anchor)) {
                if (action in anchor.attempted) continue
                val family = familyOf(action)
                if (family != null) {
                    val prefixFail = anchor.familyPrefixFailures[family]
                    if (prefixFail != null && divergenceFrame(action) > prefixFail) {
                        anchor.attempted += action
                        continue
                    }
                }
                anchor.attempted += action
                return action
            }
            return null
        }

        private fun hasUnattemptedAction(anchor: ValueAnchor): Boolean =
            actions(anchor).any { it !in anchor.attempted }

        private fun finishFrom(anchor: ValueAnchor): Solution? {
            val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
            if (!field.reachesGoal(chain)) return null
            val points = chain.map { it.center(environment) }
            val leads: List<Double?> = if (chain.zipWithNext().any { (from, to) -> to.y > from.y }) {
                config.stepUpJumpLeadDistances
            } else {
                listOf(null)
            }

            val grid = buildList {
                provenFinish?.let { add(it) }
                for (sprint in config.sprintModes) {
                    for (brake in config.brakeDistances) {
                        for (lead in leads) {
                            add(TerminalApproach(sprint, LOOK_AHEAD_NODES, brake, lead))
                        }
                    }
                }
            }

            var bestRank: TrajectoryRank? = null
            var bestFrames: List<SimulatedTrajectoryFrame>? = null
            var bestParameters: TerminalApproach? = null
            for (parameters in grid) {
                val frames = terminalRun(anchor, chain, points, parameters) ?: continue
                val rank = TrajectoryRank(
                    certifiedAndSafe = true,
                    certifiedHorizon = route.nodes.lastIndex,
                    elapsedPlusTail = (anchor.elapsed + frames.size).toDouble(),
                    collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                    launchMargin = anchor.launchMargin,
                    inputSwitches = anchor.inputSwitches +
                        inputSwitches(anchor.inputs.lastOrNull(), frames),
                )
                if (parameters == provenFinish) {
                    return solutionFrom(anchor, frames, parameters, rank.collisionEvents)
                }
                val incumbent = bestRank
                if (incumbent == null || rank < incumbent) {
                    bestRank = rank
                    bestFrames = frames
                    bestParameters = parameters
                }
            }

            val rank = bestRank ?: return null
            val parameters = bestParameters!!
            provenFinish = parameters
            return solutionFrom(anchor, bestFrames!!, parameters, rank.collisionEvents)
        }

        private fun terminalRun(
            anchor: ValueAnchor,
            chain: List<Stance>,
            points: List<HorizontalPoint>,
            parameters: TerminalApproach,
        ): List<SimulatedTrajectoryFrame>? {
            val gated = gatedRollout(
                anchor.state, points,
                CorridorFollowerProgram(chain, parameters, config),
                config.maxFrames,
            )
            val evaluation = evaluate(gated.rollout, points, goalPoint, config)
            PlanningDebugChannel.publishAttempt(gated.rollout, gated.stopFrame != null, evaluation.diagnostic)
            attempts.record(PlanAttempt(
                parameters = parameters,
                simulatedFrames = gated.rollout.frames.size,
                finalGoalError = hypot(
                    gated.rollout.finalState.position.x - goalPoint.x,
                    gated.rollout.finalState.position.z - goalPoint.z,
                ),
                finalHorizontalSpeed = gated.rollout.finalState.velocity.horizontalLength(),
                diagnostic = evaluation.diagnostic,
                blockedProgress = frontier.progressOf(anchor.stance),
            ))
            val stop = gated.stopFrame ?: return null
            if (gated.failed) return null
            return gated.rollout.frames.take(stop + 1)
        }

        private fun brakeFrom(anchor: ValueAnchor): Solution? {
            val gated = gatedRollout(
                anchor.state, listOf(anchor.stance.center(environment)),
                BrakeToStopProgram(anchor.state.rotation.yaw),
                BRAKE_TAIL_FRAMES,
            )
            if (gated.failed) return null
            val rollout = gated.rollout
            var stable = 0
            var stableEnd = -1
            for (frame in rollout.frames) {
                stable = if (frame.state.onGround &&
                    frame.state.velocity.horizontalLength() <= config.stoppedSpeed
                ) stable + 1 else 0
                if (stable >= config.stableStopFrames) {
                    stableEnd = frame.index
                    break
                }
            }
            if (stableEnd < 0) return null
            val frames = rollout.frames.take(stableEnd + 1)

            val resting = stanceOf(frames.last().state)
            if (!field.isStance(resting) || !field.isMapped(resting)) return null
            return solutionFrom(
                anchor, frames,
                TerminalApproach(false, LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
                anchor.collisionEvents + collisionEvents(anchor.state, frames),
            )
        }

        private fun solutionFrom(
            anchor: ValueAnchor,
            tail: List<SimulatedTrajectoryFrame>,
            parameters: TerminalApproach,
            collisions: Int,
        ): Solution {
            return Solution(
                inputs = anchor.prefix() + tail.map { it.input },
                boundaries = anchor.boundaries() + anchor.elapsed,
                segments = anchor.depth() + 1,
                launchMargin = anchor.launchMargin,
                parameters = parameters,
                frames = anchor.elapsed + tail.size,
                collisionEvents = collisions,
                anchor = anchor,
            )
        }

        private fun gatedRollout(
            from: MovementSimulationState,
            points: List<HorizontalPoint>,
            program: ControlProgram,
            frameCount: Int,
        ): GatedRollout {
            val evaluator = RolloutEvaluator(from, points, goalPoint, config)
            var previous = from
            var stopFrame: Int? = null
            var failed = false
            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = from,
                profile = profile,
                environment = environment,
                program = program,
                frameCount = frameCount,
            ) { frame ->
                val verdict = evaluator.observe(frame.index, frame.state, previous)
                previous = frame.state
                when (verdict) {
                    is RolloutVerdict.Stopped -> { stopFrame = verdict.frame; true }
                    is RolloutVerdict.Failed -> { failed = true; true }
                    is RolloutVerdict.Continue -> false
                }
            }
            return GatedRollout(rollout, stopFrame, failed)
        }

        /** Inputs, frames and per-frame reads of the longest tape certified so far. */
        private var certifiedInputs: List<MovementSimulationInput> = emptyList()
        private var certifiedFrames: List<SimulatedTrajectoryFrame> = emptyList()
        private var certifiedDependencies: List<Set<com.lambda.pathing.world.VoxelPos>> = emptyList()

        /**
         * Certifies by replaying only what the last certification has not already proven.
         *
         * The environment is an immutable snapshot and the simulator is a pure function of
         * its [MovementSimulationState] -- the search already leans on that every time it
         * resumes a rollout from an anchor -- so a tape sharing a prefix with the last
         * certified tape will reproduce that prefix identically. Re-simulating it anyway
         * made each publication cost the whole walk so far: certification was O(n^2) over
         * a route's frames, and the safe-prefix publisher certifies on every commit.
         */
        private fun certify(solution: Solution): MotionPlanResult {
            if (cancelled()) return MotionPlanResult.Cancelled
            val inputs = solution.inputs
            val tape = InputTape(inputs)

            var shared = 0
            val maxShared = minOf(inputs.size, certifiedInputs.size, certifiedFrames.size)
            while (shared < maxShared && inputs[shared] == certifiedInputs[shared]) shared++

            val frames = ArrayList<SimulatedTrajectoryFrame>(inputs.size)
            frames += certifiedFrames.subList(0, shared)
            val frameDependencies = ArrayList<Set<com.lambda.pathing.world.VoxelPos>>(inputs.size)
            frameDependencies += certifiedDependencies.subList(0, shared)

            val resumeState = if (shared == 0) initialState else certifiedFrames[shared - 1].state
            val suffix = inputs.subList(shared, inputs.size)
            val tracked = environment.trackingView()
            val certified = TrajectoryRolloutEngine.rollout(
                initialState = resumeState,
                profile = profile,
                environment = tracked,
                program = InputTape(suffix),
                frameCount = suffix.size,
                observer = { _ ->
                    frameDependencies += tracked.takeFrameDependencies()
                    false
                },
            )
            if (!certified.completed || certified.frames.size != suffix.size) {
                return MotionPlanResult.UnstableReplay(
                    "value-field tape did not reproduce: ${certified.termination}"
                )
            }
            certified.frames.forEach { frame -> frames += frame.copy(index = shared + frame.index) }
            check(frameDependencies.size == inputs.size) {
                "Every certified input must publish its world-read dependencies"
            }
            val dependencies = HashSet<com.lambda.pathing.world.VoxelPos>()
            frameDependencies.forEach(dependencies::addAll)

            certifiedInputs = inputs
            certifiedFrames = frames
            certifiedDependencies = frameDependencies

            if (cancelled()) return MotionPlanResult.Cancelled
            return MotionPlanResult.Success(
                sourceRoute = route,
                tape = tape,
                rollout = TrajectoryRollout(initialState, frames, TrajectoryRolloutTermination.Completed),
                parameters = solution.parameters,
                safeAnchorStance = solution.anchor.stance,
                safeAnchorFrame = solution.anchor.elapsed,
                remainingGuideTicks = field.guide(solution.anchor.stance),
                frameDependencies = frameDependencies,
                // The immutable input tape is safety-certified by replay reads. Coarse
                // route reads guide future controls but cannot change already-fixed
                // input physics and are repaired independently by the persistent D*.
                dependencies = dependencies,
                attemptCount = attempts.count,
                controlSegments = solution.segments,
                spliceFrames = solution.boundaries.filter { it in 1 until tape.frameCount },
                launchMarginFrames = solution.launchMargin,
            )
        }

        private fun retain(solution: Solution) {
            val incumbent = best
            if (incumbent == null || solution.score < incumbent.score) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        private fun readyToFinish(solution: Solution): Boolean {
            if (searchConfig.maxFinalCommitFrames <= 0) return true
            val committedElapsed = horizon.safeAnchor?.elapsed ?: return true
            return solution.frames - committedElapsed <= searchConfig.maxFinalCommitFrames
        }

        private fun finish(solution: Solution): MotionPlanResult = certify(solution)

        private fun abandoned(): MotionPlanResult = MotionPlanResult.NoSafeStop(
            attemptCount = attempts.count,
            nearest = attempts.nearest,
            blockedProgress = frontier.deepestProgress,
            deadEdge = null,
            remainingStart = route.nodes.getOrNull(frontier.deepestProgress),
            remainingGoal = goalStance,
        )
    }

    /**
     * The launch-delay variants of one control, from one anchor.
     *
     * Everything but the delay: within a family the emitted inputs are identical until
     * the launch fires, which is what makes the shared-prefix pruning in `nextAction`
     * exact. Decisions with no delay dimension have no family and no such structure.
     */
    private data class DecisionFamily(
        val movement: com.lambda.pathing.movement.MovementId,
        val sprint: Boolean,
        val step: com.lambda.pathing.coarse.Stance?,
        val yaw: Double?,
        val keys: com.lambda.pathing.movement.MovementKeys?,
        val airborneKeys: com.lambda.pathing.movement.MovementKeys?,
    )

    private fun familyOf(action: TrajectoryDecision): Any? = when (action) {
        is TrajectoryDecision.Heading -> DecisionFamily(
            action.movement, action.sprint, action.step, action.yaw, action.keys, action.airborneKeys,
        )
        is TrajectoryDecision.Launch -> DecisionFamily(
            action.movement, action.sprint, action.step, null, null, null,
        )
        else -> null
    }

    /** The first frame on which this decision's inputs can differ from its family's. */
    private fun divergenceFrame(action: TrajectoryDecision): Int = when (action) {
        is TrajectoryDecision.Heading -> action.delayFrames ?: Int.MAX_VALUE
        is TrajectoryDecision.Launch -> action.delayFrames
        else -> Int.MAX_VALUE
    }

    internal const val LOOK_AHEAD_NODES = 1

    internal const val COLLISION_FRAME_PENALTY = 4

    private const val BRAKE_TAIL_FRAMES = 24

    private const val RETRY_PENALTY_TICKS = 0.05

    /** Where escalation stops: past this a re-offer is background noise, not a retry. */
    private const val MAX_RETRY_PENALTY_TICKS = 8.0

    private const val FINISH_CHAIN_LENGTH = 24

}
