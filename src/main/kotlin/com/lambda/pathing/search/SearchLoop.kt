package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class SearchLoop(
	initialRoute: CoarseRoutePlan,
	catalog: MovementCatalog,
	private val field: ValueField,
	private val initialState: MovementSimulationState,
	profile: PlayerPhysicsProfile,
	environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints = MotionConstraints(),
	private val searchConfig: SearchConfig = SearchConfig(),
	onSafePrefix: ((MotionPlanResult.Success) -> Unit)? = null,
	private val execution: ExecutionFeedback = ExecutionFeedback.DETACHED,
	private val clock: SearchClock = SystemSearchClock(),
	private val cancelled: () -> Boolean = { false },
	private val world: WorldLink? = null,
	private val finalGoal: Stance? = null,
	private val probe: SearchProbe = SearchProbe.NONE,
	private val onExhaustion: ((SearchStats) -> Unit)? = null,
	parallelism: Int = 1,
	private val legs: LegSource? = null,
) {
	private val parallelism = parallelism.coerceAtLeast(1)
	private val pool: ExecutorService? = if (parallelism > 1) {
		Executors.newFixedThreadPool(parallelism) { runnable -> Thread(runnable, "PathPlanner-rollout").apply { isDaemon = true } }
	} else null
	private val unsupported = initialRoute.edges.mapTo(HashSet()) { it.movement }.filterTo(HashSet()) { !catalog.supports(it) }
	private val vocabulary: ActionSet = ActionSet(catalog, field, config)
	private val route: RouteState = RouteState(initialRoute, environment)
	private val attempts: AttemptAccumulator = AttemptAccumulator()
	private val legTouches = ArrayList<LegTouch>()

	private val frontier: Frontier = Frontier(field, config, searchConfig, route.index, incumbentScore = { incumbent.best?.score })

	private val publications: Publications = Publications(clock, execution)

	private val horizon: Horizon = Horizon(searchConfig.localHorizon(initialRoute), frontier)

	private val publisher: Publisher = Publisher(
		searchConfig, field, frontier, clock, publications, horizon, onSafePrefix,
		expansions = { expansions },
		incumbent = { incumbent.best },
		brakeToStop = { finisher.brakeToStop(it) },
		certify = ::certify,
		probe = probe,
	)

	init {
		frontier.reachability = ReachabilityPolicy(horizon::canReach)
		frontier.probe = probe
	}

	private val incumbent: Incumbent = Incumbent(publications, horizon, publisher, searchConfig, execution)

	private val recovery: StallRecovery = StallRecovery(
		frontier, publications, world,
		syncWorld = ::syncWorld,
	)

	private val annealing: Annealing = Annealing(
		field, frontier, horizon,
		surcharge = ::nextSurcharge,
		maxTemperature = searchConfig.maxTemperature,
	)

	private val rollouts: AnchorRollout = AnchorRollout(
		catalog, field, config, environment, profile, { route.goalPoint },
		attempts, probe,
	)

	private val gate: RolloutGate = RolloutGate(profile, environment, config) { route.goalPoint }

	private val certifier: Certifier = Certifier(initialState, profile, environment, cancelled)

	private val finisher: Finisher = Finisher(
		field, environment, config, gate, attempts, probe,
		goalPoint = { route.goalPoint },
	)

	private var expansions = 0
	private var repairs = 0
	private var expansionsWindowStart = 0
	private var finishSweeps = 0
	private var sweepEpoch = 0
	private var syncBucket = -1
	private var publishBucket = -1
	private var lastViewMillis = Long.MIN_VALUE
	private var lastPublishedTip: ValueAnchor? = null
	private var exit = SearchExit.BUDGET

	fun run(): MotionPlanResult = try {
		search()
	} finally {
		pool?.shutdownNow()
	}

	private fun search(): MotionPlanResult {
		if (cancelled()) return MotionPlanResult.Cancelled
		if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)
		val root = plantRoot()
		constructSpine(root)
		while (true) {
			val stop = stopReason()
			if (stop != null) {
				incumbent.arrival?.let { return finish(it) }
				exit = stop
				break
			}
			if (cancelled()) return MotionPlanResult.Cancelled
			execution.cursorFrame()?.let { executing -> syncWithCursor(executing)?.let { return it } }
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
		return conclude()
	}

	private fun plantRoot(): ValueAnchor {
		val bodyStance = stanceOf(initialState)
		val rootStance = if (field.isMapped(bodyStance)) bodyStance else {
			supportedStanceOf(initialState)?.takeIf { field.isMapped(it) } ?: bodyStance
		}
		val root = ValueAnchor(
			state = initialState,
			stance = rootStance,
			elapsed = 0,
			collisionEvents = 0,
			inputSwitches = 0,
			parent = null,
			inputs = emptyList(),
		)
		frontier.admit(root)
		annealing.noteGuide(rootStance)
		horizon.begin()
		return root
	}

	private fun stopReason(): SearchExit? = when {
		frontier.isExhausted && !frontier.hasBlocked && !revivable() && !restartable() -> SearchExit.EXHAUSTED
		expansions - expansionsWindowStart >= searchConfig.maxExpansions -> SearchExit.BUDGET
		else -> null
	}

	private fun revivable(): Boolean = annealing.hasSpent && annealing.canEscalate()

	private fun restartable(): Boolean = !incumbent.hasSolution && recovery.restartable()

	private fun syncWithCursor(executing: Int): MotionPlanResult? {
		horizon.follow(publications)
		annealing.rewindOnNewRoot()
		incumbent.dropUnadoptable()
		incumbent.arrival?.let { tape -> if (executing >= tape.frames - ARRIVAL_RETURN_FRAMES) return finish(tape) }
		incumbent.best?.let { solution ->
			if (finalLeg() && incumbent.ready(solution)) {
				val tipElapsed = publications.tip?.elapsed
				val pressured = tipElapsed != null && tipElapsed - executing <= searchConfig.horizonRunwayFrames
				val closing = pressured && publications.finalWindowClosing(solution.anchor)
				if (closing) incumbent.publishFinal(solution)
				if (incumbent.nearEnd(solution) || closing) incumbent.seal(solution)?.let { return finish(it) }
			}
		}
		val tip = publications.tip ?: return null
		val urgent = tip.elapsed - executing <= searchConfig.horizonCommitFrames
		val extended = publisher.commit(urgent = urgent, along = incumbent.best?.anchor)
		if (!extended && urgent) {
			incumbent.best?.takeIf { finalLeg() && incumbent.ready(it) }
				?.let { solution -> incumbent.seal(solution)?.let { return finish(it) } }
		}
		return null
	}

	private fun refreshWindowOnPublication() {
		if (publications.tip !== lastPublishedTip) {
			lastPublishedTip = publications.tip
			recovery.resetWaitWindow()
			expansionsWindowStart = expansions
		}
		if (publications.tip == null && frontier.hasParked) publisher.commit(urgent = false)
	}

	private enum class Drain { CONTINUE, BREAK }

	private fun recoverFromDrain(): Drain {
		if (recovery.waitForBlocked()) return Drain.CONTINUE
		if (annealing.hasSpent && annealing.escalate(force = true)) return Drain.CONTINUE
		if (!frontier.hasParked && !frontier.hasBlocked && restartFromTape()) return Drain.CONTINUE
		if (!finalLeg() && recovery.waitForRouteExtension()) return Drain.CONTINUE
		if (horizon.extend()) return Drain.CONTINUE
		val toward = incumbent.best?.takeIf { !incumbent.ready(it) }?.anchor
		if (!frontier.hasParked && toward == null) {
			exit = SearchExit.DRAINED
			return Drain.BREAK
		}
		if (!publisher.commit(urgent = true, along = toward)) {
			exit = SearchExit.COMMIT_REFUSED
			return Drain.BREAK
		}
		return Drain.CONTINUE
	}

	private fun periodicMaintenance() {
		if (expansions / WORLD_SYNC_INTERVAL != syncBucket) {
			syncBucket = expansions / WORLD_SYNC_INTERVAL
			syncWorld()
		}
		if (expansions / CANDIDATE_PUBLISH_INTERVAL != publishBucket) {
			publishBucket = expansions / CANDIDATE_PUBLISH_INTERVAL
			publishCandidates()
			publishSearchView()
		}
	}

	private fun selectBatch(batch: MutableList<AnchorRollout.PreparedRollout>): MotionPlanResult? {
		while (batch.size < parallelism) {
			val entry = frontier.poll() ?: break
			if (!worthExpanding(entry)) continue
			when (judgeAgainstIncumbent(entry)) {
				Verdict.SKIP -> continue
				Verdict.FINISH -> return finish(checkNotNull(incumbent.best))
				Verdict.EXPAND -> Unit
			}
			val anchor = entry.anchor
			sweepFinish(anchor)
			val action = nextAction(anchor)?.decision
			if (action == null) {
				annealing.spend(anchor)
				continue
			}
			countExpansion()
			annealing.escalate(force = false)
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

	private fun worthExpanding(entry: Frontier.OpenEntry): Boolean {
		if (!publications.adoptable(entry.anchor)) return false
		if (entry.anchor.elapsed >= horizon.end && field.guide(entry.anchor.stance) > FINISH_VALUE_TICKS) {
			frontier.park(entry)
			return false
		}
		return true
	}

	private enum class Verdict { EXPAND, SKIP, FINISH }

	private fun judgeAgainstIncumbent(entry: Frontier.OpenEntry): Verdict {
		val best = incumbent.best ?: return Verdict.EXPAND
		if (!finalLeg()) return Verdict.EXPAND
		val finishable = incumbent.ready(best) && incumbent.nearEnd(best)
		val bound = entry.bound + Solution.COLLISION_FRAME_PENALTY * entry.anchor.collisionEvents
		if (bound >= best.score) return if (finishable) Verdict.FINISH else Verdict.SKIP
		return if (finishable && incumbent.stalled) Verdict.FINISH else Verdict.EXPAND
	}

	private fun sweepFinish(anchor: ValueAnchor) {
		val remaining = field.guide(anchor.stance)
		if (anchor.sweptEpoch != sweepEpoch &&
			!hasNextLeg() &&
			remaining <= FINISH_VALUE_TICKS &&
			finishSweeps < MAX_FINISH_SWEEPS &&
			horizon.canReach(anchor) &&
			incumbent.best.let { it == null || anchor.elapsed + remaining < it.frames }
		) {
			anchor.sweptEpoch = sweepEpoch
			finishSweeps++
			finisher.finishFrom(anchor)?.let(incumbent::offer)
		}
	}

	private fun countExpansion() {
		expansions++
		clock.onExpansion()
		incumbent.tick()
		annealing.tick()
	}

	private fun executeBatch(batch: List<AnchorRollout.PreparedRollout>) {
		if (batch.size > 1 && pool != null) {
			batch.map { prepared -> pool.submit { rollouts.execute(prepared) } }.forEach { it.get() }
		} else {
			batch.forEach { rollouts.execute(it) }
		}
		for (prepared in batch) {
			val outcome = rollouts.complete(prepared)
			applyRollout(prepared.anchor, prepared.action, outcome)
			reofferOrSpend(prepared.anchor)
		}
	}

	private fun conclude(): MotionPlanResult {
		incumbent.best?.let { solution ->
			var committed = publications.tip?.elapsed ?: -1
			while (!incumbent.ready(solution) && publisher.commit(urgent = true, along = solution.anchor)) {
				val deepened = publications.tip?.elapsed ?: -1
				if (deepened <= committed) break
				committed = deepened
			}
			return finish(solution)
		}
		incumbent.fallback?.let { return finish(it) }
		return MotionPlanResult.NoSafeStop(
			attemptCount = attempts.count,
			nearest = attempts.nearest,
			remainingStart = route.plan.nodes.getOrNull(frontier.deepestProgress),
			exhaustion = stats().also { onExhaustion?.invoke(it) },
		)
	}

	private fun reofferOrSpend(anchor: ValueAnchor) {
		val surcharge = nextSurcharge(anchor)
		if (surcharge != null) {
			anchor.pendingSurcharge = surcharge
			frontier.reoffer(anchor)
		} else {
			annealing.spend(anchor)
		}
	}

	private fun applyRollout(anchor: ValueAnchor, action: TrajectoryDecision, raw: Outcome): Outcome {
		val outcome = if (raw is Outcome.Blocked) {
			frontier.parkBlocked(anchor, action)
			val capturable = world?.capturable(raw.section.x, raw.section.z) ?: true
			probe.blocked(raw.frame, raw.section, capturable, anchor.stance, action.movement)
			if (capturable) {
				Outcome.Rejected(TrajectoryDiagnostic.UnknownTerrain(raw.frame, raw.section))
			} else raw
		} else raw
		val rejection = (outcome as? Outcome.Rejected)?.diagnostic
		probe.expansion(anchor.stance, action, rejection)
		when (outcome) {
			is Outcome.Anchored -> {
				val landed = outcome.anchor
				annealing.noteGuide(landed.stance)
				if (landed.stance == route.goal) {
					val handoff = legs?.takeIf { it.hasNext() }?.handoff(route.goal)
					if (handoff != null) {
						switchLeg(landed, handoff)
						publisher.publishPrefix(landed)
						return outcome
					}
					offerGoalStop(landed)
				}
				frontier.admit(landed)
				publisher.publishPrefix(landed)
			}

			is Outcome.Arrived -> if (!hasNextLeg()) incumbent.offer(
				Solution.of(
					anchor, outcome.frames,
					anchor.collisionEvents + collisionEvents(anchor.state, outcome.frames),
				)
			)

			is Outcome.Blocked -> Unit

			is Outcome.Rejected -> if (action.seedsHazardOnFailure) {
				anchor.hazardFrame = launchSeedFrame(outcome.diagnostic)
					?.let { frame -> anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
					?: anchor.hazardFrame
			}
		}
		return outcome
	}

	private fun offerGoalStop(landed: ValueAnchor) {
		if (config.touchArrival) {
			finisher.brakeNearGoal(landed, config.touchRestRadius)?.let(incumbent::offer)
		} else if (incumbent.best == null && !incumbent.fallbackCovers(landed.elapsed)) {
			finisher.brakeNearGoal(landed, config.goalRadius)?.let(incumbent::offerFallback)
		}
	}

	private fun constructSpine(root: ValueAnchor) {
		var current = root
		var spent = 0
		val budget = (route.plan.nodes.size + 4) * SPINE_ATTEMPTS_PER_EDGE * 2
		while (spent < budget && !cancelled()) {
			val remaining = field.guide(current.stance)
			if (!remaining.isFinite()) break
			if (remaining <= FINISH_VALUE_TICKS) {
				if (!hasNextLeg() && current.sweptEpoch != sweepEpoch && finishSweeps < MAX_FINISH_SWEEPS) {
					current.sweptEpoch = sweepEpoch
					finishSweeps++
					finisher.finishFrom(current)?.let(incumbent::offer)
				}
				break
			}
			var advanced: ValueAnchor? = null
			var attemptsHere = 0
			var blocked = false
			while (attemptsHere < SPINE_ATTEMPTS_PER_EDGE) {
				val action = nextAction(current)?.decision ?: break
				attemptsHere++
				spent++
				countExpansion()
				when (val outcome = applyRollout(current, action, rollouts.transition(current, action))) {
					is Outcome.Anchored -> advanced = outcome.anchor
					is Outcome.Blocked -> blocked = true
					is Outcome.Rejected, is Outcome.Arrived -> Unit
				}
				if (advanced != null || blocked) break
			}
			if (blocked) break
			current = advanced ?: break
		}
	}

	private fun nextAction(anchor: ValueAnchor) = vocabulary.next(anchor, annealing.temperature, annealing.actionsEpoch)

	private fun nextSurcharge(anchor: ValueAnchor): Double? =
		vocabulary.nextSurcharge(anchor, annealing.temperature, annealing.actionsEpoch)

	private fun syncWorld() {
		val result = world?.sync(route.plan) ?: return
		repairFor(result.mutations)
		when (result) {
			WorldSyncResult.Quiet -> return

			is WorldSyncResult.Woken -> if (frontier.hasBlocked) {
				resweep()
				frontier.wakeBlocked()
			}

			is WorldSyncResult.Changed -> {
				resweep()
				publisher.forgetRefusedCommit()
				val next = result.route
				if (next != null && (next.nodes != route.plan.nodes || next.goal != route.plan.goal)) {
					route.replace(next)
					frontier.updateRoute(route.index)
				} else {
					frontier.rescore()
				}
				frontier.wakeBlocked()
			}
		}
	}

	private fun repairFor(mutations: Set<PathingSection>) {
		val cut = publications.repairCut(mutations) ?: return
		frontier.dropDescendants(cut)
		horizon.reRoot(cut)
		publisher.abandonTip()
		repairs++
		incumbent.dropBeyond(cut)
		restartWindow(cut.stance)
		frontier.updateRoute(route.index)
	}

	private fun restartFromTape(): Boolean {
		if (incumbent.best != null) return false
		val seed = recovery.restartSeed() ?: return false
		annealing.clearSpent()
		restartWindow(seed.stance)
		horizon.reRoot(seed)
		return true
	}

	private fun switchLeg(anchor: ValueAnchor, handoff: LegHandoff) {
		legTouches += LegTouch(anchor.elapsed, route.goal)
		anchor.legRoot = true
		(field as? SwitchableValueField)?.current = handoff.field
		route.replace(handoff.route)
		incumbent.clear()
		annealing.clearSpent()
		restartWindow(anchor.stance)
		frontier.retainLineage(anchor)
		frontier.updateRoute(route.index)
		horizon.reRoot(anchor)
		recovery.beginLeg()
		publisher.forgetRefusedCommit()
	}

	private fun resweep() {
		sweepEpoch++
		finishSweeps = 0
	}

	private fun restartWindow(seed: Stance) {
		resweep()
		expansionsWindowStart = expansions
		annealing.rewindForRestart(seed)
	}

	private fun finalLeg(): Boolean = finalGoal == null || route.goal == finalGoal

	private fun hasNextLeg(): Boolean = legs?.hasNext() == true

	private fun finish(solution: Solution): MotionPlanResult {
		exit = SearchExit.SOLVED
		onExhaustion?.invoke(stats())
		return certify(solution)
	}

	private fun certify(solution: Solution): MotionPlanResult {
		val result = certifier.certify(
			solution,
			route = route.plan,
			attemptCount = attempts.count,
		)
		if (result !is MotionPlanResult.Success || legTouches.isEmpty()) return result
		return result.copy(legTouches = legTouches.filter { it.frame <= solution.frames })
	}

	private fun publishCandidates() {
		if (!publisher.enabled || !probe.candidatesEnabled) return
		val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries
		if (pool.isNotEmpty()) probe.candidates(candidatePaths(pool, publications.tip))
	}

	private fun publishSearchView() {
		val now = clock.elapsedMillis()
		if (now - lastViewMillis < VIEW_INTERVAL_MILLIS) return
		lastViewMillis = now
		probe.stats(stats())
		if (probe.treeEnabled) {
			probe.tree(
				searchTree(
					annealing.spent, frontier.parkedEntries, frontier.openEntries,
					incumbent.best?.anchor, publications.tip,
				),
			)
		}
	}

	private fun stats(): SearchStats = SearchStats(
		exit = exit,
		expansions = expansions,
		windowExpansions = expansions - expansionsWindowStart,
		windowBudget = searchConfig.maxExpansions,
		temperature = annealing.temperature.current,
		openAnchors = frontier.openSize,
		parkedAnchors = frontier.parkedSize,
		blockedAttempts = frontier.blockedSize,
		spentAnchors = annealing.spent.size,
		tapeRestarts = recovery.tapeRestarts,
		routeNodes = route.plan.nodes.size,
		deepestRouteIndex = frontier.deepestProgress,
		commitAttempts = publisher.commitAttempts,
		publishRefusals = publisher.refusals,
		repairs = repairs,
		firstSolutionFrames = incumbent.firstFrames,
		solutionsFound = incumbent.found,
		admitted = frontier.anchorsAdmitted,
		merged = frontier.beamDominated + frontier.beamEvicted + frontier.beamCapped,
		rootFrame = horizon.root?.elapsed ?: -1,
		publishedFrame = publications.tip?.elapsed ?: -1,
		horizonEnd = horizon.end,
		cursorFrame = publications.observedCursor,
		bestScore = incumbent.best?.score,
	)

	private companion object {
		const val CANDIDATE_PUBLISH_INTERVAL = 32
		const val WORLD_SYNC_INTERVAL = 64
		const val VIEW_INTERVAL_MILLIS = 100L
		const val ARRIVAL_RETURN_FRAMES = 2
		const val SPINE_ATTEMPTS_PER_EDGE = 8
		const val MAX_FINISH_SWEEPS = 64
	}
}
