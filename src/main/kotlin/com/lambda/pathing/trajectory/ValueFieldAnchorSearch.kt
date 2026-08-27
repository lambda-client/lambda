package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.BrakeToStopProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.PricedDecision
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.world.center
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment

data class ValueFieldSearchConfig(
    val maxExpansions: Int = 8000,
    val stallExpansions: Int = 3000,
    val maxTransitionFrames: Int = 40,
    val branchMarginTicks: Double = 4.0,
    val headingFanDegrees: List<Double> = listOf(0.0, -12.0, 12.0),
    val headingCommitFrames: Int = 12,
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
    val speedBucketBlocks: Double = 0.075,
    val yawBucketDegrees: Double = 20.0,
) {
    init {
        require(maxExpansions > 0)
        require(stallExpansions > 0)
        require(maxTransitionFrames > 0)
        require(chainLength > 0)
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

        /**
         * Where the search reports what it had spent and unlocked when it stopped.
         *
         * A callback rather than a log line: this runs inside plain unit tests, and the
         * mod's logger cannot static-initialise outside a Minecraft runtime.
         */
        onExhaustion: ((SearchExhaustion) -> Unit)? = null,

        /**
         * Rollouts per expansion batch. At 1 the search is the exact serial search; above
         * it, up to this many frontier decisions are selected in rank order, simulated
         * concurrently on [executor], and their outcomes applied in the same order --
         * deterministic for a fixed setting, though a different search than serial
         * (batch members cannot see each other's failures until the batch lands).
         */
        parallelism: Int = 1,
        executor: java.util.concurrent.ExecutorService? = null,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val unsupported = route.edges.mapTo(HashSet()) { it.movement }
            .filterTo(HashSet()) { !catalog.supports(it) }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return Search(
            route, catalog, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
            expandGuide, adoptedSequence, finalGoal, probe, onExhaustion,
            parallelism = if (executor != null) parallelism.coerceAtLeast(1) else 1,
            executor = executor,
        ).run()
    }

    private const val CANDIDATE_PUBLISH_INTERVAL = 32

    private const val BLOCKED_WAIT_SLICE_MILLIS = 200L
    private const val MAX_BLOCKED_WAIT_MILLIS = 4_000L

    private const val WORLD_SYNC_INTERVAL = 64

    private const val MAX_FRUITLESS_WAKES = 2

    internal fun stanceOf(state: MovementSimulationState): Stance =
        Stance.of(state.position, state.onGround)

    /**
     * Where a grounded state is actually standing, when flooring its centre names a
     * cell that is not a stance at all: the body caught a lone pad with its edge and
     * hangs its centre over the air beside it. On the diagonal zig-zag fixture every
     * corner catch of the far pad was rejected as FellBelowRoute this way. A FALLBACK
     * only -- a landing whose floored cell is a real stance keeps its name, so clean
     * centred landings and sloppy corner catches stay distinguishable and the drop
     * staircase's hold-the-line quality gate keeps meaning something.
     */
    internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
        if (!state.onGround) return null
        val support = state.supportingBlockPos ?: return null
        val base = Stance.of(state.position, true)
        if (support.x == base.x && support.z == base.z) return null
        return Stance(support.x, support.y + 1, support.z)
    }

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
        private val onExhaustion: ((SearchExhaustion) -> Unit)?,
        private val parallelism: Int = 1,
        private val executor: java.util.concurrent.ExecutorService? = null,
    ) : CommitSupport {
        private val vocabulary = ActionSet(catalog, field, config, searchConfig) { corridor() }

        private var goalStance = route.goal
        private var goalPoint = goalStance.center(environment)
        private val attempts = AttemptAccumulator()

        private var routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

        private val frontier: Frontier = Frontier(
            field, config, searchConfig, routeIndex,
            incumbentScore = { best?.score },
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

        // How much movement difficulty the search is currently buying. Stalls heat it,
        // guide progress cools it: easy ground is searched at easy prices, and only the
        // terrain that actually needs a tight landing pays for one.
        private val temperature = Temperature()
        private var progressBaseline = Double.POSITIVE_INFINITY
        private var bestGuideSeen = Double.POSITIVE_INFINITY
        private var expansionsSinceGuideProgress = 0
        private var expansionsSinceHeat = 0
        private var escalationRoot: ValueAnchor? = null

        // Why the loop stopped. Set at each exit so a dead-end says which of the four
        // ways out it took, rather than only that it took one.
        private var exit = "budget"

        // Anchors whose corridor-level vocabulary is exhausted; revived when it widens.
        private val spentAnchors = ArrayList<ValueAnchor>()

        private var tapeRestarts = 0

        // Tape frames already restarted from while still moving. A second restart at the
        // same tip would only replay the drain, so that one concedes to the brake.
        private val restartedMoving = HashSet<Int>()

        // A launch landing braked to rest inside the goal: certified only when nothing
        // better finishes -- a last resort, never a competitor to the finisher.
        private var brakedFallback: Solution? = null
        private var blockedWaitMillis = 0L
        private var fruitlessWakes = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0

        private var expansions = 0

        override val expansionCount: Int get() = expansions

        override val incumbentAnchor: ValueAnchor? get() = best?.anchor

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
        private var syncBucket = -1
        private var publishBucket = -1

        private fun corridor(): CorridorLevel = CORRIDOR_LEVELS[corridorLevel]

        private fun revivable(): Boolean = spentAnchors.isNotEmpty() && canEscalate()

        private fun restartable(): Boolean =
            best == null && tapeRestarts < MAX_TAPE_RESTARTS &&
                horizon.latestBrakeContinuation() != null

        private fun restartFromTape(): Boolean {
            if (best != null || tapeRestarts >= MAX_TAPE_RESTARTS) return false
            // Extend before conceding. Restarting onto the resting brake commits the body
            // to halting -- every later anchor must descend from it, so the tape ends up
            // containing the deceleration whether or not it was ever needed. Take the
            // moving tip first and keep the brake for the case where that drains too.
            val seed = horizon.movingTipContinuation()?.takeIf { restartedMoving.add(it.elapsed) }
                ?: horizon.latestBrakeContinuation()
                ?: return false
            tapeRestarts++
            corridorLevel = 0
            temperature.cool()
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
                if (temperature.cool()) actionsEpoch++
                if (corridorLevel != 0) {
                    corridorLevel = 0
                    actionsEpoch++
                }
            }
        }

        /**
         * Buy the search more room after a stall: heat first, then width.
         *
         * Heating unlocks harder movements where the body already stands. Widening admits
         * coarse steps further off the best line, which is a detour the body then walks.
         * The first is the answer far more often -- the gap in front of it usually wants a
         * tighter jump, not a way around -- and it is the cheaper thing to be wrong about,
         * so the corridor only widens once nothing is left to unlock.
         */
        private fun escalate(force: Boolean): Boolean {
            spentAnchors.retainAll { horizon.canReach(it) }
            // Exhaustion alone is not stall evidence: a healthy streaming walk exhausts
            // its committed subtree all the time. Escalate only when the guide has not
            // moved either.
            if (!force && frontier.hasBlocked) return false

            // Heat and width keep separate evidence, and buy on different terms.
            //
            // A drained frontier is reason enough to heat without the usual expansion
            // quota -- an anchor whose whole cold vocabulary has failed has told us all it
            // can, and making it spend expansions it no longer has is how a lip that only
            // a run-up clears goes uncertified. Requiring even a small quota here was
            // tried and put two standing-start fixtures back into failure: on a course
            // small enough to have one viable answer the anchor exhausts in under a dozen
            // expansions, and the answer is behind the ladder. What keeps this from
            // ratcheting the whole walk is not a quota but rewindEscalationOnNewRoot.
            if (force || expansionsSinceHeat >= HEAT_EXPANSIONS) {
                expansionsSinceHeat = 0
                if (temperature.raise()) {
                    actionsEpoch++
                    revive()
                    return true
                }
            }

            // Widening admits a detour the body then walks, so it keeps the full stall
            // evidence. Sharing a counter with heat meant every heat step reset the stall
            // the corridor was accruing, and a route that genuinely had to flow around an
            // infeasible edge had to re-earn the whole quota four times over.
            //
            // The quota is waived when the frontier is dry: the counter resets on each
            // widening, and a world small enough that the next level's whole vocabulary
            // exhausts in under the quota can then never widen again -- a lone-pad
            // zig-zag drained at corridor 1 with levels 2 and 3 forever unspent. With
            // nothing open and nothing parked there are no expansions left to demand.
            val dry = !frontier.hasOpen && !frontier.hasParked
            if (force && !dry && expansionsSinceGuideProgress < FORCE_ESCALATION_MIN_EXPANSIONS) return false
            if (!force && expansionsSinceGuideProgress < ESCALATION_EXPANSIONS) return false
            if (corridorLevel >= CORRIDOR_LEVELS.lastIndex) return false
            corridorLevel++
            actionsEpoch++
            expansionsSinceGuideProgress = 0
            expandGuide?.invoke(CORRIDOR_LEVELS[corridorLevel].guideExpansionTicks)
            field.clearGuideCache()
            frontier.rescore()
            revive()
            return true
        }

        /**
         * With motion in hand and budget to spare, buy difficulty to shorten it.
         *
         * A cold search reaches the goal on plain walking and wide-margin jumps -- fast,
         * and longer than it needs to be, because the movements that cut a corner are
         * exactly the ones a cold vocabulary withholds. Reaching is not the end of the
         * search though: the horizon keeps expanding until it has to commit, and every
         * expansion after an incumbent exists is budget that can only buy a shorter tape.
         * So once nothing has improved for a while, unlock the next tier and let the
         * shortcuts compete. This is the half of the annealing that makes the tape get
         * shorter the longer the search runs, rather than merely arrive sooner.
         */
        private fun refine() {
            if (best == null && horizon.safeAnchor == null) return
            if (expansionsSinceHeat < REFINEMENT_HEAT_EXPANSIONS) return
            expansionsSinceHeat = 0
            if (temperature.raise()) actionsEpoch++
        }

        /**
         * Each committed root starts a fresh window, searched cold.
         *
         * Escalation state was global and one-way. `bestGuideSeen` is a monotone minimum
         * over the whole session, so once the frontier had reached deep once, no later
         * expansion could ever register as progress -- and progress is the only thing that
         * de-escalates. A walk therefore ratcheted to the widest corridor and the hottest
         * vocabulary and stayed there, which is what a production run showed: corridor 3
         * from early on, and 229 frames of motion on a route worth sixty.
         *
         * Committing a root means the terrain behind it is settled and the terrain ahead
         * is new, so the evidence for widening should be re-earned there rather than
         * inherited. Temperature steps down here rather than resetting, because movement
         * difficulty arrives in runs -- a parkour stretch is a dozen gaps, not one.
         *
         * The corpus cannot adjudicate this: it never escalates past corridor 1, so the
         * variant comparison there is noise on an axis this does not touch. The argument
         * is mechanical instead. Escalation was a one-way budget for a whole session, so
         * spending it on an early stall left nothing for the genuine obstacle later -- a
         * production session died at route index 14 holding `corridor=3 temp=1.00
         * restarts=3`, having already spent everything it had to spend.
         */
        private fun rewindEscalationOnNewRoot() {
            val root = horizon.reachableRoot ?: return
            if (root === escalationRoot) return
            escalationRoot = root
            progressBaseline = Double.POSITIVE_INFINITY
            bestGuideSeen = Double.POSITIVE_INFINITY
            expansionsSinceGuideProgress = 0
            expansionsSinceHeat = 0
            if (temperature.cool()) actionsEpoch++
            if (corridorLevel != 0) {
                corridorLevel = 0
                actionsEpoch++
            }
            noteGuide(root.stance)
        }

        /**
         * Whether further searching in this window can be expected to buy anything.
         *
         * The search had no termination criterion during a walk at all. `stallExpansions`
         * only applies once a full solution to the final goal is in hand, which during a
         * receding-horizon walk is almost never, so between publications the search simply
         * expanded as fast as the machine allowed until the body needed something. A
         * twelve-second parkour leg was measured spending 145,000 expansions to settle on
         * a tape it had already found -- 205, 209, 209 and 207 frames across runs costing
         * 142k to 162k -- while producing thirty frames of tape a second against the twenty
         * the body consumes.
         *
         * So this stops expanding once the body is comfortably ahead and the window has
         * already had a fair search. It deliberately does not stop when the runway is
         * short: being ahead is the whole justification, and a body running out of tape
         * needs every expansion it can get.
         */
        /** Whether a stall still has something left to buy. */
        private fun canEscalate(): Boolean =
            !temperature.exhausted || corridorLevel < CORRIDOR_LEVELS.lastIndex

        /**
         * Return the anchors that ran out of vocabulary to the queue.
         *
         * Their price has to be recomputed, not carried over: the escalation that revived
         * them is exactly what put new movements within reach, and those are the ones the
         * anchor will now be ranked by.
         */
        private fun revive() {
            spentAnchors.forEach { anchor ->
                remainingSurcharge(anchor)?.let { anchor.pendingSurcharge = it }
                frontier.reopen(anchor)
            }
            spentAnchors.clear()
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
            val root = ValueAnchor(
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
            frontier.admit(root)

            noteGuide(stanceOf(initialState))
            horizon.begin()
            constructSpine(root)
            while (true) {
                if (frontier.isExhausted && !frontier.hasBlocked && !revivable() && !restartable()) {
                    exit = "exhausted"
                    break
                }
                if (expansions - expansionsWindowStart >= searchConfig.maxExpansions) {
                    exit = "budget"
                    break
                }
                if (cancelled()) return MotionPlanResult.Cancelled
                val executing = cursorFrame?.invoke()
                if (executing != null) {
                    // Execution is the only irrevocable commitment: re-root onto what
                    // the body has actually pressed. On catch-up (the cursor entered
                    // the published brake tail) the search continues from the settled
                    // brake anchor -- same session, same frontier.
                    horizon.advanceExecutedRoot()
                    rewindEscalationOnNewRoot()
                    best?.let {
                        if (!horizon.canReach(it.anchor) || !executionCompatible(it.anchor)) {
                            best = null
                        }
                    }
                    // A surviving full solution to the final goal finalizes after a
                    // short improvement window -- but only once the body is nearly
                    // there. Finalizing mid-course froze the tape's quality with the
                    // body seconds behind it.
                    best?.let {
                        if (mayFinalize() && readyToFinish(it) && bodyNearEnd(it) &&
                            expansionsSinceImprovement >= FINAL_IMPROVEMENT_WINDOW
                        ) {
                            return finish(it)
                        }
                    }
                    val tip = horizon.safeAnchor
                    if (tip != null) {
                        val runway = tip.elapsed - executing
                        if (runway <= searchConfig.horizonRunwayFrames) {
                            val urgent = runway <= searchConfig.horizonCommitFrames
                            val extended = horizon.commitFromCandidates(
                                urgent = urgent,
                                along = best?.takeIf { !readyToFinish(it) }?.anchor,
                            )
                            // The body is about to outrun the tape and no extension
                            // exists -- what remains of the running solution is its
                            // tail. Finalize now rather than waiting out the usual
                            // improvement window: waiting was measured as the body
                            // braking at the tape end while a finished solution sat
                            // unadopted behind the window.
                            if (!extended && urgent) {
                                best?.let {
                                    if (mayFinalize() && readyToFinish(it)) return finish(it)
                                }
                            }
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
                    if (spentAnchors.isNotEmpty() && escalate(force = true)) continue

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
                    // Anchors waiting beyond the local horizon are work, not a dead end.
                    // Spend them before insisting on a commitment the search does not want
                    // to make.
                    if (horizon.extendHorizon()) continue

                    val toward = best?.takeIf { !readyToFinish(it) }?.anchor
                    if (!frontier.hasParked && toward == null) {
                        exit = "drained"
                        break
                    }
                    if (!horizon.commitFromCandidates(urgent = true, along = toward)) {
                        exit = "commit-refused"
                        break
                    }
                    // Publishing no longer re-roots, so a successful commit does not
                    // refill the open list; re-evaluate instead of polling empty.
                    continue
                }
                if (expansions / WORLD_SYNC_INTERVAL != syncBucket) {
                    syncBucket = expansions / WORLD_SYNC_INTERVAL
                    syncWorld()
                }
                if (expansions / CANDIDATE_PUBLISH_INTERVAL != publishBucket) {
                    publishBucket = expansions / CANDIDATE_PUBLISH_INTERVAL
                    horizon.publishCandidates()
                }

                // Select up to [parallelism] decisions in rank order, simulate them
                // (concurrently when a pool is present), and apply their outcomes in the
                // same order. At parallelism 1 this is exactly the serial search.
                val batch = ArrayList<AnchorRollout.PreparedRollout>(parallelism)
                var finishInstead: MotionPlanResult? = null
                while (batch.size < parallelism && finishInstead == null) {
                    val entry = frontier.poll() ?: break

                    // Execution kills branches continuously: a branch whose divergence
                    // point the cursor has passed can never be published again, and
                    // keeping it live let a better-but-dead line dominate the frontier
                    // while the tape starved.
                    if (!horizon.adoptable(entry.anchor)) continue

                    if (entry.anchor.elapsed >= horizon.horizonEnd &&
                        field.guide(entry.anchor.stance) > searchConfig.finishValueTicks
                    ) {
                        frontier.park(entry)
                        continue
                    }

                    val incumbent = best
                    val entryScoreBound = entry.bound + COLLISION_FRAME_PENALTY * entry.anchor.collisionEvents
                    if (incumbent != null && mayFinalize() && entryScoreBound >= incumbent.score) {
                        if (readyToFinish(incumbent) && bodyNearEnd(incumbent)) {
                            finishInstead = finish(incumbent)
                            break
                        }
                        // The session must stay alive for refinement, but this anchor is
                        // provably no improvement -- the same test admission prunes by.
                        continue
                    }
                    if (best != null && mayFinalize() && readyToFinish(best!!) && bodyNearEnd(best!!) &&
                        expansionsSinceImprovement >= searchConfig.stallExpansions
                    ) {
                        finishInstead = finish(best!!)
                        break
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

                    val priced = nextAction(anchor)
                    if (priced == null) {
                        if (canEscalate()) spentAnchors += anchor
                        continue
                    }
                    val action = priced.decision
                    expansions++
                    clock.onExpansion()
                    expansionsSinceImprovement++
                    expansionsSinceGuideProgress++
                    expansionsSinceHeat++
                    escalate(force = false)
                    refine()

                    val prepared = rollouts.prepare(anchor, action)
                    if (prepared == null) {
                        applyRollout(anchor, action, Outcome.Rejected(TrajectoryDiagnostic.NoStop(0, 0.0, anchor.speed)))
                        reofferOrSpend(anchor)
                        continue
                    }
                    batch += prepared
                }

                finishInstead?.let { return it }

                if (batch.size > 1 && executor != null) {
                    batch.map { prepared -> executor.submit { rollouts.execute(prepared) } }
                        .forEach { it.get() }
                } else {
                    batch.forEach { rollouts.execute(it) }
                }
                for (prepared in batch) {
                    val outcome = rollouts.complete(prepared, prepared.anchor.hazardFrame)
                    applyRollout(prepared.anchor, prepared.action, outcome)
                    reofferOrSpend(prepared.anchor)
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
                exhaustion = exhaustion().also { onExhaustion?.invoke(it) },
            )
        }

        /**
         * One decision rolled out and fully absorbed: admitted on success, learned from
         * on failure. Shared by the main loop and the spine pass so both feed the same
         * family-prefix and hazard bookkeeping -- a failure teaches the search the same
         * lesson no matter which loop paid for it.
         */
        private fun rolloutDecision(anchor: ValueAnchor, action: TrajectoryDecision): Outcome =
            applyRollout(anchor, action, rollouts.transition(anchor, action, anchor.hazardFrame))

        private fun reofferOrSpend(anchor: ValueAnchor) {
            val surcharge = remainingSurcharge(anchor)
            if (surcharge != null) {
                anchor.pendingSurcharge = surcharge
                frontier.reoffer(anchor)
            } else if (canEscalate()) {
                spentAnchors += anchor
            }
        }

        private fun applyRollout(anchor: ValueAnchor, action: TrajectoryDecision, raw: Outcome): Outcome {
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
            probe.expansion(anchor.stance, action, (outcome as? Outcome.Rejected)?.diagnostic)
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
            return outcome
        }

        /**
         * The constructive pass: descend the route greedily before the search proper
         * starts, taking each anchor's best few priced decisions in order and keeping the
         * first that certifies, to the goal or to the first edge where none do.
         *
         * The point is decoupling *knowing* a full-course solution from *committing* to
         * one. The best-first loop rations depth through the publication horizon, so
         * before this pass existed a session could not hold a certified line to the goal
         * until commits had walked the horizon there -- and a mid-course dead end cost
         * 160x what a fresh session paid for the same stance. The spine's lineage is
         * admitted to the frontier normally (deep anchors park and become the commitment
         * candidates), its arrival seeds [best] so incumbent pruning and stall
         * finalization govern from the first real expansion, and its failures leave the
         * same family-prefix evidence an expansion would. Publication and commitment
         * rules are untouched: the pass buys knowledge, never skips a work floor.
         *
         * Cost accounting is honest -- every rollout ticks the clock exactly like a
         * main-loop expansion, so the search-versus-body race sees the spine's price.
         */
        private fun constructSpine(root: ValueAnchor) {
            var current = root
            var spent = 0
            val budget = (route.nodes.size + 4) * SPINE_ATTEMPTS_PER_EDGE * 2
            var stalled: TrajectoryDiagnostic? = null
            while (spent < budget && !cancelled()) {
                val remaining = field.guide(current.stance)
                if (!remaining.isFinite()) break
                if (remaining <= searchConfig.finishValueTicks) {
                    if (current.sweptEpoch != sweepEpoch && finishSweeps < searchConfig.maxFinishSweeps) {
                        current.sweptEpoch = sweepEpoch
                        finishSweeps++
                        finisher.finishFrom(current)?.let { retain(it) }
                    }
                    break
                }
                var advanced: ValueAnchor? = null
                var attemptsHere = 0
                var blocked = false
                while (attemptsHere < SPINE_ATTEMPTS_PER_EDGE) {
                    val priced = nextAction(current) ?: break
                    attemptsHere++
                    spent++
                    expansions++
                    clock.onExpansion()
                    expansionsSinceImprovement++
                    expansionsSinceGuideProgress++
                    expansionsSinceHeat++
                    when (val outcome = rolloutDecision(current, priced.decision)) {
                        is Outcome.Anchored -> advanced = outcome.anchor
                        is Outcome.Blocked -> blocked = true
                        is Outcome.Rejected -> stalled = outcome.diagnostic
                        is Outcome.Arrived -> Unit
                    }
                    if (advanced != null || blocked) break
                }
                // Streaming holes are the main loop's wait ladder to resolve, not ours.
                if (blocked) break
                current = advanced ?: break
                stalled = null
            }
            probe.spine(
                reachedElapsed = current.elapsed,
                rollouts = spent,
                stalledAt = if (best == null) current.stance else null,
                diagnostic = stalled,
            )
        }

        private fun actions(anchor: ValueAnchor): List<PricedDecision> {
            val hazard = anchor.hazardFrame
            val cached = anchor.actions
            if (cached != null && anchor.actionsHazardFrame == hazard &&
                anchor.actionsEpoch == actionsEpoch
            ) return cached
            return vocabulary.actions(anchor, temperature).also {
                anchor.actions = it
                anchor.actionsHazardFrame = hazard
                anchor.actionsEpoch = actionsEpoch
            }
        }

        private fun nextAction(anchor: ValueAnchor): PricedDecision? {
            for (priced in actions(anchor)) {
                val action = priced.decision
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
                return priced
            }
            return null
        }

        /**
         * The price of the cheapest movement [anchor] has left, or null when it has none.
         *
         * The list is already sorted by price, but a family prefix failure can retire an
         * entry out of order, so this scans rather than reading the head.
         */
        private fun remainingSurcharge(anchor: ValueAnchor): Double? = actions(anchor)
            .firstOrNull { it.decision !in anchor.attempted }
            ?.let { temperature.surcharge(it.price) }

        private fun hasUnattemptedAction(anchor: ValueAnchor): Boolean =
            actions(anchor).any { it.decision !in anchor.attempted }

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

        /** Everything worth comparing between a session that dead-ends and one that does not. */
        fun exhaustion(): SearchExhaustion = SearchExhaustion(
            exit = exit,
            expansions = expansions,
            windowExpansions = expansions - expansionsWindowStart,
            windowBudget = searchConfig.maxExpansions,
            corridorLevel = corridorLevel,
            temperature = temperature.current,
            openAnchors = frontier.openSize,
            parkedAnchors = frontier.parkedSize,
            blockedAttempts = frontier.blockedSize,
            spentAnchors = spentAnchors.size,
            deepestAnchorFrame = frontier.deepestElapsed,
            unreachableAdmissions = frontier.unreachableAdmissions,
            tapeRestarts = tapeRestarts,
            routeNodes = route.nodes.size,
            deepestRouteIndex = frontier.deepestProgress,
            committed = horizon.committed,
            rootFrame = horizon.reachableRoot?.elapsed ?: -1,
            publishedFrame = horizon.publishedTip?.elapsed ?: -1,
            horizonEnd = horizon.horizonEnd,
            anchorsAdmitted = frontier.anchorsAdmitted,
            beamDominated = frontier.beamDominated,
            beamEvicted = frontier.beamEvicted,
            beamCapped = frontier.beamCapped,
            beamBuckets = frontier.beamBuckets,
            beamLargestBucket = frontier.beamLargestBucket,
        )

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
            // Better must mean adoptably better. The publication protocol refuses a
            // branch swap that gains less than REFINEMENT_GAIN_TICKS, so a solution off
            // the published tape that wins by a tick is a trap: the search follows it,
            // nothing can publish it, the tape starves, and the body brakes at the tip
            // -- measured on bedrock-00 as every stall's line refusing with over==line.
            // Off-tape solutions must clear the same bar the protocol will hold them to.
            val tip = horizon.safeAnchor
            val requiredGain = if (tip != null && !solution.anchor.descendsFrom(tip)) {
                OFF_TAPE_RETAIN_GAIN_FRAMES
            } else 0
            val improves = when {
                incumbent == null -> true
                requiredGain > 0 -> solution.score <= incumbent.score - requiredGain
                else -> solution.score < incumbent.score
            }
            if (improves) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        private fun mayFinalize(): Boolean = finalGoal == null || goalStance == finalGoal

        private fun executionCompatible(anchor: ValueAnchor): Boolean = horizon.adoptable(anchor)

        private fun readyToFinish(solution: Solution): Boolean {
            if (searchConfig.maxFinalCommitFrames <= 0) return true
            val committedElapsed = horizon.safeAnchor?.elapsed ?: return true
            return solution.frames - committedElapsed <= searchConfig.maxFinalCommitFrames
        }

        /**
         * Whether the body is close enough to the solution's end that finalizing is the
         * right move. Finishing certifies the whole remaining tape and ends the session
         * -- and with it every chance of improvement -- so while the body is mid-course
         * the search declines to finalize and keeps refining just in time instead. A
         * session with no cursor (planning before motion, or a parked successor) keeps
         * the historical behaviour: short courses finalize whole.
         */
        private fun bodyNearEnd(solution: Solution): Boolean {
            val executing = cursorFrame?.invoke() ?: return true
            return solution.frames - executing <= searchConfig.maxFinalCommitFrames
        }

        private fun finish(solution: Solution): MotionPlanResult {
            exit = "solved"
            onExhaustion?.invoke(exhaustion())
            return certify(solution)
        }

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

    // Escalation widens BREADTH; the admissible detour stays narrow.
    //
    // The margin used to reach 32 ticks, which at sprint speed is nine blocks -- wide
    // enough to propose successors pointing away from the goal, and sticky, because
    // nothing de-escalates the corridor without a fresh global best guide. A production
    // parkour run finished at corridor=3 having spent 145k expansions and 229 frames of
    // motion on a route worth about sixty.
    //
    // Capping it was tried once before the detour price existed and cost a corpus route:
    // the width was doing double duty as the only escape hatch onto terrain the coarse
    // graph prices well above optimal. Now that ActionSet charges a step's excess in the
    // frontier order, the escape hatch is the price rather than the filter, and the same
    // cap keeps every route. The corpus never escalates past level 1, so it can only show
    // that this costs nothing there -- the case it is for is the one above.
    private val CORRIDOR_LEVELS = listOf(
        CorridorLevel(steps = 3, marginTicks = 4.0, guideExpansionTicks = 4.0),
        CorridorLevel(steps = 5, marginTicks = 6.0, guideExpansionTicks = 8.0),
        CorridorLevel(steps = 8, marginTicks = 8.0, guideExpansionTicks = 16.0),
        CorridorLevel(steps = 12, marginTicks = 8.0, guideExpansionTicks = 32.0),
    )

    private const val ESCALATION_EXPANSIONS = 1500

    /**
     * Expansions of flat guide that buy the next tier of movement difficulty.
     *
     * Far below [ESCALATION_EXPANSIONS], which widens the corridor. Heat is local and
     * cheap to be wrong about -- the worst case is a few rollouts of a jump that was
     * never needed -- while width admits a detour the body then walks, so the two do not
     * deserve the same evidence.
     */
    private const val HEAT_EXPANSIONS = 120

    // The idle rule (stop expanding at 40+ frames of runway) is deleted, deliberately:
    // it was the right economy when the search had no full solution mid-walk and its
    // extra expansions bought nothing. With an incumbent standing from the spine,
    // divergence pruning killing dead branches, and finalization waiting for the body,
    // spare capacity is refinement of the tape the body has not yet reached.

    /** Expansions without improvement, holding motion, before difficulty is bought to shorten it. */
    private const val REFINEMENT_HEAT_EXPANSIONS = 600

    private const val FORCE_ESCALATION_MIN_EXPANSIONS = 32

    private const val FINAL_IMPROVEMENT_WINDOW = 256

    private const val MAX_TAPE_RESTARTS = 3

    /**
     * Frames an off-tape solution must win by to displace the incumbent, mirroring the
     * publication protocol's REFINEMENT_GAIN_TICKS: retaining anything the protocol
     * will refuse to publish wedges the session between an unadoptable best and a
     * starving tape.
     */
    private const val OFF_TAPE_RETAIN_GAIN_FRAMES = 3

    /**
     * Ordered decisions the spine pass rolls per edge before conceding the edge to the
     * search proper. This must cover the learn-then-launch sequence, not just the launch
     * candidates: on a gap edge the cheap walk probes order first, fail in a frame or
     * two, and their failures seed the hazard frame that unlocks the delayed launch
     * vocabulary -- four attempts died inside the walk probes on every pad course.
     */
    private const val SPINE_ATTEMPTS_PER_EDGE = 8

    private const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0

    private const val GOAL_BRAKE_VERTICAL_TOLERANCE = 0.05

}
