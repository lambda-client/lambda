package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.BrakeToStopProgram
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.world.center
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.actions.PursuitTracker
import com.lambda.pathing.search.ValueFieldAnchorSearch.stanceOf
import java.util.concurrent.ExecutorService

private const val CANDIDATE_PUBLISH_INTERVAL = 32

private const val WORLD_SYNC_INTERVAL = 64

/**
 * One run of the anchor-chain beam search: [ValueFieldAnchorSearch.search] constructs a
 * session per request and calls [run]. The loop body is a fixed sequence of phases --
 * cursor sync, publication window, drain recovery, maintenance, batch selection, batch
 * execution -- each a method below; the annealing, tempo and stall-recovery state they
 * share lives in [Annealing], [SearchTempo] and [StallRecovery].
 */
internal class AnchorSearchSession(
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
    private val executor: ExecutorService? = null,
) {
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
        expansionCount = { expansions },
        incumbentAnchor = { best?.anchor },
        brakeToStop = ::brakeToStop,
        certify = ::certify,
        probe = probe,
    )

    init {
        frontier.reachability = ReachabilityPolicy(horizon::canReach)
    }

    private val stats = SearchStats()

    private val tempo = SearchTempo()

    private val recovery = StallRecovery(
        frontier, horizon, worldWait,
        syncWorld = ::syncWorld,
        surcharge = ::remainingSurcharge,
        probe = probe,
    )

    private val annealing = Annealing(
        field, frontier, horizon, recovery, expandGuide,
        maxTemperature = searchConfig.maxTemperature,
    )

    private var finishSweeps = 0
    private var sweepEpoch = 0

    // A goal landing braked to rest: the finish of last resort, never a competitor to the finisher.
    private var brakedFallback: Solution? = null
    private var best: Solution? = null
    private var expansionsSinceImprovement = 0

    private var expansions = 0

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
    private var lastViewMillis = Long.MIN_VALUE
    private var improveBucket = -1

    /**
     * The finished tape already published to the executor, if any. Once set, the session
     * no longer returns at the finalize gate: it refines until the body arrives or the
     * frontier drains, and every exit returns the best finished tape instead of an
     * exhaustion. See docs/decisions/publication-protocol.md.
     */
    private var finalSolution: Solution? = null

    private fun finishedResult(): MotionPlanResult? {
        val published = finalSolution ?: return null
        val out = best?.takeIf { it.score <= published.score } ?: published
        return finish(out)
    }

    /**
     * Publish the finish so the session survives to refine it; returns a result only when
     * it cannot be published (bootstrap, unacked, dead fork) and nothing was sealed before.
     */
    private fun sealFinished(solution: Solution): MotionPlanResult? {
        if (horizon.publishFinished(solution)) {
            finalSolution = solution
            return null
        }
        return if (finalSolution == null) finish(solution) else null
    }

    private fun corridor(): CorridorLevel = CORRIDOR

    private fun revivable(): Boolean = recovery.hasSpent && annealing.canEscalate()

    private fun restartable(): Boolean =
        best == null && finalSolution == null && recovery.restartable()

    /** The DAG repair (see [HorizonController.repairFor]): re-root at the cut and forget what lies beyond it. */
    private fun repairFor(mutations: Set<com.lambda.pathing.core.PathingSection>) {
        val cut = horizon.repairFor(mutations) ?: return
        fun beyond(solution: Solution?) = solution != null &&
            solution.anchor !== cut && solution.anchor.descendsFrom(cut)
        if (beyond(best)) best = null
        if (beyond(finalSolution)) finalSolution = null
        if (beyond(brakedFallback)) brakedFallback = null
        finishSweeps = 0
        sweepEpoch++
        expansionsWindowStart = expansions
        annealing.rewindForRestart(cut.stance)
        horizon.invalidateCommitMemo()
        frontier.updateRoute(routeIndex)
    }

    private fun restartFromTape(): Boolean {
        if (best != null) return false
        val seed = recovery.restartSeed(expansions, stats.adoptableDrops) ?: return false
        finishSweeps = 0
        sweepEpoch++
        expansionsWindowStart = expansions
        annealing.rewindForRestart(seed.stance)
        horizon.reRootForRestart(seed)
        return true
    }

    private fun syncWorld() {
        val result = worldSync?.invoke(route) ?: return
        repairFor(result.mutations)
        when (result) {
            WorldSyncResult.Quiet -> return

            is WorldSyncResult.Woken -> {
                if (frontier.hasBlocked) {
                    sweepEpoch++
                    finishSweeps = 0
                    frontier.wakeBlocked()
                }
            }

            is WorldSyncResult.Changed -> {
                sweepEpoch++
                finishSweeps = 0
                horizon.invalidateCommitMemo()
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
        // A body on a block's edge floors to a cell that is no stance; the coarse route
        // starts from its supporting block, so the root does too.
        val bodyStance = stanceOf(initialState)
        val rootStance = if (field.isMapped(bodyStance)) bodyStance else {
            ValueFieldAnchorSearch.supportedStanceOf(initialState)?.takeIf { field.isMapped(it) } ?: bodyStance
        }
        val root = ValueAnchor(
            state = initialState,
            stance = rootStance,
            elapsed = 0,
            collisionEvents = 0,
            launchMargin = 0,
            inputSwitches = 0,
            parent = null,
            inputs = emptyList(),
            boundary = 0,
        )
        frontier.admit(root)

        annealing.noteGuide(rootStance)
        horizon.begin()
        constructSpine(root)
        while (true) {
            val stop = stopReason()
            if (stop != null) {
                finishedResult()?.let { return it }
                stats.exit = stop
                break
            }
            if (cancelled()) return MotionPlanResult.Cancelled
            cursorFrame?.invoke()?.let { executing ->
                syncWithCursor(executing)?.let { return it }
            }
            refreshWindowOnPublication()
            if (!frontier.hasOpen) {
                when (recoverFromDrain()) {
                    Drain.CONTINUE -> continue
                    Drain.BREAK -> break
                }
            }
            periodicMaintenance()
            val batch = ArrayList<AnchorRollout.PreparedRollout>(parallelism)
            selectBatch(batch)?.let { return it }
            executeBatch(batch)
        }
        return concludeWithIncumbent()
    }

    /** "exhausted" or "budget" when the loop must stop, null while it may go on. */
    private fun stopReason(): String? = when {
        frontier.isExhausted && !frontier.hasBlocked && !revivable() && !restartable() -> "exhausted"
        expansions - expansionsWindowStart >= searchConfig.maxExpansions -> "budget"
        else -> null
    }

    /**
     * Fold one cursor reading into the session: measure tempo, re-root onto what the body
     * has pressed, drop an incumbent execution left behind, return or seal a finish the
     * body is about to arrive at, and keep the tape extended ahead of the runway.
     */
    private fun syncWithCursor(executing: Int): MotionPlanResult? {
        tempo.observe(executing, expansions)
        // Execution is the only irrevocable commitment: re-root onto what the
        // body has pressed (the settled brake anchor once the cursor enters the tail).
        horizon.advanceExecutedRoot()
        annealing.rewindOnNewRoot()
        best?.let {
            if (!horizon.canReach(it.anchor) || !executionCompatible(it.anchor)) {
                best = null
            }
        }
        // A published finish is returned only once the body is nearly there;
        // finalizing mid-course freezes the tape's quality.
        finalSolution?.let { published ->
            val arrivalTape = best?.takeIf { it.score <= published.score } ?: published
            if (executing >= arrivalTape.frames - ARRIVAL_RETURN_FRAMES) {
                return finish(arrivalTape)
            }
        }
        best?.let {
            val tipElapsed = horizon.safeAnchor?.elapsed
            val pressured = tipElapsed != null &&
                tipElapsed - executing <= searchConfig.horizonRunwayFrames
            if (mayFinalize() && readyToFinish(it)) {
                // A sealed finish whose fork is dying under runway pressure is
                // published now, tail and all; neither condition alone suffices.
                // See docs/decisions/publication-protocol.md.
                if (pressured && horizon.finalWindowClosing(it.anchor)) {
                    if (horizon.publishFinished(it)) finalSolution = it
                }
                if ((bodyNearEnd(it) ||
                        (pressured && horizon.finalWindowClosing(it.anchor))) &&
                    expansionsSinceImprovement >= FINAL_IMPROVEMENT_WINDOW
                ) {
                    sealFinished(it)?.let { result -> return result }
                }
            }
        }
        val tip = horizon.safeAnchor ?: return null
        val runway = tip.elapsed - executing
        // Consulted every pass, not only under runway pressure: a branch is
        // publishable only while its fork is ahead of the cursor. Idle passes
        // are paced by the gate's gain floor, the work floor and the memo.
        // See docs/decisions/publication-protocol.md.
        val urgent = runway <= searchConfig.horizonCommitFrames
        val extended = horizon.commitFromCandidates(
            urgent = urgent,
            along = best?.takeIf { !readyToFinish(it) }?.anchor,
        )
        // The body is about to outrun the tape and nothing extends it:
        // finalize now rather than wait out the improvement window.
        if (!extended && urgent) {
            best?.let {
                if (mayFinalize() && readyToFinish(it)) {
                    sealFinished(it)?.let { result -> return result }
                }
            }
        }
        return null
    }

    /** Publication is progress: refresh the per-window budgets, then bootstrap a parked-only frontier. */
    private fun refreshWindowOnPublication() {
        if (horizon.safeAnchor !== lastPublishedTip) {
            lastPublishedTip = horizon.safeAnchor
            recovery.resetWaitWindow()
            expansionsWindowStart = expansions
        }

        if (horizon.safeAnchor == null && frontier.hasParked) horizon.commitFromCandidates(urgent = false)
    }

    private enum class Drain { CONTINUE, BREAK }

    /** The open list is empty: every way of refilling it, in order, before the loop gives up. */
    private fun recoverFromDrain(): Drain {
        // The starved reserve is the escape hatch for a drained open list.
        if (frontier.reviveStarved { horizon.adoptable(it) }) return Drain.CONTINUE

        if (recovery.waitForBlocked()) return Drain.CONTINUE

        // Total exhaustion is stall evidence in itself: escalate and revive the spent anchors.
        if (recovery.hasSpent && annealing.escalate(force = true)) return Drain.CONTINUE

        // Genuinely drained: restart from the published tape's continuation before giving up.
        if (!frontier.hasParked && !frontier.hasBlocked && restartFromTape()) return Drain.CONTINUE

        // A truncated route cannot finalize; wait for it to extend instead of refusing.
        if (!mayFinalize() && recovery.waitForRouteExtension()) return Drain.CONTINUE

        // Anchors parked beyond the local horizon are work, not a dead end.
        if (horizon.extendHorizon()) return Drain.CONTINUE

        val toward = best?.takeIf { !readyToFinish(it) }?.anchor
        if (!frontier.hasParked && toward == null) {
            stats.exit = "drained"
            return Drain.BREAK
        }
        if (!horizon.commitFromCandidates(urgent = true, along = toward)) {
            stats.exit = "commit-refused"
            return Drain.BREAK
        }
        // Publishing does not re-root, so a commit does not refill the open list.
        return Drain.CONTINUE
    }

    private fun periodicMaintenance() {
        if (expansions / WORLD_SYNC_INTERVAL != syncBucket) {
            syncBucket = expansions / WORLD_SYNC_INTERVAL
            syncWorld()
        }
        if (expansions / CANDIDATE_PUBLISH_INTERVAL != publishBucket) {
            publishBucket = expansions / CANDIDATE_PUBLISH_INTERVAL
            horizon.publishCandidates()
            publishSearchView()
        }
    }

    /**
     * Select up to [parallelism] decisions in rank order and prepare their rollouts into
     * [batch]. Returns a result instead when a polled entry proves the incumbent final.
     */
    private fun selectBatch(batch: MutableList<AnchorRollout.PreparedRollout>): MotionPlanResult? {
        while (batch.size < parallelism) {
            val entry = frontier.poll() ?: break
            probe.polled(
                entry.anchor.stance, entry.anchor.elapsed,
                entry.order.toRawBits(), entry.bound.toRawBits(), entry.sequence,
            )
            if (!worthExpanding(entry)) continue
            when (judgeAgainstIncumbent(entry)) {
                Verdict.SKIP -> continue
                Verdict.FINISH -> return finish(best!!)
                Verdict.EXPAND -> Unit
            }

            val anchor = entry.anchor
            sweepFinish(anchor)

            val priced = nextAction(anchor)
            if (priced == null) {
                if (annealing.canEscalate()) recovery.spend(anchor)
                continue
            }
            val action = priced.decision
            countExpansion()
            annealing.escalate(force = false)
            annealing.refine(holdingMotion = best != null || horizon.safeAnchor != null)
            if (expansions / IMPROVEMENT_INTERVAL != improveBucket) {
                improveBucket = expansions / IMPROVEMENT_INTERVAL
                improveIncumbent()
                improvePublished()
            }

            val prepared = rollouts.prepare(anchor, action)
            if (prepared == null) {
                applyRollout(anchor, action, Outcome.Rejected(TrajectoryDiagnostic.NoStop(0, 0.0, anchor.speed)))
                reofferOrSpend(anchor)
                continue
            }
            batch += prepared
        }
        return null
    }

    /** Whether a polled entry is still worth a rollout; drops, starves or parks it otherwise. */
    private fun worthExpanding(entry: Frontier.OpenEntry): Boolean {
        // A branch whose fork the cursor has passed can never be published again.
        val forkLife = horizon.forkLife(entry.anchor)
        if (forkLife < HorizonController.PUBLISH_DIVERGENCE_MARGIN_FRAMES) {
            stats.adoptableDrops++
            return false
        }
        if (!entry.starveExempt && tempo.starved(forkLife, searchConfig.branchExpansionHeadroomExpansions)) {
            stats.forkStarvedDrops++
            frontier.starve(entry)
            return false
        }

        if (entry.anchor.elapsed >= horizon.horizonEnd &&
            field.guide(entry.anchor.stance) > searchConfig.finishValueTicks
        ) {
            frontier.park(entry)
            return false
        }
        return true
    }

    private enum class Verdict { EXPAND, SKIP, FINISH }

    /** An entry against the incumbent: expand it, prune it, or finish because nothing can beat the incumbent. */
    private fun judgeAgainstIncumbent(entry: Frontier.OpenEntry): Verdict {
        val incumbent = best
        val entryScoreBound = entry.bound + Solution.COLLISION_FRAME_PENALTY * entry.anchor.collisionEvents
        if (incumbent != null && mayFinalize() && entryScoreBound >= incumbent.score) {
            if (readyToFinish(incumbent) && bodyNearEnd(incumbent)) return Verdict.FINISH
            // Provably no improvement on the incumbent: the same bound admission prunes by.
            return Verdict.SKIP
        }
        if (incumbent != null && mayFinalize() && readyToFinish(incumbent) && bodyNearEnd(incumbent) &&
            expansionsSinceImprovement >= searchConfig.stallExpansions
        ) {
            return Verdict.FINISH
        }
        return Verdict.EXPAND
    }

    /** One finish sweep from an anchor inside finish range, once per sweep epoch. */
    private fun sweepFinish(anchor: ValueAnchor) {
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
    }

    /** One expansion, however it is spent: the clock and every per-expansion counter tick. */
    private fun countExpansion() {
        expansions++
        clock.onExpansion()
        expansionsSinceImprovement++
        annealing.tick()
    }

    /**
     * Simulate the batch (concurrently when a pool is present) and apply the outcomes in
     * selection order. At parallelism 1 this is exactly the serial search.
     */
    private fun executeBatch(batch: List<AnchorRollout.PreparedRollout>) {
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

    /** The loop stopped without a finish: commit the incumbent as far as it goes, else fall back. */
    private fun concludeWithIncumbent(): MotionPlanResult {
        best?.let { solution ->
            // Each commit must deepen the tape or the loop cannot converge.
            // See docs/decisions/publication-protocol.md.
            var committed = horizon.safeAnchor?.elapsed ?: -1
            while (!readyToFinish(solution) &&
                horizon.commitFromCandidates(urgent = true, along = solution.anchor)
            ) {
                val deepened = horizon.safeAnchor?.elapsed ?: -1
                if (deepened <= committed) break
                committed = deepened
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
     * One decision rolled out and absorbed: admitted on success, learned from on failure.
     * Shared by the main loop and the spine pass so both feed the same bookkeeping.
     */
    private fun rolloutDecision(anchor: ValueAnchor, action: TrajectoryDecision): Outcome =
        applyRollout(anchor, action, rollouts.transition(anchor, action, anchor.hazardFrame))

    private fun reofferOrSpend(anchor: ValueAnchor) {
        val surcharge = remainingSurcharge(anchor)
        if (surcharge != null) {
            anchor.pendingSurcharge = surcharge
            frontier.reoffer(anchor)
        } else if (annealing.canEscalate()) {
            recovery.spend(anchor)
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
                annealing.noteGuide(outcome.anchor.stance)
                // A moving arrival on the goal stance may be unfinishable any other way.
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
                        action.sprint, PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
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
                action.family?.let { family ->

                    if (outcome.diagnostic.frame < divergenceFrame(action)) {
                        anchor.familyPrefixFailures.merge(family, outcome.diagnostic.frame, ::minOf)
                    }
                }
                if (action.seedsHazardOnFailure) {
                    anchor.hazardFrame = launchSeedFrame(outcome.diagnostic)
                        ?.let { frame -> anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
                        ?: anchor.hazardFrame
                }
            }
        }
        return outcome
    }

    /**
     * The constructive pass: descend the route greedily before the search proper, keeping
     * the first of each anchor's best few decisions that certifies, to the goal or the
     * first edge where none do. Its lineage enters the frontier normally, its arrival
     * seeds [best], and every rollout ticks the clock like an expansion. Publication and
     * commitment rules are untouched. See docs/decisions/session-loop.md.
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
                countExpansion()
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
        val epoch = annealing.actionsEpoch
        if (cached != null && anchor.actionsHazardFrame == hazard &&
            anchor.actionsEpoch == epoch
        ) return cached
        return vocabulary.actions(anchor, annealing.temperature).also {
            anchor.actions = it
            anchor.actionsHazardFrame = hazard
            anchor.actionsEpoch = epoch
        }
    }

    // Per-anchor only: a bucket-level "peers skip this action" memo measured net-negative.
    // See docs/decisions/beam.md.
    private fun nextAction(anchor: ValueAnchor): PricedDecision? {
        for (priced in actions(anchor)) {
            val action = priced.decision
            if (action in anchor.attempted) continue
            val family = action.family
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
        ?.let { annealing.temperature.surcharge(it.price) }

    private fun brakeToStop(anchor: ValueAnchor): Solution? =
        brakeWithResting(anchor)?.first

    private fun brakeWithResting(anchor: ValueAnchor): Pair<Solution, MovementSimulationState>? {
        val gated = gate.run(
            anchor.state, listOf(anchor.stance.center(environment)),
            BrakeToStopProgram(anchor.state.rotation.yaw),
            BRAKE_TAIL_FRAMES,
        )
        if (gated.failed) return null
        val rollout = gated.rollout
        // A brake terminal must be a closed physics cycle (period 1 on solid ground,
        // period 2 on bouncy blocks), not merely slow. See docs/decisions/execution-tolerance.md.
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
            TerminalApproach(false, PursuitTracker.DEFAULT_LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
            anchor.collisionEvents + collisionEvents(anchor.state, frames),
        ) to restingState
    }

    // Braking a moving goal landing to rest is the finish when the stop stays inside the goal.
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

    /**
     * Hand the debug channel the live counters and, if something is drawing it, the
     * anchor tree. Wall-clock paced so the sample rate is machine-independent.
     */
    private fun publishSearchView() {
        val now = clock.elapsedMillis()
        if (now - lastViewMillis < VIEW_INTERVAL_MILLIS) return
        lastViewMillis = now

        probe.stats(
            SearchStatsView(
                expansions = expansions,
                windowExpansions = expansions - expansionsWindowStart,
                windowBudget = searchConfig.maxExpansions,
                guideExpansions = annealing.guideExpansions,
                temperature = annealing.temperature.current,
                open = frontier.openSize,
                parked = frontier.parkedSize,
                blocked = frontier.blockedSize,
                spent = recovery.spent.size,
                admitted = frontier.anchorsAdmitted,
                merged = frontier.beamDominated + frontier.beamEvicted + frontier.beamCapped,
                restarts = recovery.tapeRestarts,
                rootFrame = horizon.reachableRoot?.elapsed ?: -1,
                publishedFrame = horizon.publishedTip?.elapsed ?: -1,
                horizonEnd = horizon.horizonEnd,
                cursorFrame = horizon.observedCursor,
                bestScore = best?.score,
                elapsedMillis = now,
            ),
        )

        if (!probe.treeEnabled) return

        val roles = HashMap<ValueAnchor, SearchNodeRole>()
        fun claim(anchor: ValueAnchor, role: SearchNodeRole) {
            val existing = roles[anchor]
            if (existing == null || role < existing) roles[anchor] = role
        }
        fun claimChain(leaf: ValueAnchor?, role: SearchNodeRole) {
            var node = leaf
            while (node != null && roles.size < MAX_TREE_NODES) {
                claim(node, role)
                node = node.parent
            }
        }

        // Leaves first at their own role, then their ancestry as scaffolding, so a
        // branch reads as one line back to where it left the spine.
        recovery.spent.forEach { claim(it, SearchNodeRole.SPENT) }
        frontier.parkedEntries.forEach { claim(it.anchor, SearchNodeRole.PARKED) }
        frontier.openEntries.forEach { claim(it.anchor, SearchNodeRole.OPEN) }
        val leaves = roles.keys.toList()
        leaves.forEach { claimChain(it.parent, SearchNodeRole.INTERIOR) }
        claimChain(best?.anchor, SearchNodeRole.BEST)
        claimChain(horizon.publishedTip, SearchNodeRole.SPINE)

        val nodes = ArrayList<SearchTreeNode>(roles.size)
        val edges = ArrayList<SearchTreeEdge>(roles.size)
        roles.forEach { (anchor, role) ->
            nodes += SearchTreeNode(
                position = anchor.state.position,
                elapsed = anchor.elapsed,
                guide = field.guide(anchor.stance),
                role = role,
            )
            val parent = anchor.parent ?: return@forEach
            if (parent !in roles) return@forEach
            edges += SearchTreeEdge(
                from = parent.state.position,
                to = anchor.state.position,
                role = role,
                via = anchor.via,
            )
        }
        probe.tree(
            SearchTreeView(
                nodes = nodes,
                edges = edges,
                totalAnchors = frontier.anchorsAdmitted,
                truncated = roles.size >= MAX_TREE_NODES,
            ),
        )
    }

    /** Everything worth comparing between a session that dead-ends and one that does not. */
    fun exhaustion(): SearchExhaustion {
        return SearchExhaustion(
            exit = stats.exit,
            expansions = expansions,
            windowExpansions = expansions - expansionsWindowStart,
            windowBudget = searchConfig.maxExpansions,
            guideExpansions = annealing.guideExpansions,
            improvementSplices = stats.improvementSplices,
            improvementRollouts = stats.improvementRollouts,
            improvementSaved = stats.improvementSaved,
            improvementDiagnosis = stats.improvementDiagnosis,
            temperature = annealing.temperature.current,
            openAnchors = frontier.openSize,
            parkedAnchors = frontier.parkedSize,
            blockedAttempts = frontier.blockedSize,
            spentAnchors = recovery.spent.size,
            deepestAnchorFrame = frontier.deepestElapsed,
            unreachableAdmissions = frontier.unreachableAdmissions,
            tapeRestarts = recovery.tapeRestarts,
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
            adoptableDrops = stats.adoptableDrops,
            forkStarvedDrops = stats.forkStarvedDrops,
            commitAttempts = horizon.commitAttempts,
            commitSuppressed = horizon.commitSuppressed,
            publishRefusals = horizon.publishRefusals,
            repairs = horizon.repairs,
            junctionRestarts = horizon.junctionRestarts,
        )
    }

    private fun certify(solution: Solution): MotionPlanResult = certifier.certify(
        solution,
        route = route,
        remainingGuideTicks = field.guide(solution.anchor.stance),
        attemptCount = attempts.count,
    )

    private fun retain(solution: Solution) {
        // A solution diverging under frames the body already pressed can never be adopted.
        if (!horizon.canReach(solution.anchor)) return
        if (!executionCompatible(solution.anchor)) return
        val incumbent = best
        // Off-tape solutions must clear the swap floor the publication gate will hold
        // them to; retaining one that wins by less wedges the session.
        // See docs/decisions/publication-protocol.md.
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
     * Whether the body is within [ValueFieldSearchConfig.finalizeArrivalFrames] of the
     * solution's end. Finalizing ends every chance of improvement, so a mid-course body
     * keeps refining. Without a cursor (planning before motion) this is always true.
     */
    private fun bodyNearEnd(solution: Solution): Boolean {
        val executing = cursorFrame?.invoke() ?: return true
        return solution.frames - executing <= searchConfig.finalizeArrivalFrames
    }

    /** Spend the remaining improvement budget on the solution, then certify it. */
    private fun finish(solution: Solution): MotionPlanResult {
        stats.exit = "solved"
        val best = improve(solution)
        onExhaustion?.invoke(exhaustion())
        return certify(best)
    }

    /** One improver per session, so its budget is spent across the walk. See docs/decisions/improver.md. */
    private val improver by lazy {
        PlanImprover(
            rollouts = rollouts,
            // Always carries skips and gait: every candidate survives recertify and a
            // score comparison before it can touch the plan.
            vocabulary = ActionSet(
                catalog, field, config,
                searchConfig.copy(momentumSkips = true, momentumGait = true),
                corridor = ::corridor,
            ),
            finisher = finisher,
            canReach = horizon::canReach,
        )
    }

    /**
     * Shorten the incumbent in slices while the body is still walking toward it, when
     * most cut points are still ahead of the cursor. See docs/decisions/improver.md.
     */
    private fun improveIncumbent() {
        if (searchConfig.improvementBudget <= 0) return
        val incumbent = best ?: return
        if (improver.rolloutsSpent >= searchConfig.improvementBudget) return
        val slice = minOf(
            searchConfig.improvementBudget,
            improver.rolloutsSpent + IMPROVEMENT_SLICE_ROLLOUTS,
        )
        val improved = improver.improve(incumbent, slice) ?: return
        stats.improvementSaved += incumbent.frames - improved.frames
        retain(improved)
    }

    /**
     * The DAG's quality producer while walking: rewrite a span of the published spine and
     * offer the shorter tip through the ordinary publication gate, which brakes, certifies
     * and applies the swap floor. Runs only while no full solution exists (the incumbent
     * path has its own improver) and within the shared improvement budget.
     */
    private fun improvePublished() {
        if (searchConfig.improvementBudget <= 0 || best != null) return
        val tip = horizon.publishedTip ?: return
        if (improver.rolloutsSpent >= searchConfig.improvementBudget) return
        val slice = minOf(searchConfig.improvementBudget, improver.rolloutsSpent + IMPROVEMENT_SLICE_ROLLOUTS)
        val better = improver.improveTip(tip, slice, minGainFrames = SWAP_FLOOR_GAIN_TICKS.toInt()) ?: return
        stats.improvementRollouts = improver.rolloutsSpent
        stats.improvementDiagnosis = improver.diagnosis()
        frontier.reopen(better)
        horizon.publishPrefix(better, expansions)
        if (horizon.publishedTip === better) {
            stats.improvementSplices++
            stats.improvementSaved += tip.elapsed - better.elapsed
        }
    }

    private fun improve(solution: Solution): Solution {
        if (searchConfig.improvementBudget <= 0) return solution
        val improved = improver.improve(solution, searchConfig.improvementBudget)
        // Recorded even when nothing was found: budget spent for nothing is the interesting case.
        stats.improvementRollouts = improver.rolloutsSpent
        stats.improvementSplices = improver.splices
        stats.improvementDiagnosis = improver.diagnosis()
        if (improved == null) return solution
        // A shorter tape the publisher would refuse is not an improvement.
        if (!horizon.canReach(improved.anchor)) return solution
        stats.improvementSaved = solution.frames - improved.frames
        return improved
    }

}

/** The first frame at which this decision's rollout can differ from its family's: its launch. */
private fun divergenceFrame(action: TrajectoryDecision): Int = action.launchDelayFrames ?: Int.MAX_VALUE

private const val BRAKE_TAIL_FRAMES = 64

// Fixed corridor: the detour excess is priced in the frontier order rather than filtered,
// and escalation buys heat and guide reach, never width. See docs/decisions/annealing.md.
private val CORRIDOR = CorridorLevel(steps = 3, marginTicks = 4.0)

/** Wall-clock spacing between debug-channel samples of the live search. */
private const val VIEW_INTERVAL_MILLIS = 100L

/** Anchors drawn before the tree sample gives up; the search routinely holds far more. */
private const val MAX_TREE_NODES = 4096

/** Expansions between improvement slices, and how many rollouts each may spend. */
private const val IMPROVEMENT_INTERVAL = 2000
private const val IMPROVEMENT_SLICE_ROLLOUTS = 250

private const val FINAL_IMPROVEMENT_WINDOW = 256

/** Frames before the finished tape's end at which the surviving session returns it. */
private const val ARRIVAL_RETURN_FRAMES = 2

/** Frames an off-tape solution must win by: the publication gate's swap floor. */
private const val OFF_TAPE_RETAIN_GAIN_FRAMES = SWAP_FLOOR_GAIN_TICKS.toInt()

/** Decisions the spine pass rolls per edge; must cover the walk probes that seed the hazard frame. */
private const val SPINE_ATTEMPTS_PER_EDGE = 8

private const val GOAL_BRAKE_VERTICAL_TOLERANCE = 0.05
