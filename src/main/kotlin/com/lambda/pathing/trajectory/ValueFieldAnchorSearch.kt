package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.BrakeToStopProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.world.center
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment

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
    ) : CommitSupport {
        private val vocabulary = ActionSet(catalog, field, config, searchConfig)

        private var goalStance = route.goal
        private var goalPoint = goalStance.center(environment)
        private val attempts = AttemptAccumulator()

        private var routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

        private val frontier: Frontier = Frontier(
            field, config, searchConfig, routeIndex,
            incumbentFrames = { best?.frames },
        )

        private val horizon: HorizonController = HorizonController(
            searchConfig, field, frontier, clock, cursorFrame, onSafePrefix,
            support = this,
            probe = probe,
        )

        init {
            frontier.reachability = ReachabilityPolicy(horizon::canReach)
        }

        private var finishSweeps = 0
        private var sweepEpoch = 0
        private var blockedWaitMillis = 0L
        private var fruitlessWakes = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0

        private var expansions = 0

        override val expansionCount: Int get() = expansions

        private val rollouts = AnchorRollout(
            catalog, field, config, searchConfig, environment, profile, { goalPoint },
            attempts, frontier::progressOf, probe,
        )

        private val gate = RolloutGate(profile, environment, config) { goalPoint }

        private val certifier = Certifier(initialState, profile, environment, cancelled)

        private val finisher = FinishPlanner(
            field, environment, config, gate, attempts, probe,
            goalPoint = { goalPoint },
            routeLastIndex = { route.nodes.lastIndex },
            progressOf = frontier::progressOf,
        )

        private var walking = false

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
                    finisher.finishFrom(anchor)?.let { retain(it) }
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
                        Solution.of(
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

        override fun brakeToStop(anchor: ValueAnchor): Solution? {
            val gated = gate.run(
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
            return Solution.of(
                anchor, frames,
                TerminalApproach(false, LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
                anchor.collisionEvents + collisionEvents(anchor.state, frames),
            )
        }

        override fun certify(solution: Solution): MotionPlanResult = certifier.certify(
            solution,
            route = route,
            remainingGuideTicks = field.guide(solution.anchor.stance),
            attemptCount = attempts.count,
        )

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

}
