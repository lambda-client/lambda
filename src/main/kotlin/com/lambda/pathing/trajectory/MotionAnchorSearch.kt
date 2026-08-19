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
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

data class MotionAnchorSearchConfig(
    /** Anchors expanded before the search gives up; each expansion is a few short rollouts. */
    val maxExpansions: Int = 900,
    /** A transition is a local decision; anything longer is not a transition. */
    val maxTransitionFrames: Int = 40,
    /**
     * Grounded ticks after the anchor on which a launch may be pressed. Ordered by the
     * arc probe's measured launch point when the edge carries a hint, latest-first
     * otherwise — the latest launch is the one with the most run-up committed.
     */
    val launchDelays: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6),
    /** How close to the goal an anchor must be before the terminal sweep is worth running. */
    val finishNodeHorizon: Int = 3,
    /** Terminal sweeps allowed per search; each is the legacy whole-suffix gait grid. */
    val maxFinishSweeps: Int = 64,
    /** Anchors retained per equivalence bucket after dominance filtering. */
    val frontierPerKey: Int = 3,
    val speedBucketBlocks: Double = 0.05,
    val yawBucketDegrees: Double = 20.0,
) {
    init {
        require(maxExpansions > 0)
        require(maxTransitionFrames > 0)
        require(launchDelays.isNotEmpty() && launchDelays.all { it >= 0 })
        require(finishNodeHorizon >= 0)
        require(maxFinishSweeps >= 0)
        require(frontierPerKey > 0)
        require(speedBucketBlocks > 0.0)
        require(yawBucketDegrees > 0.0)
    }
}

/**
 * Kinodynamic best-first search over grounded motion anchors (Phase 1.5).
 *
 * The whole-route beam it replaces discovered launch *N* by replaying the entire tape
 * containing launches `1..N-1`, so an independent hazard multiplied the work of every
 * hazard before it — 7,290 rollouts of a 426-frame tape on the bedrock corpus. Here a
 * node of the search is an exact body state ([MotionAnchor]), an action is one short
 * simulated transition to the next event, and a failure prunes that transition alone;
 * a certified prefix is shared by every descendant and is never simulated again.
 *
 * What does not change: the simulator is still the only thing that certifies anything,
 * the published tape is still replayed once from frame zero, and a refusal still proves
 * only [WalkingSeedSearchResult.EdgeFailureScope.CURRENT_ENTRY].
 */
