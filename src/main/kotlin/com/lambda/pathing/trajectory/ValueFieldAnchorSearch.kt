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
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

data class ValueFieldSearchConfig(
    val maxExpansions: Int = 900,
    /**
     * Expansions allowed since the incumbent last improved.
     *
     * The admissible bound is a straight-line heuristic, far too loose to prune a
     * frontier this wide, so without this the search always runs to [maxExpansions] —
     * tens of thousands of rollouts to confirm a five-block walk it certified almost
     * immediately. Stalling out instead makes the cost scale with how hard the route
     * actually is, which is what made a trivial live scenario cost a second of planning.
     */
    val stallExpansions: Int = 120,
    val maxTransitionFrames: Int = 40,
    val launchDelays: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6),
    /** Coarse steps offered out of one anchor. The route offered exactly one. */
    val branchingSteps: Int = 3,
    /**
     * How much worse than the best step an alternative may be priced and still be
     * simulated. Roughly one stride: the ties and near-ties the greedy route used to
     * break arbitrarily, and nothing else.
     */
    val branchMarginTicks: Double = 24.0,
    /**
     * Weight on the remaining-travel estimate when ordering the frontier.
     *
     * At 1.0 the search is breadth-like: it fans out across open ground and only reaches
     * the goal — and therefore its first certified tape — late, which is pure planning
     * latency. Weighting the tail drives it goalward first, so a tape exists early and
     * the incumbent cut can prune everything the field says cannot beat it. Ordering
     * only; termination still rests on the admissible bound.
     */
    val tailWeight: Double = 1.0,
    /**
     * Headings offered either side of the value-descent bearing, in degrees.
     *
     * This is the freedom the corridor removal alone did not buy: a pursuit action can
     * only ever walk between stance centres, so the trajectory stayed a grid path however
     * the search chose between them. A held heading is off-lattice by construction.
     */
    val headingFanDegrees: List<Double> = listOf(-24.0, -12.0, 12.0, 24.0),
    /** Steps of value-descending lookahead handed to the steering controller. */
    val chainLength: Int = 6,
    /** Ticks-to-go below which the braking terminal sweep is worth running. */
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
        require(headingFanDegrees.all { it.isFinite() })
        require(tailWeight >= 1.0)
        require(finishValueTicks >= 0.0)
        require(maxFinishSweeps >= 0)
        require(frontierPerKey > 0)
        require(speedBucketBlocks > 0.0)
        require(yawBucketDegrees > 0.0)
    }
}

/**
 * Kinodynamic anchor search steered by the coarse **value field** rather than by one
 * extracted route.
 *
 * [MotionAnchorSearch] is the same shape of search confined to a polyline: it steers at
 * `route.nodes`, indexes its state by route progress, and hard-rejects any rollout that
 * strays past `maxCorridorDeviation`. That makes the coarse layer's *greedy tie-breaks*
 * binding on the trajectory, which is not what the coarse layer knows. It knows the
 * cost-to-go field; the single chain is one readout of it.
 *
 * Here an anchor is identified by the **stance it stands on**, its successors are the
 * cheapest few coarse steps out of that stance by `edge + value`, and the controller
 * steers at a short lookahead chain redrawn from wherever the body actually is. There is
 * no corridor gate: leaving the greedy line is priced by the value field (going the wrong
 * way raises the tail), not vetoed. Everything else is unchanged — the simulator is still
 * the only thing that certifies anything, and the published tape is still one replay from
 * frame zero.
 *
 * The route is still accepted, but only for reporting and the coarse-feedback channel;
 * nothing in the search steers by it.
 */
