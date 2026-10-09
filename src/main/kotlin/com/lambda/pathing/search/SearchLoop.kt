package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class SearchLoop(
	initialRoute: CoarseRoutePlan,
	catalog: MovementCatalog,
	private val field: ValueField,
	private val initialState: MovementSimulationState,
	profile: PlayerPhysicsProfile,
	environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints,
	private val searchConfig: SearchConfig = SearchConfig(),
	onPublish: ((MotionPlanResult.Success, Boolean) -> Unit)? = null,
	private val execution: ExecutionFeedback = ExecutionFeedback.DETACHED,
	private val clock: SearchClock = SystemSearchClock(),
	private val cancelled: () -> Boolean = { false },
	private val world: WorldLink? = null,
	private val finalGoal: Stance? = null,
	private val probe: SearchProbe = SearchProbe.NONE,
	private val onConcluded: ((SearchStats) -> Unit)? = null,
	parallelism: Int = 1,
	private val legs: LegSource? = null,
) {
	private val parallelism = parallelism.coerceAtLeast(1)
	private val live = world != null
	private val unsupported = initialRoute.edges.mapTo(HashSet()) { it.movement }.filterTo(HashSet()) { !catalog.supports(it) }
	private val route = RouteState(initialRoute, environment)
	private val attempts = AttemptAccumulator(probe)
	private val budget = ExpansionBudget(searchConfig.maxExpansions, clock)
	private val frontier = Frontier(field, config, searchConfig, route.index, searchConfig.localHorizon(initialRoute))
	private val publications = Publications(clock, execution)
	private val incumbent = Incumbent(publications, frontier, searchConfig, execution, budget)
	private val annealing = Annealing(field, frontier, ActionSet(catalog, field, config), budget, searchConfig.maxTemperature)
	private val rollouts = AnchorRollout(catalog, field, config, environment, profile, route, attempts, probe)
	private val crew = if (parallelism > 1) RolloutCrew(parallelism - 1, rollouts) else null
	private val certifier = Certifier(initialState, profile, environment, cancelled, route, attempts)
	private val finisher = Finisher(field, environment, config, RolloutGate(profile, environment, config, route), attempts, route)
	private val publisher = Publisher(searchConfig, field, frontier, clock, publications, onPublish, budget, incumbent, finisher, certifier, probe, live)
	private val recovery = StallRecovery(frontier, publications, world)

	private var repairs = 0
	private var syncBucket = -1
	private var publishBucket = -1
	private var lastViewMillis = -VIEW_INTERVAL_MILLIS
	private var lastPublishedTip: ValueAnchor? = null
	private var refinedSeed: ValueAnchor? = null
	private var idleCursor = -1
	private var idleSlices = 0
	private val legRoots = ArrayDeque<ValueAnchor>()
	private var exit = SearchExit.BUDGET

	fun run(): MotionPlanResult = try {
		search()
	} finally {
		crew?.close()
	}

	private fun search(): MotionPlanResult {
		if (cancelled()) return MotionPlanResult.Cancelled
		if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)
		constructSpine(plantRoot())
		while (true) {
			stopReason()?.let { stop -> return incumbent.arrival?.let(::finish) ?: conclude(stop) }
			if (cancelled()) return MotionPlanResult.Cancelled
			execution.cursorFrame()?.let { executing -> followCursor(executing)?.let { return it } }
			followPublication()
			if (!frontier.hasOpen) {
				val drained = recoverFromDrain() ?: continue
				return conclude(drained)
			}
			periodicMaintenance()
			val batch = selectBatch() ?: return finish(checkNotNull(incumbent.best))
			expand(batch)
		}
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
		frontier.admit(root, incumbentScore = null)
		annealing.noteGuide(rootStance)
		return root
	}

	private fun stopReason(): SearchExit? = when {
		frontier.isExhausted && !frontier.hasBlocked && !annealing.revivable && !restartable() && !extendable() && !refine() && !walkingToGoal() -> SearchExit.EXHAUSTED
		budget.exhausted && !awaitingArrival() && !walkingToGoal() && !restartFromTape() -> SearchExit.BUDGET
		else -> null
	}

	private fun awaitingArrival(): Boolean = incumbent.isSealed && execution.cursorFrame() != null

	private fun extendable(): Boolean = world != null && !routeReachesFinalGoal()

	private fun walkingToGoal(): Boolean = live && incumbent.best != null && routeReachesFinalGoal()

	private fun idleUntilArrival(): Boolean {
		if (!walkingToGoal()) return false
		val best = checkNotNull(incumbent.best)
		if (!incumbent.isSealed && incumbent.ready(best) && publisher.seal(best) != null) return false
		val executing = execution.cursorFrame() ?: -1
		if (executing != idleCursor) {
			idleCursor = executing
			idleSlices = 0
		}
		if (idleSlices++ >= IDLE_PATIENCE_SLICES) return false
		world?.await(StallRecovery.WAIT_SLICE_MILLIS)
		syncWorld()
		return true
	}

	private fun restartable(): Boolean = !incumbent.hasSolution && recovery.restartable()

	private fun followCursor(executing: Int): MotionPlanResult? {
		frontier.follow(publications)
		rewindLegs()
		annealing.rewindOnNewRoot()
		incumbent.dropUnadoptable()
		incumbent.arrival?.let { tape -> if (executing >= tape.frames - ARRIVAL_RETURN_FRAMES) return finish(tape) }
		incumbent.best?.let { solution ->
			if (routeReachesFinalGoal() && incumbent.ready(solution)) {
				val tipElapsed = publications.tip?.elapsed
				val pressured = tipElapsed != null && tipElapsed - executing <= searchConfig.horizonRunwayFrames
				val closing = pressured && publications.finalWindowClosing(solution.anchor)
				if (incumbent.nearEnd(solution) || closing) publisher.seal(solution)?.let { return finish(it) }
			}
		}
		val tip = publications.tip ?: return null
		val urgent = tip.elapsed - executing <= searchConfig.horizonCommitFrames
		if (!urgent) budget.restartWindow()
		val extended = publisher.commit(urgent = urgent, along = incumbent.best?.anchor)
		if (!extended && urgent) {
			incumbent.best?.takeIf { routeReachesFinalGoal() && incumbent.ready(it) }
				?.let { solution -> publisher.seal(solution)?.let { return finish(it) } }
		}
		return null
	}

	private fun followPublication() {
		if (publications.tip !== lastPublishedTip) {
			lastPublishedTip = publications.tip
			recovery.resetWaitWindow()
			budget.restartWindow()
		}
		if (publications.tip == null && frontier.hasParked) publisher.commit(urgent = false)
	}

	private fun recoverFromDrain(): SearchExit? {
		if (recovery.mayAwaitBlocked()) {
			if (world?.await(StallRecovery.WAIT_SLICE_MILLIS) == true) {
				syncWorld()
				frontier.wakeBlocked()
				recovery.noteWake(frontier.hasOpen)
			}
			return null
		}
		if (annealing.revive()) return null
		if (!frontier.hasParked && !frontier.hasBlocked && restartFromTape()) return null
		if (!routeReachesFinalGoal() && recovery.mayAwaitRouteExtension()) {
			world?.await(StallRecovery.WAIT_SLICE_MILLIS)
			syncWorld()
			frontier.wakeBlocked()
			return null
		}
		if (frontier.extend()) return null
		val toward = incumbent.best?.takeIf { !incumbent.ready(it) }?.anchor
		if (!frontier.hasParked && toward == null) return SearchExit.DRAINED.takeUnless { refine() || idleUntilArrival() }
		if (publisher.commit(urgent = true, along = toward)) return null
		return SearchExit.COMMIT_REFUSED.takeUnless { idleUntilArrival() }
	}

	private fun periodicMaintenance() {
		if (budget.count / WORLD_SYNC_INTERVAL != syncBucket) {
			syncBucket = budget.count / WORLD_SYNC_INTERVAL
			syncWorld()
		}
		if (budget.count / CANDIDATE_PUBLISH_INTERVAL != publishBucket) {
			publishBucket = budget.count / CANDIDATE_PUBLISH_INTERVAL
			publishCandidates()
			publishSearchView()
		}
	}

	private fun selectBatch(): List<AnchorRollout.PreparedRollout>? {
		val batch = ArrayList<AnchorRollout.PreparedRollout>(parallelism)
		while (batch.size < parallelism) {
			val entry = frontier.poll() ?: break
			when (judge(entry)) {
				Verdict.SKIP -> continue
				Verdict.FINISH -> return null
				Verdict.EXPAND -> Unit
			}
			val anchor = entry.anchor
			sweepFinish(anchor)
			val action = annealing.next(anchor)
			if (action == null) {
				annealing.spend(anchor)
				continue
			}
			budget.spend()
			annealing.heat()
			val prepared = rollouts.prepare(anchor, action)
			if (prepared == null) {
				applyRollout(anchor, action, Outcome.Rejected(TrajectoryDiagnostic.NoStop(0, 0.0, anchor.speed)))
				reofferOrSpend(anchor)
				continue
			}
			batch += prepared
		}
		return batch
	}

	private enum class Verdict { EXPAND, SKIP, FINISH }

	private fun judge(entry: Frontier.OpenEntry): Verdict {
		val anchor = entry.anchor
		if (!publications.adoptable(anchor)) return Verdict.SKIP
		if (anchor.elapsed >= frontier.end && field.guide(anchor.stance) > FINISH_VALUE_TICKS) {
			frontier.park(entry)
			return Verdict.SKIP
		}
		val best = incumbent.best ?: return Verdict.EXPAND
		if (!routeReachesFinalGoal()) return Verdict.EXPAND
		val finishable = !live && incumbent.ready(best) && incumbent.nearEnd(best)
		val bound = entry.bound + Solution.COLLISION_FRAME_PENALTY * anchor.collisionEvents
		if (bound >= best.score) return if (finishable) Verdict.FINISH else Verdict.SKIP
		return if (finishable && incumbent.stalled) Verdict.FINISH else Verdict.EXPAND
	}

	private fun sweepFinish(anchor: ValueAnchor) {
		val remaining = field.guide(anchor.stance)
		if (remaining <= FINISH_VALUE_TICKS && frontier.canReach(anchor) &&
			incumbent.best.let { it == null || anchor.elapsed + remaining < it.frames }
		) sweep(anchor)
	}

	private fun sweep(anchor: ValueAnchor) {
		if (!hasNextLeg()) finisher.sweep(anchor)?.let(incumbent::offer)
	}

	private fun expand(batch: List<AnchorRollout.PreparedRollout>) {
		if (batch.size > 1 && crew != null) crew.run(batch) else batch.forEach { rollouts.execute(it) }
		for (prepared in batch) {
			val outcome = rollouts.complete(prepared)
			applyRollout(prepared.anchor, prepared.action, outcome)
			reofferOrSpend(prepared.anchor)
		}
	}

	private fun conclude(stop: SearchExit): MotionPlanResult {
		exit = stop
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
			exhaustion = stats().also { onConcluded?.invoke(it) },
		)
	}

	private fun reofferOrSpend(anchor: ValueAnchor) {
		val surcharge = annealing.surcharge(anchor)
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
				val atGoal = landed.stance == route.goal
				val next = if (atGoal) legs?.handoff(route.goal) else null
				if (next != null) switchLeg(landed, next) else {
					if (atGoal) offerGoalStop(landed)
					frontier.admit(landed, incumbent.best?.score)
				}
				publisher.publishPrefix(landed)
			}

			is Outcome.Arrived -> if (!hasNextLeg()) incumbent.offer(
				Solution(anchor, outcome.frames)
			)

			is Outcome.Blocked -> Unit

			is Outcome.Rejected -> if (action.seedsHazardOnFailure) {
				launchSeedFrame(outcome.diagnostic)?.let { frame -> anchor.hazardFrame = anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
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
		val limit = (route.plan.nodes.size + 4) * SPINE_ATTEMPTS_PER_EDGE * 2
		while (spent < limit && !cancelled()) {
			val remaining = field.guide(current.stance)
			if (!remaining.isFinite()) break
			if (remaining <= FINISH_VALUE_TICKS) {
				sweep(current)
				break
			}
			var advanced: ValueAnchor? = null
			var attemptsHere = 0
			while (attemptsHere < SPINE_ATTEMPTS_PER_EDGE) {
				val action = annealing.next(current) ?: break
				attemptsHere++
				spent++
				budget.spend()
				val outcome = applyRollout(current, action, rollouts.transition(current, action))
				if (outcome is Outcome.Blocked) break
				if (outcome is Outcome.Anchored) {
					advanced = outcome.anchor
					break
				}
			}
			current = advanced ?: break
		}
	}

	private fun syncWorld() {
		val root = frontier.root ?: return
		val result = world?.sync(route.plan, root) ?: return
		result.revision?.let(route::advanceRevision)
		if (result.mutations.isNotEmpty()) certifier.forget()
		repairFor(result.mutations)
		when (result) {
			WorldSyncResult.Quiet -> return

			is WorldSyncResult.Woken -> if (frontier.hasBlocked) {
				finisher.resetSweeps()
				frontier.wakeBlocked()
			}

			is WorldSyncResult.Changed -> {
				finisher.resetSweeps()
				publisher.forgetRefusedCommit()
				val next = result.route
				if (next != null && (next.nodes != route.plan.nodes || next.goal != route.plan.goal)) {
					if (next.goal != route.plan.goal) incumbent.clear()
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
		frontier.reRoot(cut)
		rewindLegs()
		publisher.abandonTip()
		repairs++
		incumbent.dropBeyond(cut)
		restartWindow(cut.stance)
		frontier.updateRoute(route.index)
	}

	private fun refine(): Boolean {
		if (!frontier.widenable && !live) return false
		val solution = incumbent.best?.takeIf { routeReachesFinalGoal() } ?: return false
		val executing = execution.cursorFrame() ?: if (live) 0 else return false
		val lead = executing + searchConfig.horizonCommitFrames + Publications.DIVERGENCE_MARGIN_FRAMES
		val seed = solution.anchor.lineFrom(frontier.root).firstOrNull { it.elapsed >= lead } ?: return false
		if (frontier.widenable) frontier.widen() else if (seed === refinedSeed) return false
		refinedSeed = seed
		seed.attempted.clear()
		annealing.clearSpent()
		restartWindow(seed.stance)
		frontier.reRoot(seed)
		return true
	}

	private fun restartFromTape(): Boolean {
		if (incumbent.best != null) return false
		val seed = recovery.restartSeed() ?: return false
		annealing.clearSpent()
		restartWindow(seed.stance)
		frontier.reRoot(seed)
		rewindLegs()
		return true
	}

	private fun switchLeg(anchor: ValueAnchor, next: CoarseRoutePlan) {
		route.replace(next)
		anchor.legRoot = true
		legRoots.addLast(anchor)
		incumbent.clear()
		annealing.clearSpent()
		restartWindow(anchor.stance)
		frontier.clear()
		frontier.updateRoute(route.index)
		frontier.reRoot(anchor)
		recovery.beginLeg()
		publisher.forgetRefusedCommit()
	}

	private fun restartWindow(seed: Stance) {
		finisher.resetSweeps()
		budget.restartWindow()
		annealing.rewindForRestart(seed)
	}

	private fun rewindLegs() {
		val root = frontier.root ?: return
		val chain = legs ?: return
		while (legRoots.isNotEmpty() && !root.descendsFrom(legRoots.last())) {
			legRoots.removeLast().legRoot = false
			route.replace(chain.rewind())
			incumbent.clear()
			annealing.clearSpent()
			restartWindow(root.stance)
			frontier.updateRoute(route.index)
			recovery.beginLeg()
			publisher.forgetRefusedCommit()
		}
	}

	private fun routeReachesFinalGoal(): Boolean = finalGoal == null || route.goal == finalGoal

	private fun hasNextLeg(): Boolean = legs?.hasNext() == true

	private fun finish(solution: Solution): MotionPlanResult {
		exit = SearchExit.SOLVED
		onConcluded?.invoke(stats())
		return certifier.certify(solution)
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
		expansions = budget.count,
		windowExpansions = budget.inWindow,
		windowBudget = searchConfig.maxExpansions,
		temperature = annealing.temperature,
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
		merged = frontier.beamMerged,
		rootFrame = frontier.root?.elapsed ?: -1,
		publishedFrame = publications.tip?.elapsed ?: -1,
		horizonEnd = frontier.end,
		cursorFrame = publications.observedCursor,
		bestScore = incumbent.best?.score,
	)

	private companion object {
		const val CANDIDATE_PUBLISH_INTERVAL = 32
		const val WORLD_SYNC_INTERVAL = 64
		const val VIEW_INTERVAL_MILLIS = 100L
		const val ARRIVAL_RETURN_FRAMES = 2
		const val SPINE_ATTEMPTS_PER_EDGE = 8
		const val IDLE_PATIENCE_SLICES = 5
	}
}