object MotionAnchorSearch {
    fun search(
        route: CoarseRoutePlan,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
        anchorConfig: MotionAnchorSearchConfig = MotionAnchorSearchConfig(),
    ): WalkingSeedSearchResult {
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }
            .filterTo(HashSet()) { it !in SUPPORTED_KINDS }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        return Search(route, initialState, profile, environment, config, anchorConfig).run()
    }

    private val SUPPORTED_KINDS = setOf(
        CoarseMoveKind.WALK,
        CoarseMoveKind.STEP_UP,
        CoarseMoveKind.WALK_OFF,
        CoarseMoveKind.JUMP_CANDIDATE,
    )

    /**
     * One exact body state the search may continue from.
     *
     * Grounded and moving by construction: those are the states a later transition can
     * be certified from without inheriting an in-flight arc, and they are the states the
     * anytime runtime will splice at (final plan §2.3).
     */
    private class MotionAnchor(
        val state: MovementSimulationState,
        val nodeIndex: Int,
        val elapsed: Int,
        val collisionEvents: Int,
        val launchMargin: Int,
        val inputSwitches: Int,
        val parent: MotionAnchor?,
        val inputs: List<MovementSimulationInput>,
        val boundary: Int,
    ) {
        val speed: Double get() = state.velocity.horizontalLength()

        /** Inputs from the search's initial state to this anchor, oldest first. */
        fun prefix(): List<MovementSimulationInput> {
            val chain = ArrayList<List<MovementSimulationInput>>()
            var node: MotionAnchor? = this
            while (node != null) {
                if (node.inputs.isNotEmpty()) chain += node.inputs
                node = node.parent
            }
            chain.reverse()
            return chain.flatten()
        }

        /** Frame indices where one transition handed over to the next; execution ignores them. */
        fun boundaries(): List<Int> {
            val frames = ArrayList<Int>()
            var node: MotionAnchor? = this
            while (node?.parent != null) {
                frames += node.boundary
                node = node.parent
            }
            return frames.asReversed().dropLast(1)
        }

        fun depth(): Int {
            var depth = 0
            var node: MotionAnchor? = this
            while (node?.parent != null) {
                depth++
                node = node.parent
            }
            return depth
        }
    }

    /** A certified completion, kept until the frontier can no longer beat it. */
    private class Solution(
        val inputs: List<MovementSimulationInput>,
        val boundaries: List<Int>,
        val segments: Int,
        val launchMargin: Int,
        val parameters: WalkingSeedParameters,
        val frames: Int,
        val collisionEvents: Int,
    ) {
        /**
         * Winner selection currency: arrival time plus a toll per contact.
         *
         * Ranking on frames alone lets a tape that scrapes a wall and saves one tick beat
         * a clean one, which is the "it takes a collision instead of the better jump"
         * report. The toll was established at four frames per bump by the staircase work
         * (it cut a five-riser climb from ten bumps to four) and was lost when the search
         * was extracted out of the legacy sweep. It is a preference among *certified*
         * tapes only, never a safety gate: a necessary scrape — a rising jump grazing the
         * lip it clears — still wins when nothing cleaner certifies.
         */
        val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents
    }

    private data class AnchorKey(
        val nodeIndex: Int,
        val feetLevel: Int,
        val yawBucket: Int,
        val speedBucket: Int,
    )

    private sealed interface Action {
        val sprint: Boolean
        val lookAheadNodes: Int

        data class Walk(
            override val sprint: Boolean,
            override val lookAheadNodes: Int,
            val easeTurns: Boolean,
        ) : Action

        data class Launch(
            override val sprint: Boolean,
            override val lookAheadNodes: Int,
            val delayFrames: Int,
        ) : Action
    }

    private sealed interface Outcome {
        /** The transition ended at a new grounded, moving anchor. */
        data class Anchored(val anchor: MotionAnchor) : Outcome

        /** The transition itself walked the body to a certified stop at the goal. */
        data class Arrived(val frames: List<SimulatedTrajectoryFrame>, val stopFrame: Int) : Outcome

        data class Rejected(val diagnostic: TrajectoryDiagnostic, val progress: Int) : Outcome
    }

    private class Search(
        private val route: CoarseRoutePlan,
        private val initialState: MovementSimulationState,
        private val profile: PlayerPhysicsProfile,
        private val environment: SnapshotSimulationEnvironment,
        private val config: WalkingSeedSearchConfig,
        private val anchorConfig: MotionAnchorSearchConfig,
    ) {
        private val nodes = route.nodes.map { it.center() }
        private val goal = nodes.last()
        private val attempts = ArrayList<WalkingSeedAttempt>()
        private val dominance = HashMap<AnchorKey, MutableList<MotionAnchor>>()
        private val open = PriorityQueue<Pair<Double, MotionAnchor>>(compareBy { it.first })
        private var finishSweeps = 0
        private var best: Solution? = null
        private var deepestProgress = 0

        fun run(): WalkingSeedSearchResult {
            val root = MotionAnchor(
                state = initialState,
                nodeIndex = RouteProgressTracker(nodes).advance(initialState),
                elapsed = 0,
                collisionEvents = 0,
                launchMargin = 0,
                inputSwitches = 0,
                parent = null,
                inputs = emptyList(),
                boundary = 0,
            )
            admit(root)

            var expansions = 0
            while (open.isNotEmpty() && expansions < anchorConfig.maxExpansions) {
                // Best-first optimality: `elapsed + tail` is an admissible lower bound on
                // any completion through this anchor, so once the cheapest open anchor
                // cannot beat the certified incumbent, nothing left can. Returning the
                // *first* certified stop instead would publish whatever the frontier
                // happened to reach first -- which is how a gap chain certified eight
                // ticks slower than the whole-route sweep's line.
                val (bound, anchor) = open.poll()
                best?.let { if (bound >= it.frames) return finish(it) }
                expansions++

                if (route.nodes.lastIndex - anchor.nodeIndex <= anchorConfig.finishNodeHorizon &&
                    finishSweeps < anchorConfig.maxFinishSweeps
                ) {
                    finishSweeps++
                    finishFrom(anchor)?.let { retain(it) }
                }

                // Walks run first because they *measure* the hazard: the frame a plain
                // corridor follow leaves the ground or meets a wall is what a launch from
                // this same anchor has to beat, and it is the only honest local source of
                // the launch-runway margin the field metrics gate on.
                var hazardFrame: Int? = null
                val (walks, launches) = actions(anchor).partition { it is Action.Walk }
                for (action in walks + launches) {
                    when (val outcome = transition(anchor, action, hazardFrame)) {
                        is Outcome.Anchored -> admit(outcome.anchor)
                        is Outcome.Arrived -> retain(
                            Solution(
                                inputs = anchor.prefix() +
                                    outcome.frames.take(outcome.stopFrame + 1).map { it.input },
                                boundaries = anchor.boundaries() + anchor.elapsed,
                                segments = anchor.depth() + 1,
                                launchMargin = anchor.launchMargin,
                                parameters = WalkingSeedParameters(
                                    action.sprint, action.lookAheadNodes,
                                    config.brakeDistances.first(), null,
                                ),
                                frames = anchor.elapsed + outcome.stopFrame + 1,
                                collisionEvents = anchor.collisionEvents,
                            ),
                        )

                        is Outcome.Rejected -> if (action is Action.Walk) {
                            hazardFrame = launchSeedFrame(outcome.diagnostic)
                                ?.let { frame -> hazardFrame?.coerceAtMost(frame) ?: frame }
                                ?: hazardFrame
                        }
                    }
                }
            }

            best?.let { return finish(it) }

            return WalkingSeedSearchResult.NoSafeStop(
                attempts = attempts.toList(),
                blockedProgress = deepestProgress,
                deadEdge = route.edges.getOrNull(deepestProgress),
                remainingStart = route.nodes.getOrNull(deepestProgress),
                remainingGoal = route.goal,
                remainingMoveSummary = route.edges.drop(deepestProgress)
                    .groupingBy { edge ->
                        val span = maxOf(abs(edge.to.x - edge.from.x), abs(edge.to.z - edge.from.z))
                        "${edge.kind}(span=$span,dy=${edge.to.y - edge.from.y})"
                    }
                    .eachCount().entries.joinToString { (move, count) -> "$count $move" },
            )
        }

        /**
         * Walks, then launches. Launches are offered on every edge, not only on jump
         * candidates: a sprint-jump crosses flat ground ~30% faster than a sprint
         * ([com.lambda.pathing.coarse.CoarseMoveRates]), and on a rise or a gap the walk
         * simply fails and costs one short rollout to prove it.
         */
        private fun actions(anchor: MotionAnchor): List<Action> {
            if (anchor.nodeIndex >= route.edges.size) return emptyList()
            val edge = route.edges[anchor.nodeIndex]
            val actions = ArrayList<Action>()
            for (sprint in config.sprintModes) {
                for ((lookAhead, ease) in WALK_STYLES) actions += Action.Walk(sprint, lookAhead, ease)
            }
            for (sprint in config.sprintModes) {
                for (delay in launchDelays(anchor, edge)) {
                    actions += Action.Launch(sprint, LOOK_AHEAD_NODES, delay)
                }
            }
            return actions
        }

        /**
         * Orders the delay lattice by the arc probe's measured launch point (C1: the
         * probe is a prior on the search, never a decision). Where the body will be after
         * `k` grounded ticks is estimated from its committed velocity — good enough to
         * rank candidates, and every delay is still simulated and certified.
         */
        private fun launchDelays(anchor: MotionAnchor, edge: CoarseEdge): List<Int> {
            val hint = edge.jumpHint ?: return anchorConfig.launchDelays.sortedDescending()
            val from = edge.from.center()
            val to = edge.to.center()
            val along = alongEdge(from, to, anchor.state.position.x, anchor.state.position.z)
            val perTick = alongEdge(
                from, to,
                from.x + anchor.state.velocity.x,
                from.z + anchor.state.velocity.z,
            )
            return anchorConfig.launchDelays.sortedBy { delay ->
                abs(along + delay * perTick - hint.launchOffsetBlocks)
            }
        }

        /**
         * One local transition, simulated exactly once and stopped at its next event.
         *
         * A launch transition ends when the body lands; a walk transition ends when it
         * reaches the next route node. Either way the rollout stops there — the frames
         * beyond the event belong to whatever action is chosen next, and simulating them
         * speculatively is the work this search exists to avoid.
         */
        private fun transition(anchor: MotionAnchor, action: Action, hazardFrame: Int?): Outcome {
            val launch = (action as? Action.Launch)?.let { LaunchTrigger(it.delayFrames) }
            val program = SegmentFollowerProgram(
                nodes = nodes,
                startProgress = anchor.nodeIndex,
                sprint = action.sprint,
                lookAheadNodes = action.lookAheadNodes,
                launch = launch,
                maxYawChange = config.maxYawDegreesPerFrame,
                easeTurns = (action as? Action.Walk)?.easeTurns == true,
            )
            val evaluator = RolloutEvaluator(anchor.state, nodes, goal, config)
            val tracker = RouteProgressTracker(nodes, anchor.nodeIndex)
            var previous = anchor.state
            var airborne = false
            var failure: TrajectoryDiagnostic? = null
            var stopFrame: Int? = null
            var eventFrame: Int? = null
            var eventNode = anchor.nodeIndex

            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = anchor.state,
                profile = profile,
                environment = environment,
                program = program,
                frameCount = anchorConfig.maxTransitionFrames,
            ) { frame ->
                val verdict = evaluator.observe(frame.index, frame.state, previous)
                previous = frame.state
                val progress = tracker.advance(frame.state)
                when (verdict) {
                    is RolloutVerdict.Failed -> {
                        failure = verdict.diagnostic
                        true
                    }

                    is RolloutVerdict.Stopped -> {
                        stopFrame = verdict.frame
                        true
                    }

                    is RolloutVerdict.Continue -> {
                        if (!frame.state.onGround) {
                            airborne = true
                            false
                        } else {
                            // A launch has to be *taken* before its transition can end;
                            // otherwise the body reaches the next node first and the
                            // action degenerates into the walk it was meant to beat.
                            val node = attribute(frame.state, progress)
                            val done = if (launch != null) launch.hasFired && airborne
                            else node != null && node > anchor.nodeIndex
                            val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed
                            if (done && moving && node != null) {
                                eventFrame = frame.index
                                eventNode = node
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }

            val progressByFrame = routeProgressByFrame(rollout, rollout.frames.lastIndex, nodes)
            val endProgress = maxOf(anchor.nodeIndex, progressByFrame.lastOrNull() ?: anchor.nodeIndex)
            record(anchor, action, rollout, failure, stopFrame != null, endProgress)

            stopFrame?.let { return Outcome.Arrived(rollout.frames, it) }
            failure?.let { return Outcome.Rejected(it, endProgress) }
            val frame = eventFrame ?: return Outcome.Rejected(
                evaluate(rollout, nodes, goal, config).diagnostic
                    ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
                endProgress,
            )

            val frames = rollout.frames.take(frame + 1)
            return Outcome.Anchored(
                MotionAnchor(
                    state = frames.last().state,
                    nodeIndex = eventNode,
                    elapsed = anchor.elapsed + frames.size,
                    collisionEvents = anchor.collisionEvents +
                        collisionEvents(anchor.state, frames),
                    launchMargin = anchor.launchMargin + launchMargin(frames, hazardFrame),
                    inputSwitches = anchor.inputSwitches + inputSwitches(anchor, frames),
                    parent = anchor,
                    inputs = frames.map { it.input },
                    boundary = anchor.elapsed + frames.size,
                ),
            )
        }

        /**
         * The terminal sweep: the legacy corridor follower, run once on the remaining
         * suffix from an exact moving entry state.
         *
         * Reusing that controller keeps every hard-won stopping behaviour — the
         * physics-latched brake, the terminal pivot, the release latch — instead of
         * reimplementing arrival inside the anchor search.
         */
        private fun finishFrom(anchor: MotionAnchor): Solution? {
            val suffix = route.suffix(anchor.nodeIndex)
            val suffixNodes = suffix.nodes.map { it.center() }
            val suffixGoal = suffixNodes.last()
            val leads: List<Double?> = if (suffix.edges.any { it.kind == CoarseMoveKind.STEP_UP }) {
                config.stepUpJumpLeadDistances
            } else {
                listOf(null)
            }

            var bestFinish: Triple<TrajectoryRank, List<SimulatedTrajectoryFrame>, WalkingSeedParameters>? = null
            for (sprint in config.sprintModes) {
                for (brake in config.brakeDistances) {
                    for (lead in leads) {
                        val parameters = WalkingSeedParameters(sprint, LOOK_AHEAD_NODES, brake, lead)
                        val evaluator = RolloutEvaluator(anchor.state, suffixNodes, suffixGoal, config)
                        var previous = anchor.state
                        var stopFrame: Int? = null
                        var failed = false
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = anchor.state,
                            profile = profile,
                            environment = environment,
                            program = CorridorFollowerProgram(suffix.nodes, parameters, config),
                            frameCount = config.maxFrames,
                        ) { frame ->
                            val verdict = evaluator.observe(frame.index, frame.state, previous)
                            previous = frame.state
                            when (verdict) {
                                is RolloutVerdict.Stopped -> {
                                    stopFrame = verdict.frame
                                    true
                                }

                                is RolloutVerdict.Failed -> {
                                    failed = true
                                    true
                                }

                                is RolloutVerdict.Continue -> false
                            }
                        }
                        val evaluation = evaluate(rollout, suffixNodes, suffixGoal, config)
                        PlanningDebugChannel.publishAttempt(
                            rollout, stopFrame != null, evaluation.diagnostic,
                        )
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(
                                rollout.finalState.position.x - suffixGoal.x,
                                rollout.finalState.position.z - suffixGoal.z,
                            ),
                            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = anchor.nodeIndex,
                        )
                        val stop = stopFrame ?: continue
                        if (failed) continue
                        val frames = rollout.frames.take(stop + 1)
                        val rank = TrajectoryRank(
                            certifiedAndSafe = true,
                            certifiedHorizon = route.nodes.lastIndex,
                            elapsedPlusTail = (anchor.elapsed + frames.size).toDouble(),
                            collisionEvents = anchor.collisionEvents +
                                collisionEvents(anchor.state, frames),
                            launchMargin = anchor.launchMargin,
                            inputSwitches = anchor.inputSwitches + inputSwitches(anchor, frames),
                        )
                        val incumbent = bestFinish
                        if (incumbent == null || rank < incumbent.first) {
                            bestFinish = Triple(rank, frames, parameters)
                        }
                    }
                }
            }

            val winner = bestFinish ?: return null
            return Solution(
                inputs = anchor.prefix() + winner.second.map { it.input },
                boundaries = anchor.boundaries() + anchor.elapsed,
                segments = anchor.depth() + 1,
                launchMargin = anchor.launchMargin,
                parameters = winner.third,
                frames = anchor.elapsed + winner.second.size,
                collisionEvents = winner.first.collisionEvents,
            )
        }

        /**
         * The publication certificate, unchanged from the legacy search: one fresh replay
         * of the whole tape from the original state, on its own tracker, so the plan's
         * read set contains exactly what the published frames depend on.
         */
        private fun certify(
            inputs: List<MovementSimulationInput>,
            boundaries: List<Int>,
            segments: Int,
            launchMargin: Int,
            sprint: Boolean,
            lookAhead: Int,
            brake: Double = config.brakeDistances.first(),
            stepLead: Double? = null,
        ): WalkingSeedSearchResult {
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
                    "anchor tape did not reproduce: ${certified.termination}"
                )
            }
            return WalkingSeedSearchResult.Success(
                sourceRoute = route,
                tape = tape,
                rollout = certified,
                parameters = WalkingSeedParameters(
                    sprint = sprint,
                    lookAheadNodes = lookAhead,
                    brakeDistance = brake,
                    stepUpJumpLeadDistance = stepLead,
                    gapLaunchFrames = certified.frames.filter { it.input.jump }.map { it.index },
                ),
                dependencies = route.dependencies + tracked.dependencies(),
                attempts = attempts.toList(),
                controlSegments = segments,
                spliceFrames = boundaries.filter { it in 1 until tape.frameCount },
                launchMarginFrames = launchMargin,
            )
        }

        /** Keeps the fastest certified completion; ties break toward fewer contacts. */
        private fun retain(solution: Solution) {
            val incumbent = best
            if (incumbent == null || solution.score < incumbent.score) {
                best = solution
            }
        }

        private fun finish(solution: Solution): WalkingSeedSearchResult = certify(
            solution.inputs,
            solution.boundaries,
            solution.segments,
            solution.launchMargin,
            solution.parameters.sprint,
            solution.parameters.lookAheadNodes,
            solution.parameters.brakeDistance,
            solution.parameters.stepUpJumpLeadDistance,
        )

        /**
         * Dominance filter. Two anchors in the same bucket are interchangeable to every
         * future transition, so the slower one is dead weight — but "same bucket" keeps
         * support level and speed in the key, because the corpus proved a committed
         * single winner cannot split a coupled ascent/gap entry.
         */
        private fun admit(anchor: MotionAnchor) {
            if (anchor.nodeIndex > deepestProgress) deepestProgress = anchor.nodeIndex
            val key = AnchorKey(
                nodeIndex = anchor.nodeIndex,
                feetLevel = floor(anchor.state.position.y).toInt(),
                yawBucket = floor(
                    ((anchor.state.rotation.yaw % 360.0) + 360.0) % 360.0 / anchorConfig.yawBucketDegrees
                ).toInt(),
                speedBucket = floor(anchor.speed / anchorConfig.speedBucketBlocks).toInt(),
            )
            val bucket = dominance.getOrPut(key) { ArrayList() }
            if (bucket.any { it.dominates(anchor) }) return
            bucket.removeAll { anchor.dominates(it) }
            if (bucket.size >= anchorConfig.frontierPerKey) {
                val worst = bucket.maxByOrNull { it.elapsed } ?: return
                if (worst.elapsed <= anchor.elapsed) return
                bucket.remove(worst)
            }
            bucket += anchor
            val tail = tailLowerBound(anchor.state, route, anchor.nodeIndex)
            open += (anchor.elapsed + tail.ticks) to anchor
        }

        private fun MotionAnchor.dominates(other: MotionAnchor): Boolean =
            elapsed <= other.elapsed &&
                speed >= other.speed - SPEED_DOMINANCE_SLACK &&
                collisionEvents <= other.collisionEvents &&
                inputSwitches <= other.inputSwitches

        private fun record(
            anchor: MotionAnchor,
            action: Action,
            rollout: TrajectoryRollout,
            failure: TrajectoryDiagnostic?,
            stopped: Boolean,
            endProgress: Int,
        ) {
            PlanningDebugChannel.publishAttempt(rollout, stopped, failure)
            if (failure != null && endProgress > deepestProgress) deepestProgress = endProgress
            attempts += WalkingSeedAttempt(
                parameters = WalkingSeedParameters(
                    sprint = action.sprint,
                    lookAheadNodes = action.lookAheadNodes,
                    brakeDistance = config.brakeDistances.first(),
                    stepUpJumpLeadDistance = null,
                    gapLaunchFrames = (action as? Action.Launch)
                        ?.let { rollout.frames.filter { frame -> frame.input.jump }.map { frame -> frame.index } }
                        ?: emptyList(),
                ),
                simulatedFrames = rollout.frames.size,
                finalGoalError = hypot(
                    rollout.finalState.position.x - goal.x,
                    rollout.finalState.position.z - goal.z,
                ),
                finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                diagnostic = failure,
                blockedProgress = endProgress,
            )
        }

        /**
         * Which route node this grounded state actually stands on, or null for none.
         *
         * The landing/splice invariant, as *attribution* rather than as a veto. Nearest
         * node is horizontal-dominated in both directions: a jump that falls short lands
         * a block below the node it projects onto (and every successor then walks into
         * the wall it thinks it is standing on), while a jump that lands correctly on a
         * rise can still project back onto the takeoff node it has already left. So the
         * support level chooses the node, and horizontal distance only breaks ties among
         * the nodes on that level.
         */
        private fun attribute(state: MovementSimulationState, progress: Int): Int? {
            var best: Int? = null
            var bestDistance = Double.POSITIVE_INFINITY
            for (index in progress..minOf(nodes.lastIndex, progress + ATTRIBUTION_LOOKAHEAD)) {
                if (abs(state.position.y - nodes[index].y) > SUPPORT_LEVEL_TOLERANCE) continue
                val distance = spliceDistanceSquared(nodes[index], state)
                if (distance < bestDistance) {
                    best = index
                    bestDistance = distance
                }
            }
            return best
        }

        private fun inputSwitches(anchor: MotionAnchor, frames: List<SimulatedTrajectoryFrame>): Int =
            inputSwitches(anchor.inputs.lastOrNull(), frames)
    }

    /**
     * The walk family: tight tracking, a smoother two-node line, and a turn-easing
     * variant. The legacy sweep ran one of these for a whole route; here the choice is
     * re-made at every anchor, which is why three styles cover what nine gaits did.
     */
    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    /** Look-ahead used by launch transitions and by the terminal sweep. */
    private const val LOOK_AHEAD_NODES = 1

    /**
     * How far an anchor's feet may sit from its route node's level. A grounded body
     * standing on the projected stance is exactly on it; anything approaching half a
     * block is a different support.
     */
    private const val SUPPORT_LEVEL_TOLERANCE = 0.4

    /**
     * Nodes past the monotone projection that a landing may be attributed to. A jump
     * spans several stances, so its landing is legitimately ahead of the projection the
     * corridor tracker had while the body was still airborne.
     */
    private const val ATTRIBUTION_LOOKAHEAD = 2

    /** Frames a single contact is worth in winner selection; never a safety gate. */
    private const val COLLISION_FRAME_PENALTY = 4

    /** Speed difference below which two anchors are equally useful to a successor. */
    private const val SPEED_DOMINANCE_SLACK = 0.01
}