object ValueFieldAnchorSearch {
    fun search(
        route: CoarseRoutePlan,
        field: CoarseValueField,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
    ): WalkingSeedSearchResult {
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }
            .filterTo(HashSet()) { it !in SUPPORTED_KINDS }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        return Search(route, field, initialState, profile, environment, config, searchConfig).run()
    }

    private val SUPPORTED_KINDS = setOf(
        CoarseMoveKind.WALK,
        CoarseMoveKind.STEP_UP,
        CoarseMoveKind.WALK_OFF,
        CoarseMoveKind.JUMP_CANDIDATE,
    )

    /** The stance a grounded body stands on. Feet Y is the stance's own Y by construction. */
    internal fun stanceOf(state: MovementSimulationState): Stance = Stance(
        floor(state.position.x).toInt(),
        floor(state.position.y + STANCE_LEVEL_EPSILON).toInt(),
        floor(state.position.z).toInt(),
    )

    private class ValueAnchor(
        val state: MovementSimulationState,
        val stance: Stance,
        val elapsed: Int,
        val collisionEvents: Int,
        val launchMargin: Int,
        val inputSwitches: Int,
        val parent: ValueAnchor?,
        val inputs: List<MovementSimulationInput>,
        val boundary: Int,
    ) {
        val speed: Double get() = state.velocity.horizontalLength()

        /** The direction the body is actually travelling; null when it carries no momentum. */
        fun heading(): Pair<Double, Double>? =
            if (speed <= 1e-6) null else state.velocity.x to state.velocity.z

        fun prefix(): List<MovementSimulationInput> {
            val chain = ArrayList<List<MovementSimulationInput>>()
            var node: ValueAnchor? = this
            while (node != null) {
                if (node.inputs.isNotEmpty()) chain += node.inputs
                node = node.parent
            }
            chain.reverse()
            return chain.flatten()
        }

        fun boundaries(): List<Int> {
            val frames = ArrayList<Int>()
            var node: ValueAnchor? = this
            while (node?.parent != null) {
                frames += node.boundary
                node = node.parent
            }
            return frames.asReversed().dropLast(1)
        }

        fun depth(): Int {
            var depth = 0
            var node: ValueAnchor? = this
            while (node?.parent != null) {
                depth++
                node = node.parent
            }
            return depth
        }
    }

    private class Solution(
        val inputs: List<MovementSimulationInput>,
        val boundaries: List<Int>,
        val segments: Int,
        val launchMargin: Int,
        val parameters: WalkingSeedParameters,
        val frames: Int,
        val collisionEvents: Int,
    )

    /**
     * Bucketed by the stance the body stands on, not by progress along anything.
     *
     * Bucketing by ticks-to-go instead — the route-free analogue of the confined search's
     * route-node index — was tried and is worse: it makes geographically distinct anchors
     * that happen to be equally far from the goal compete for the same three slots, which
     * pruned the only line that certified one corpus case at all.
     */
    private data class AnchorKey(
        val stance: Stance,
        val yawBucket: Int,
        val speedBucket: Int,
    )

    private sealed interface Action {
        val sprint: Boolean
        val step: Stance?

        /** Pure pursuit toward a stance centre: the grid-shaped baseline. */
        data class Walk(
            override val sprint: Boolean,
            override val step: Stance?,
            val lookAheadNodes: Int,
            val easeTurns: Boolean,
        ) : Action

        data class Launch(
            override val sprint: Boolean,
            override val step: Stance?,
            val delayFrames: Int,
        ) : Action

        /**
         * Hold a world heading, optionally jumping partway through. Off-lattice: the body
         * goes where this heading and its own momentum take it, not to a block centre.
         */
        data class Heading(
            override val sprint: Boolean,
            override val step: Stance?,
            val yaw: Double,
            val offsetDegrees: Double,
            val delayFrames: Int?,
        ) : Action
    }

    private sealed interface Outcome {
        data class Anchored(val anchor: ValueAnchor) : Outcome
        data class Arrived(val frames: List<SimulatedTrajectoryFrame>, val stopFrame: Int) : Outcome
        data class Rejected(val diagnostic: TrajectoryDiagnostic) : Outcome
    }

    private class Search(
        private val route: CoarseRoutePlan,
        private val field: CoarseValueField,
        private val initialState: MovementSimulationState,
        private val profile: PlayerPhysicsProfile,
        private val environment: SnapshotSimulationEnvironment,
        private val config: WalkingSeedSearchConfig,
        private val searchConfig: ValueFieldSearchConfig,
    ) {
        /**
         * The safety gates with the corridor test disabled. Deviation from a chain that
         * is redrawn at every anchor is not a safety property, and vetoing it is exactly
         * the confinement this search exists to remove. Every gate that describes the
         * *world* — falls, grounded collisions, head bonks, unsupported physics — is
         * untouched.
         */
        private val gateConfig = config.copy(maxCorridorDeviation = Double.MAX_VALUE)

        private val goalStance = route.goal
        private val goalPoint = goalStance.center()
        private val attempts = ArrayList<WalkingSeedAttempt>()
        private val dominance = HashMap<AnchorKey, MutableList<ValueAnchor>>()
        private val open = PriorityQueue<OpenEntry>(compareBy { it.order })
        private val routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }
        private var finishSweeps = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0
        private var bestValue = Double.POSITIVE_INFINITY
        private var deepestProgress = 0

        private class OpenEntry(val order: Double, val bound: Double, val anchor: ValueAnchor)

        fun run(): WalkingSeedSearchResult {
            admit(
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

            var expansions = 0
            while (open.isNotEmpty() && expansions < searchConfig.maxExpansions) {
                val entry = open.poll()
                // `elapsed + lowerBound` is admissible — it never reads a D* label — so
                // once the cheapest open anchor cannot beat the incumbent, nothing can.
                // The queue is *ordered* by the sharper value estimate; correctness of
                // the stop rests only on this bound.
                best?.let { if (entry.bound >= it.frames) return finish(it) }
                if (best != null && expansionsSinceImprovement >= searchConfig.stallExpansions) {
                    return finish(best!!)
                }
                expansions++
                expansionsSinceImprovement++

                val anchor = entry.anchor
                val remaining = field.guide(anchor.stance)
                // The terminal sweep is by far the most expensive thing here — a whole
                // brake/lead grid of full-length rollouts — so it must not run from an
                // anchor the field already says cannot beat the incumbent. Ungated, every
                // anchor that drifted into the finish radius paid for its own sweep,
                // which is the "it spends ages near the goal" seen in the renderer.
                if (remaining <= searchConfig.finishValueTicks &&
                    finishSweeps < searchConfig.maxFinishSweeps &&
                    best.let { it == null || anchor.elapsed + remaining < it.frames }
                ) {
                    finishSweeps++
                    finishFrom(anchor)?.let { retain(it) }
                }

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
                                    action.sprint, LOOK_AHEAD_NODES,
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
                deadEdge = null,
                remainingStart = route.nodes.getOrNull(deepestProgress),
                remainingGoal = goalStance,
                remainingMoveSummary = "value-field search; %d anchors, best value %.1f ticks"
                    .format(dominance.values.sumOf { it.size }, bestValue),
            )
        }

        /**
         * The action family out of one anchor: the cheapest few coarse steps by
         * `edge + value`, each in the walk styles and as a launch lattice.
         *
         * This is the direct replacement for `route.edges[nodeIndex]`. The route offered
         * one committed step; the field offers the [ValueFieldSearchConfig.branchingSteps]
         * best and lets the physics decide between them.
         */
        private fun actions(anchor: ValueAnchor): List<Action> {
            val steps = field.steps(
                anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
                anchor.heading(),
            )
            if (steps.isEmpty()) return emptyList()

            val actions = ArrayList<Action>()
            for (sprint in config.sprintModes) {
                for (step in steps) {
                    for ((lookAhead, ease) in WALK_STYLES) {
                        actions += Action.Walk(sprint, step.to, lookAhead, ease)
                    }
                }
            }
            // The off-lattice family, fanned around the bearing the value field is
            // descending toward. Offered from the best step only: a fan around a
            // second-choice direction is a fan around the wrong idea, and the second
            // choice gets its own fan once it has earned an anchor of its own.
            val bearing = descentBearing(anchor, steps.first().to)
            for (sprint in config.sprintModes) {
                for (offset in searchConfig.headingFanDegrees) {
                    actions += Action.Heading(
                        sprint, steps.first().to, bearing + offset, offset, delayFrames = null,
                    )
                }
            }

            // No launches once the goal is inside braking range. A jump cannot help a
            // body stop, it commits ticks of airborne time it cannot steer out of, and
            // the terminal sweep owns the arrival anyway — so every launch offered here
            // is a rollout spent proving it made things worse. This is the visible
            // "jumping around for ages before it stops".
            if (field.guide(anchor.stance) <= searchConfig.finishValueTicks) return actions

            // A gap taken off-axis is the case the grid cannot describe at all: leaving a
            // pad at an angle to line the body up for the jump after it.
            for (sprint in config.sprintModes) {
                for (offset in searchConfig.headingFanDegrees) {
                    for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                        actions += Action.Heading(
                            sprint, steps.first().to, bearing + offset, offset, delayFrames = delay,
                        )
                    }
                }
            }

            // Launches are offered on the two most promising steps only: a launch is
            // seven simulations, and a third-choice direction that also needs a jump is
            // reachable through the anchor its own best step creates.
            for (sprint in config.sprintModes) {
                for (step in steps.take(LAUNCH_STEPS)) {
                    for (delay in launchDelays(anchor, step)) {
                        actions += Action.Launch(sprint, step.to, delay)
                    }
                }
            }
            return actions
        }

        /**
         * The direction the value field is descending in, as a continuous bearing from
         * where the body actually stands — not a bearing between two block centres.
         *
         * Aimed a couple of steps out rather than at the immediate neighbour, so the fan
         * is spread around the line the route is going, not around one lattice step.
         */
        private fun descentBearing(anchor: ValueAnchor, firstStep: Stance): Double {
            val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD, anchor.heading())
            val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
            return Math.toDegrees(
                kotlin.math.atan2(aim.z - anchor.state.position.z, aim.x - anchor.state.position.x)
            ) - 90.0
        }

        private fun launchDelays(anchor: ValueAnchor, edge: CoarseEdge): List<Int> {
            val hint = edge.jumpHint ?: return searchConfig.launchDelays.sortedDescending()
            val from = edge.from.center()
            val to = edge.to.center()
            val along = alongEdge(from, to, anchor.state.position.x, anchor.state.position.z)
            val perTick = alongEdge(
                from, to,
                from.x + anchor.state.velocity.x,
                from.z + anchor.state.velocity.z,
            )
            return searchConfig.launchDelays.sortedBy { delay ->
                abs(along + delay * perTick - hint.launchOffsetBlocks)
            }
        }

        /**
         * One local transition, simulated once and stopped at its next event.
         *
         * The event is "the body is grounded and moving on a *different* stance than it
         * started on" — a physical fact about where the body is, not a projection onto a
         * planned node. A launch additionally has to actually leave the ground first.
         */
        private fun transition(anchor: ValueAnchor, action: Action, hazardFrame: Int?): Outcome {
            val chain = field.chain(
                anchor.stance, action.step, searchConfig.chainLength, anchor.heading(),
            )
            val points = chain.map { it.center() }
            val launch = when (action) {
                is Action.Launch -> LaunchTrigger(action.delayFrames)
                is Action.Heading -> action.delayFrames?.let { LaunchTrigger(it) }
                is Action.Walk -> null
            }
            val program = if (action is Action.Heading) {
                HeadingFollowerProgram(
                    targetYaw = action.yaw,
                    sprint = action.sprint,
                    maxYawChange = config.maxYawDegreesPerFrame,
                    launch = launch,
                )
            } else {
                SegmentFollowerProgram(
                    nodes = points,
                    startProgress = 0,
                    sprint = action.sprint,
                    lookAheadNodes = (action as? Action.Walk)?.lookAheadNodes ?: LOOK_AHEAD_NODES,
                    launch = launch,
                    maxYawChange = config.maxYawDegreesPerFrame,
                    easeTurns = (action as? Action.Walk)?.easeTurns == true,
                )
            }
            val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, gateConfig)
            var previous = anchor.state
            var airborne = false
            var failure: TrajectoryDiagnostic? = null
            var stopFrame: Int? = null
            var eventFrame: Int? = null
            var eventStance = anchor.stance

            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = anchor.state,
                profile = profile,
                environment = environment,
                program = program,
                frameCount = searchConfig.maxTransitionFrames,
            ) { frame ->
                val verdict = evaluator.observe(frame.index, frame.state, previous)
                previous = frame.state
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
                            val stance = stanceOf(frame.state)
                            val done = if (launch != null) launch.hasFired && airborne
                            else stance != anchor.stance
                            val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed
                            if (done && moving && stance != anchor.stance) {
                                eventFrame = frame.index
                                eventStance = stance
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }

            record(anchor, action, rollout, failure, stopFrame != null)

            stopFrame?.let { return Outcome.Arrived(rollout.frames, it) }
            failure?.let { return Outcome.Rejected(it) }
            val frame = eventFrame ?: return Outcome.Rejected(
                evaluate(rollout, points, goalPoint, gateConfig).diagnostic
                    ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
            )

            val frames = rollout.frames.take(frame + 1)
            // A landing on ground the coarse layer does not model as standable — or that
            // the value field cannot price — has no value and no usable successors.
            // Anchoring there strands the search on a state it can neither rank nor
            // continue, and ranking it by a straight-line guess is what sends the search
            // off in the wrong direction.
            if (!field.isStance(eventStance) || !field.isMapped(eventStance)) {
                return Outcome.Rejected(
                    TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
                )
            }

            return Outcome.Anchored(
                ValueAnchor(
                    state = frames.last().state,
                    stance = eventStance,
                    elapsed = anchor.elapsed + frames.size,
                    collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                    launchMargin = anchor.launchMargin + launchMargin(frames, hazardFrame),
                    inputSwitches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
                    parent = anchor,
                    inputs = frames.map { it.input },
                    boundary = anchor.elapsed + frames.size,
                ),
            )
        }

        /**
         * The terminal sweep: the legacy corridor follower with its brake, step-up lead
         * schedule and terminal reacquisition, run on a value-descending chain that
         * actually reaches the goal.
         */
        private fun finishFrom(anchor: ValueAnchor): Solution? {
            val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
            if (!field.reachesGoal(chain)) return null
            val points = chain.map { it.center() }
            val leads: List<Double?> = if (chain.zipWithNext().any { (from, to) -> to.y > from.y }) {
                config.stepUpJumpLeadDistances
            } else {
                listOf(null)
            }

            var bestFinish: Triple<TrajectoryRank, List<SimulatedTrajectoryFrame>, WalkingSeedParameters>? = null
            for (sprint in config.sprintModes) {
                for (brake in config.brakeDistances) {
                    for (lead in leads) {
                        val parameters = WalkingSeedParameters(sprint, LOOK_AHEAD_NODES, brake, lead)
                        val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, gateConfig)
                        var previous = anchor.state
                        var stopFrame: Int? = null
                        var failed = false
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = anchor.state,
                            profile = profile,
                            environment = environment,
                            program = CorridorFollowerProgram(chain, parameters, config),
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
                        val evaluation = evaluate(rollout, points, goalPoint, gateConfig)
                        PlanningDebugChannel.publishAttempt(rollout, stopFrame != null, evaluation.diagnostic)
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(
                                rollout.finalState.position.x - goalPoint.x,
                                rollout.finalState.position.z - goalPoint.z,
                            ),
                            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = progressOf(anchor.stance),
                        )
                        val stop = stopFrame ?: continue
                        if (failed) continue
                        val frames = rollout.frames.take(stop + 1)
                        val rank = TrajectoryRank(
                            certifiedAndSafe = true,
                            certifiedHorizon = route.nodes.lastIndex,
                            elapsedPlusTail = (anchor.elapsed + frames.size).toDouble(),
                            collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                            launchMargin = anchor.launchMargin,
                            inputSwitches = anchor.inputSwitches +
                                inputSwitches(anchor.inputs.lastOrNull(), frames),
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

        private fun certify(solution: Solution): WalkingSeedSearchResult {
            val tape = InputTape(solution.inputs)
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
                    "value-field tape did not reproduce: ${certified.termination}"
                )
            }
            return WalkingSeedSearchResult.Success(
                sourceRoute = route,
                tape = tape,
                rollout = certified,
                parameters = solution.parameters.copy(
                    gapLaunchFrames = certified.frames.filter { it.input.jump }.map { it.index },
                ),
                dependencies = route.dependencies + tracked.dependencies(),
                attempts = attempts.toList(),
                controlSegments = solution.segments,
                spliceFrames = solution.boundaries.filter { it in 1 until tape.frameCount },
                launchMarginFrames = solution.launchMargin,
            )
        }

        private fun retain(solution: Solution) {
            val incumbent = best
            if (incumbent == null ||
                solution.frames < incumbent.frames ||
                (solution.frames == incumbent.frames && solution.collisionEvents < incumbent.collisionEvents)
            ) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        private fun finish(solution: Solution): WalkingSeedSearchResult = certify(solution)

        private fun admit(anchor: ValueAnchor) {
            // The body's own stance can be unmapped (it may stand somewhere the coarse
            // layer never labelled). The root still has to be expanded, so it falls back
            // to the admissible bound; every later anchor is refused instead of guessed.
            val guide = field.guide(anchor.stance)
                .takeIf { it.isFinite() }
                ?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
            if (guide < bestValue) bestValue = guide
            deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

            val key = AnchorKey(
                stance = anchor.stance,
                yawBucket = floor(
                    ((anchor.state.rotation.yaw % 360.0) + 360.0) % 360.0 / searchConfig.yawBucketDegrees
                ).toInt(),
                speedBucket = floor(anchor.speed / searchConfig.speedBucketBlocks).toInt(),
            )
            val bucket = dominance.getOrPut(key) { ArrayList() }
            if (bucket.any { it.dominates(anchor) }) return
            bucket.removeAll { anchor.dominates(it) }
            if (bucket.size >= searchConfig.frontierPerKey) {
                val worst = bucket.maxByOrNull { it.elapsed } ?: return
                if (worst.elapsed <= anchor.elapsed) return
                bucket.remove(worst)
            }
            bucket += anchor
            // Incumbent cut. `guide` estimates the remaining travel closely (it is the
            // coarse layer's measured cost-to-go), so an anchor already projected to
            // finish later than a certified tape is not worth a rollout. The admissible
            // bound below still governs *termination*; this only declines to open a
            // branch the field says is already beaten — without it the free search keeps
            // fanning out across open ground long after it has a good tape, which is the
            // whole of the planning-latency regression.
            best?.let { if (anchor.elapsed + guide >= it.frames) return }

            open += OpenEntry(
                order = anchor.elapsed + searchConfig.tailWeight * guide,
                bound = anchor.elapsed + field.lowerBound(anchor.stance),
                anchor = anchor,
            )
        }

        private fun ValueAnchor.dominates(other: ValueAnchor): Boolean =
            elapsed <= other.elapsed &&
                speed >= other.speed - SPEED_DOMINANCE_SLACK &&
                collisionEvents <= other.collisionEvents &&
                inputSwitches <= other.inputSwitches

        /** Best-effort route index for reporting only; the search never steers by it. */
        private fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

        private fun record(
            anchor: ValueAnchor,
            action: Action,
            rollout: TrajectoryRollout,
            failure: TrajectoryDiagnostic?,
            stopped: Boolean,
        ) {
            PlanningDebugChannel.publishAttempt(rollout, stopped, failure)
            attempts += WalkingSeedAttempt(
                parameters = WalkingSeedParameters(
                    sprint = action.sprint,
                    lookAheadNodes = (action as? Action.Walk)?.lookAheadNodes ?: LOOK_AHEAD_NODES,
                    brakeDistance = config.brakeDistances.first(),
                    stepUpJumpLeadDistance = null,
                    gapLaunchFrames = if (action is Action.Walk) emptyList() else {
                        rollout.frames.filter { frame -> frame.input.jump }.map { frame -> frame.index }
                    },
                ),
                simulatedFrames = rollout.frames.size,
                finalGoalError = hypot(
                    rollout.finalState.position.x - goalPoint.x,
                    rollout.finalState.position.z - goalPoint.z,
                ),
                finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                diagnostic = failure,
                blockedProgress = progressOf(anchor.stance),
            )
        }
    }

    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    private const val LOOK_AHEAD_NODES = 1

    /** Grounded ticks after the anchor at which an off-axis launch may fire. */
    private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

    /** Stances ahead the descent bearing is aimed at, so the fan spreads around the line. */
    private const val BEARING_LOOKAHEAD = 2

    /** Coarse steps that also get a launch lattice; the rest are reached through anchors. */
    private const val LAUNCH_STEPS = 2

    /** Long enough for the brake schedule to see the goal from the finish horizon. */
    private const val FINISH_CHAIN_LENGTH = 24

    /** Feet Y sits exactly on the stance level; the epsilon absorbs float noise only. */
    private const val STANCE_LEVEL_EPSILON = 1e-6

    private const val SPEED_DOMINANCE_SLACK = 0.01
}
