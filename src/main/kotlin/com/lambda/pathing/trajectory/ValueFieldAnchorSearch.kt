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

        expandGuide: ((Double) -> Unit)? = null,

        adoptedSequence: (() -> Long)? = null,

        finalGoal: Stance? = null,

        probe: SearchProbe = SearchProbe.NONE,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val unsupported = route.edges.mapTo(HashSet()) { it.movement }
            .filterTo(HashSet()) { !catalog.supports(it) }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return Search(
            route, catalog, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
            expandGuide, adoptedSequence, finalGoal, probe,
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
        private val expandGuide: ((Double) -> Unit)?,
        private val adoptedSequence: (() -> Long)?,
        private val finalGoal: Stance?,
        private val probe: SearchProbe,
    ) : CommitSupport {
        private val vocabulary = ActionSet(catalog, field, config, searchConfig) { corridor() }

        private var goalStance = route.goal
        private var goalPoint = goalStance.center(environment)
        private val attempts = AttemptAccumulator()

        private var routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

        private val frontier: Frontier = Frontier(
            field, config, searchConfig, routeIndex,
            incumbentFrames = { best?.frames },
        )

        private val horizon: HorizonController = HorizonController(
            searchConfig, field, frontier, clock, cursorFrame, adoptedSequence, onSafePrefix,
            support = this,
            probe = probe,
        )

        init {
            frontier.reachability = ReachabilityPolicy(horizon::canReach)
        }

        private var finishSweeps = 0
        private var sweepEpoch = 0

        // The corridor widens under evidence: when the frontier makes no guide progress
        // for a while, successors with worse coarse bounds become proposable so the
        // search can flow around a trajectory-infeasible edge. The coarse graph itself
        // is never touched -- it stays a pure lower bound.
        private var corridorLevel = 0
        private var actionsEpoch = 0
        private var progressBaseline = Double.POSITIVE_INFINITY
        private var bestGuideSeen = Double.POSITIVE_INFINITY
        private var expansionsSinceGuideProgress = 0

        // Anchors whose corridor-level vocabulary is exhausted; revived when it widens.
        private val spentAnchors = ArrayList<ValueAnchor>()

        private var tapeRestarts = 0

        // A launch landing braked to rest inside the goal: certified only when nothing
        // better finishes -- a last resort, never a competitor to the finisher.
        private var brakedFallback: Solution? = null
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

        private var lastPublishedTip: ValueAnchor? = null
        private var expansionsWindowStart = 0

        private fun corridor(): CorridorLevel = CORRIDOR_LEVELS[corridorLevel]

        private fun revivable(): Boolean =
            spentAnchors.isNotEmpty() && corridorLevel < CORRIDOR_LEVELS.lastIndex

        private fun restartable(): Boolean =
            best == null && tapeRestarts < MAX_TAPE_RESTARTS &&
                horizon.latestBrakeContinuation() != null

        private fun restartFromTape(): Boolean {
            if (best != null || tapeRestarts >= MAX_TAPE_RESTARTS) return false
            val seed = horizon.latestBrakeContinuation() ?: return false
            tapeRestarts++
            corridorLevel = 0
            finishSweeps = 0
            sweepEpoch++
            actionsEpoch++
            progressBaseline = Double.POSITIVE_INFINITY
            bestGuideSeen = Double.POSITIVE_INFINITY
            expansionsSinceGuideProgress = 0
            expansionsWindowStart = expansions
            spentAnchors.clear()
            noteGuide(seed.stance)
            horizon.reRootForRestart(seed)
            return true
        }

        private fun noteGuide(stance: Stance) {
            val guide = field.guide(stance)
            if (guide < bestGuideSeen) bestGuideSeen = guide
            if (bestGuideSeen <= progressBaseline - GUIDE_PROGRESS_HYSTERESIS_TICKS) {
                progressBaseline = bestGuideSeen
                expansionsSinceGuideProgress = 0
                if (corridorLevel != 0) {
                    corridorLevel = 0
                    actionsEpoch++
                }
            }
        }

        private fun escalateCorridor(force: Boolean): Boolean {
            spentAnchors.retainAll { horizon.canReach(it) }
            // Exhaustion alone is not stall evidence: a healthy streaming walk exhausts
            // its committed subtree all the time. Widen only when the guide has not
            // moved either.
            if (force && expansionsSinceGuideProgress < FORCE_ESCALATION_MIN_EXPANSIONS) return false
            if (!force && expansionsSinceGuideProgress < ESCALATION_EXPANSIONS) return false
            if (corridorLevel >= CORRIDOR_LEVELS.lastIndex) return false
            if (!force && frontier.hasBlocked) return false
            corridorLevel++
            actionsEpoch++
            expansionsSinceGuideProgress = 0
            expandGuide?.invoke(CORRIDOR_LEVELS[corridorLevel].marginTicks)
            field.clearGuideCache()
            frontier.rescore()
            spentAnchors.forEach { frontier.reopen(it) }
            spentAnchors.clear()
            return true
        }

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

            noteGuide(stanceOf(initialState))
            horizon.begin()
            while ((!frontier.isExhausted || frontier.hasBlocked || revivable() || restartable()) &&
                expansions - expansionsWindowStart < searchConfig.maxExpansions
            ) {
                if (cancelled()) return MotionPlanResult.Cancelled
                val executing = cursorFrame?.invoke()
                if (executing != null) {
                    // Execution is the only irrevocable commitment: re-root onto what
                    // the body has actually pressed. On catch-up (the cursor entered
                    // the published brake tail) the search continues from the settled
                    // brake anchor -- same session, same frontier.
                    horizon.advanceExecutedRoot()
                    best?.let {
                        if (!horizon.canReach(it.anchor) || !executionCompatible(it.anchor)) {
                            best = null
                        }
                    }
                    // A surviving full solution to the final goal finalizes after a
                    // short improvement window -- before execution races invalidate it.
                    best?.let {
                        if (mayFinalize() && readyToFinish(it) &&
                            expansionsSinceImprovement >= FINAL_IMPROVEMENT_WINDOW
                        ) {
                            return finish(it)
                        }
                    }
                    val tip = horizon.safeAnchor
                    if (tip != null) {
                        val runway = tip.elapsed - executing
                        if (runway <= searchConfig.horizonRunwayFrames) {
                            horizon.commitFromCandidates(
                                urgent = runway <= searchConfig.horizonCommitFrames,
                                along = best?.takeIf { !readyToFinish(it) }?.anchor,
                            )
                        }
                    }
                }

                // Publication is progress: refresh the per-window budgets.
                if (horizon.safeAnchor !== lastPublishedTip) {
                    lastPublishedTip = horizon.safeAnchor
                    blockedWaitMillis = 0
                    fruitlessWakes = 0
                    expansionsWindowStart = expansions
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
                    // Total exhaustion at this corridor level is stall evidence in
                    // itself: widen the corridor and revive the spent anchors.
                    if (spentAnchors.isNotEmpty() && escalateCorridor(force = true)) continue

                    // The frontier genuinely drained. Restart the search from the
                    // newest published tape's settled continuation -- same session,
                    // same tape, fresh search state -- before giving up.
                    if (!frontier.hasParked && !frontier.hasBlocked && restartFromTape()) continue

                    // A truncated route cannot finalize; wait for it to extend toward
                    // the final goal instead of refusing.
                    if (!mayFinalize() && worldWait != null &&
                        blockedWaitMillis < MAX_BLOCKED_WAIT_MILLIS
                    ) {
                        blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
                        worldWait.invoke(BLOCKED_WAIT_SLICE_MILLIS)
                        syncWorld()
                        frontier.wakeBlocked()
                        continue
                    }
                    val toward = best?.takeIf { !readyToFinish(it) }?.anchor
                    if (!frontier.hasParked && toward == null) break
                    if (!horizon.commitFromCandidates(urgent = true, along = toward)) break
                    // Publishing no longer re-roots, so a successful commit does not
                    // refill the open list; re-evaluate instead of polling empty.
                    continue
                }
                if (expansions % WORLD_SYNC_INTERVAL == 0) syncWorld()
                if (expansions % CANDIDATE_PUBLISH_INTERVAL == 0) horizon.publishCandidates()
                val entry = frontier.poll() ?: continue

                if (entry.anchor.elapsed >= horizon.horizonEnd &&
                    field.guide(entry.anchor.stance) > searchConfig.finishValueTicks
                ) {
                    frontier.park(entry)
                    continue
                }

                best?.let {
                    if (mayFinalize() && entry.bound >= it.frames && readyToFinish(it)) {
                        return finish(it)
                    }
                }
                if (best != null && mayFinalize() && readyToFinish(best!!) &&
                    expansionsSinceImprovement >= searchConfig.stallExpansions
                ) {
                    return finish(best!!)
                }

                val anchor = entry.anchor
                val remaining = field.guide(anchor.stance)

                if (anchor.sweptEpoch != sweepEpoch &&
                    remaining <= searchConfig.finishValueTicks &&
                    finishSweeps < searchConfig.maxFinishSweeps &&
                    horizon.canReach(anchor) &&
                    best.let { it == null || anchor.elapsed + remaining < it.frames }
                ) {
                    anchor.sweptEpoch = sweepEpoch
                    finishSweeps++
                    finisher.finishFrom(anchor)?.let { retain(it) }
                }

                val action = nextAction(anchor)
                if (action == null) {
                    if (corridorLevel < CORRIDOR_LEVELS.lastIndex) spentAnchors += anchor
                    continue
                }
                expansions++
                clock.onExpansion()
                expansionsSinceImprovement++
                expansionsSinceGuideProgress++
                escalateCorridor(force = false)

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
                        noteGuide(outcome.anchor.stance)
                        // An arrival on the goal stance still moving may be impossible to
                        // finish any other way (a launch cannot re-cross the gap that got
                        // here; bouncy ground never satisfies the loose stop) -- braking
                        // it to rest is the finish of last resort.
                        if (outcome.anchor.stance == goalStance && best == null) {
                            finishByBraking(outcome.anchor)
                        }
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
                } else if (corridorLevel < CORRIDOR_LEVELS.lastIndex) {
                    spentAnchors += anchor
                }
            }

            best?.let { solution ->

                while (!readyToFinish(solution) &&
                    horizon.commitFromCandidates(urgent = true, along = solution.anchor)
                ) {
                }
                return finish(solution)
            }

            brakedFallback?.let { return finish(it) }

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
            if (cached != null && anchor.actionsHazardFrame == hazard &&
                anchor.actionsEpoch == actionsEpoch
            ) return cached
            return vocabulary.actions(anchor).also {
                anchor.actions = it
                anchor.actionsHazardFrame = hazard
                anchor.actionsEpoch = actionsEpoch
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

        override fun brakeToStop(anchor: ValueAnchor): Solution? =
            brakeWithResting(anchor)?.first

        private fun brakeWithResting(anchor: ValueAnchor): Pair<Solution, MovementSimulationState>? {
            val gated = gate.run(
                anchor.state, listOf(anchor.stance.center(environment)),
                BrakeToStopProgram(anchor.state.rotation.yaw),
                BRAKE_TAIL_FRAMES,
            )
            if (gated.failed) return null
            val rollout = gated.rollout
            // A brake terminal must be a closed physics cycle, not merely slow: a body
            // holding at it repeats the same states forever, so a tape resumed from the
            // terminal replays within tolerance. Rest is period 1 on solid ground and
            // period 2 on bouncy blocks (a standing micro-bounce alternates grounded and
            // airborne frames with frozen position). Goal terminals stay on the looser
            // stable-stop rule — a completed walk is never resumed from.
            var stable = 0
            var stableEnd = -1
            var previous = anchor.state
            var twoBack: MovementSimulationState? = null
            for (frame in rollout.frames) {
                val state = frame.state
                val cycleClosed = twoBack.let {
                    it != null && state.position == it.position &&
                        state.velocity == it.velocity && state.onGround == it.onGround
                }
                stable = if (cycleClosed &&
                    state.velocity.horizontalLength() <= config.stoppedSpeed
                ) stable + 1 else 0
                twoBack = previous
                previous = state
                if (stable >= config.stableStopFrames && state.onGround) {
                    stableEnd = frame.index
                    break
                }
            }
            if (stableEnd < 0) return null
            val frames = rollout.frames.take(stableEnd + 1)

            val restingState = frames.last().state
            val resting = stanceOf(restingState)
            if (!field.isStance(resting) || !field.isMapped(resting)) return null
            return Solution.of(
                anchor, frames,
                TerminalApproach(false, LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
                anchor.collisionEvents + collisionEvents(anchor.state, frames),
            ) to restingState
        }

        // A transition that lands on the goal stance still carries momentum -- the
        // movement program truncates at the landing, and the corridor-following
        // finisher cannot cross the gap edge that got here. Braking the landing to
        // rest IS the finish when the stop stays inside the goal.
        private fun finishByBraking(anchor: ValueAnchor) {
            val incumbent = brakedFallback
            if (incumbent != null && incumbent.frames <= anchor.elapsed) return
            val (solution, resting) = brakeWithResting(anchor) ?: return
            val goal = goalPoint
            val distance = kotlin.math.hypot(
                resting.position.x - goal.x, resting.position.z - goal.z,
            )
            if (distance > config.goalRadius) return
            if (kotlin.math.abs(resting.position.y - goal.y) > GOAL_BRAKE_VERTICAL_TOLERANCE) return
            if (incumbent == null || solution.score < incumbent.score) brakedFallback = solution
        }

        override fun certify(solution: Solution): MotionPlanResult = certifier.certify(
            solution,
            route = route,
            remainingGuideTicks = field.guide(solution.anchor.stance),
            attemptCount = attempts.count,
        )

        private fun retain(solution: Solution) {
            // A solution whose tape diverges under frames the body already pressed can
            // never be adopted.
            if (!horizon.canReach(solution.anchor)) return
            if (!executionCompatible(solution.anchor)) return
            val incumbent = best
            if (incumbent == null || solution.score < incumbent.score) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        private fun mayFinalize(): Boolean = finalGoal == null || goalStance == finalGoal

        private fun executionCompatible(anchor: ValueAnchor): Boolean {
            val executing = cursorFrame?.invoke() ?: return true
            return horizon.executionDivergence(anchor) >= executing
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

    private const val BRAKE_TAIL_FRAMES = 64

    private val CORRIDOR_LEVELS = listOf(
        CorridorLevel(steps = 3, marginTicks = 4.0),
        CorridorLevel(steps = 5, marginTicks = 8.0),
        CorridorLevel(steps = 8, marginTicks = 16.0),
        CorridorLevel(steps = 12, marginTicks = 32.0),
    )

    private const val ESCALATION_EXPANSIONS = 1500

    private const val FORCE_ESCALATION_MIN_EXPANSIONS = 32

    private const val FINAL_IMPROVEMENT_WINDOW = 256

    private const val MAX_TAPE_RESTARTS = 3

    private const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0

    private const val GOAL_BRAKE_VERTICAL_TOLERANCE = 0.05

    private const val RETRY_PENALTY_TICKS = 0.05

}
