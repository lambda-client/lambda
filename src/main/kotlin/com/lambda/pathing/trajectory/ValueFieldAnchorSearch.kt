package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.BrakeToStopProgram
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.CorridorFollowerProgram
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.world.center
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.hypot

data class ValueFieldSearchConfig(
    val maxExpansions: Int = 8000,
    val stallExpansions: Int = 3000,
    val maxTransitionFrames: Int = 40,
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

sealed interface WorldSyncResult {

    data object Quiet : WorldSyncResult

    data object Woken : WorldSyncResult

    data class Changed(val route: CoarseRoutePlan?) : WorldSyncResult
}

object ValueFieldAnchorSearch {

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

        worldWait: ((Long) -> Boolean)? = null,

        worldSync: ((CoarseRoutePlan) -> WorldSyncResult)? = null,

        sectionCapturable: ((Int, Int) -> Boolean)? = null,

        probe: SearchProbe = SearchProbe.NONE,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val unsupported = route.edges.mapTo(HashSet()) { it.movement }
            .filterTo(HashSet()) { !catalog.supports(it) }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return Search(
            route, catalog, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
            probe,
        ).run()
    }

    private const val CANDIDATE_PUBLISH_INTERVAL = 32

    private const val BLOCKED_WAIT_SLICE_MILLIS = 200L
    private const val MAX_BLOCKED_WAIT_MILLIS = 4_000L

    private const val WORLD_SYNC_INTERVAL = 64

    private const val MAX_FRUITLESS_WAKES = 2

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
        private val probe: SearchProbe,
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
            probe = probe,
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
            attempts, frontier::progressOf, probe,
        )

        private var walking = false

        private var provenFinish: TerminalApproach? = null

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

                    if (!frontier.hasParked && frontier.hasBlocked && worldWait != null &&
                        blockedWaitMillis < MAX_BLOCKED_WAIT_MILLIS &&
                        fruitlessWakes < MAX_FRUITLESS_WAKES
                    ) {
                        blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
                        if (worldWait.invoke(BLOCKED_WAIT_SLICE_MILLIS)) {
                            syncWorld()
                            frontier.wakeBlocked()

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

                val outcome = if (raw is Outcome.Blocked) {
                    frontier.parkBlocked(anchor, action)
                    val capturable = sectionCapturable?.invoke(raw.sectionX, raw.sectionZ) ?: true
                    probe.blocked(
                        raw.frame, raw.sectionX, raw.sectionY, raw.sectionZ,
                        capturable, anchor.stance, action.movement,
                    )
                    if (capturable) {
                        Outcome.Rejected(
                            TrajectoryDiagnostic.UnknownTerrain(raw.frame, raw.sectionX, raw.sectionY, raw.sectionZ),
                        )
                    } else raw
                } else raw
                probe.decision(action, outcome is Outcome.Rejected, (outcome as? Outcome.Rejected)?.diagnostic?.frame ?: 0)
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

                    is Outcome.Blocked -> Unit

                    is Outcome.Rejected -> {
                        familyOf(action)?.let { family ->

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
            probe.attempt(gated.rollout, gated.stopFrame != null, evaluation.diagnostic)
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

        private var certifiedInputs: List<MovementSimulationInput> = emptyList()
        private var certifiedFrames: List<SimulatedTrajectoryFrame> = emptyList()
        private var certifiedDependencies: List<Set<VoxelPos>> = emptyList()

        private fun certify(solution: Solution): MotionPlanResult {
            if (cancelled()) return MotionPlanResult.Cancelled
            val inputs = solution.inputs
            val tape = InputTape(inputs)

            var shared = 0
            val maxShared = minOf(inputs.size, certifiedInputs.size, certifiedFrames.size)
            while (shared < maxShared && inputs[shared] == certifiedInputs[shared]) shared++

            val frames = ArrayList<SimulatedTrajectoryFrame>(inputs.size)
            frames += certifiedFrames.subList(0, shared)
            val frameDependencies = ArrayList<Set<VoxelPos>>(inputs.size)
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
            val dependencies = HashSet<VoxelPos>()
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
            remainingStart = route.nodes.getOrNull(frontier.deepestProgress),
            remainingGoal = goalStance,
        )
    }

    private data class DecisionFamily(
        val movement: MovementId,
        val sprint: Boolean,
        val step: Stance?,
        val yaw: Double?,
        val keys: MovementKeys?,
        val airborneKeys: MovementKeys?,
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

    private fun divergenceFrame(action: TrajectoryDecision): Int = when (action) {
        is TrajectoryDecision.Heading -> action.delayFrames ?: Int.MAX_VALUE
        is TrajectoryDecision.Launch -> action.delayFrames
        else -> Int.MAX_VALUE
    }

    internal const val LOOK_AHEAD_NODES = 1

    internal const val COLLISION_FRAME_PENALTY = 4

    private const val BRAKE_TAIL_FRAMES = 24

    private const val RETRY_PENALTY_TICKS = 0.05

    private const val FINISH_CHAIN_LENGTH = 24

}
