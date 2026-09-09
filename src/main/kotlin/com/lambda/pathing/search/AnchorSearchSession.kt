package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.actions.control.BrakeToStopProgram
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.control.PursuitTracker
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.search.ValueFieldAnchorSearch.stanceOf
import com.lambda.pathing.world.center
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.concurrent.ExecutorService

private const val CANDIDATE_PUBLISH_INTERVAL = 32

private const val WORLD_SYNC_INTERVAL = 64

internal class AnchorSearchSession(
	private var route: CoarseRoutePlan,
	private val catalog: MovementCatalog,
	private val field: ValueField,
	private val initialState: MovementSimulationState,
	profile: PlayerPhysicsProfile,
	private val environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints,
	private val searchConfig: ValueFieldSearchConfig,
	onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
	private val cursorFrame: (() -> Int?)?,
	private val clock: SearchClock,
	private val cancelled: () -> Boolean,
	worldWait: ((Long) -> Boolean)?,
	private val worldSync: ((CoarseRoutePlan) -> WorldSyncResult)?,
	private val sectionCapturable: ((Int, Int) -> Boolean)?,
	expandGuide: ((Double) -> Unit)?,
	adoptedSequence: (() -> Long)?,
	private val finalGoal: Stance?,
	private val probe: SearchProbe,
	private val onExhaustion: ((SearchExhaustion) -> Unit)?,
	private val parallelism: Int = 1,
	private val executor: ExecutorService? = null,

	private val nextLeg: ((Stance) -> LegHandoff?)? = null,
	private val hasNextLeg: () -> Boolean = { false },
) {
	private val vocabulary = ActionSet(catalog, field, config, searchConfig)

	private val legTouches = ArrayList<LegTouch>()

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
		frontier.probe = probe
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

	private var finalSolution: Solution? = null

	private fun finishedResult(): MotionPlanResult? {
		val published = finalSolution ?: return null
		val out = best?.takeIf { it.score <= published.score } ?: published
		return finish(out)
	}

	private fun sealFinished(solution: Solution): MotionPlanResult? {
		if (horizon.publishFinished(solution)) {
			finalSolution = solution
			return null
		}
		return if (finalSolution == null) finish(solution) else null
	}


	private fun revivable(): Boolean = recovery.hasSpent && annealing.canEscalate()

	private fun restartable(): Boolean =
		best == null && finalSolution == null && recovery.restartable()

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

	private fun stopReason(): String? = when {
		frontier.isExhausted && !frontier.hasBlocked && !revivable() && !restartable() -> "exhausted"
		expansions - expansionsWindowStart >= searchConfig.maxExpansions -> "budget"
		else -> null
	}

	private fun syncWithCursor(executing: Int): MotionPlanResult? {
		tempo.observe(executing, expansions)

		horizon.advanceExecutedRoot()
		annealing.rewindOnNewRoot()
		best?.let {
			if (!horizon.canReach(it.anchor) || !executionCompatible(it.anchor)) {
				best = null
			}
		}

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

		val urgent = runway <= searchConfig.horizonCommitFrames
		val extended = horizon.commitFromCandidates(
			urgent = urgent,
			along = best?.takeIf { !readyToFinish(it) }?.anchor,
		)

		if (!extended && urgent) {
			best?.let {
				if (mayFinalize() && readyToFinish(it)) {
					sealFinished(it)?.let { result -> return result }
				}
			}
		}
		return null
	}

	private fun refreshWindowOnPublication() {
		if (horizon.safeAnchor !== lastPublishedTip) {
			lastPublishedTip = horizon.safeAnchor
			recovery.resetWaitWindow()
			expansionsWindowStart = expansions
		}

		if (horizon.safeAnchor == null && frontier.hasParked) horizon.commitFromCandidates(urgent = false)
	}

	private enum class Drain { CONTINUE, BREAK }

	private fun recoverFromDrain(): Drain {

		if (frontier.reviveStarved { horizon.adoptable(it) }) return Drain.CONTINUE

		if (recovery.waitForBlocked()) return Drain.CONTINUE

		if (recovery.hasSpent && annealing.escalate(force = true)) return Drain.CONTINUE

		if (!frontier.hasParked && !frontier.hasBlocked && restartFromTape()) return Drain.CONTINUE

		if (!mayFinalize() && recovery.waitForRouteExtension()) return Drain.CONTINUE

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

	private fun selectBatch(batch: MutableList<AnchorRollout.PreparedRollout>): MotionPlanResult? {
		while (batch.size < parallelism) {
			val entry = frontier.poll() ?: break
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

	private fun worthExpanding(entry: Frontier.OpenEntry): Boolean {

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

	private fun judgeAgainstIncumbent(entry: Frontier.OpenEntry): Verdict {
		val incumbent = best
		val entryScoreBound = entry.bound + Solution.COLLISION_FRAME_PENALTY * entry.anchor.collisionEvents
		if (incumbent != null && mayFinalize() && entryScoreBound >= incumbent.score) {
			if (readyToFinish(incumbent) && bodyNearEnd(incumbent)) return Verdict.FINISH

			return Verdict.SKIP
		}
		if (incumbent != null && mayFinalize() && readyToFinish(incumbent) && bodyNearEnd(incumbent) &&
			expansionsSinceImprovement >= searchConfig.stallExpansions
		) {
			return Verdict.FINISH
		}
		return Verdict.EXPAND
	}

	private fun sweepFinish(anchor: ValueAnchor) {
		val remaining = field.guide(anchor.stance)
		if (anchor.sweptEpoch != sweepEpoch &&
			!hasNextLeg() &&
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

	private fun countExpansion() {
		expansions++
		clock.onExpansion()
		expansionsSinceImprovement++
		annealing.tick()
	}

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

	private fun concludeWithIncumbent(): MotionPlanResult {
		best?.let { solution ->

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

				if (outcome.anchor.stance == goalStance) {
					val handoff = if (hasNextLeg()) nextLeg?.invoke(goalStance) else null
					if (handoff != null) {
						switchLeg(outcome.anchor, handoff)
						horizon.publishPrefix(outcome.anchor)
						return outcome
					}
					if (config.touchArrival) finishByTouch(outcome.anchor)
					else if (best == null) finishByBraking(outcome.anchor)
				}
				frontier.admit(outcome.anchor)
				horizon.publishPrefix(outcome.anchor)
			}

			is Outcome.Arrived -> if (!hasNextLeg()) retain(
				Solution.of(
					anchor,
					outcome.frames,
					TerminalApproach(
						action.sprint, PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
						config.brakeDistances.first(), null,
					),
					anchor.collisionEvents + collisionEvents(
						anchor.state,
						outcome.frames,
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

	private fun constructSpine(root: ValueAnchor) {
		var current = root
		var spent = 0
		val budget = (route.nodes.size + 4) * SPINE_ATTEMPTS_PER_EDGE * 2
		var stalled: TrajectoryDiagnostic? = null
		while (spent < budget && !cancelled()) {
			val remaining = field.guide(current.stance)
			if (!remaining.isFinite()) break
			if (remaining <= searchConfig.finishValueTicks) {
				if (!hasNextLeg() && current.sweptEpoch != sweepEpoch && finishSweeps < searchConfig.maxFinishSweeps) {
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

		var stable = 0
		var stableEnd = -1
		var previous = anchor.state
		var twoBack: MovementSimulationState? = null
		for ((index, _, state) in rollout.frames) {
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
				stableEnd = index
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

	private fun finishByTouch(anchor: ValueAnchor) {
		val (solution, resting) = brakeWithResting(anchor) ?: return
		val goal = goalPoint
		val distance = kotlin.math.hypot(resting.position.x - goal.x, resting.position.z - goal.z)
		if (distance > config.touchRestRadius) return
		if (kotlin.math.abs(resting.position.y - goal.y) > GOAL_BRAKE_VERTICAL_TOLERANCE) return
		retain(solution)
	}

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
				trace = anchor.trace,
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
			legSwitches = stats.legSwitches,
			forkStarvedDrops = stats.forkStarvedDrops,
			commitAttempts = horizon.commitAttempts,
			commitSuppressed = horizon.commitSuppressed,
			publishRefusals = horizon.publishRefusals,
			repairs = horizon.repairs,
			junctionRestarts = horizon.junctionRestarts,
		)
	}

	private fun certify(solution: Solution): MotionPlanResult {
		val result = certifier.certify(
			solution,
			route = route,
			remainingGuideTicks = field.guide(solution.anchor.stance),
			attemptCount = attempts.count,
		)
		if (result !is MotionPlanResult.Success || legTouches.isEmpty()) return result
		return result.copy(legTouches = legTouches.filter { it.frame <= solution.frames })
	}

	private fun switchLeg(anchor: ValueAnchor, handoff: LegHandoff) {
		legTouches += LegTouch(anchor.elapsed, goalStance)
		anchor.legRoot = true
		(field as? com.lambda.pathing.coarse.SwitchableValueField)?.current = handoff.field
		route = handoff.route
		goalStance = route.goal
		goalPoint = goalStance.center(environment)
		routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

		best = null
		finalSolution = null
		brakedFallback = null
		finishSweeps = 0
		sweepEpoch++
		expansionsWindowStart = expansions
		expansionsSinceImprovement = 0

		frontier.retainLineage(anchor)
		frontier.updateRoute(routeIndex)
		horizon.reRootForRestart(anchor)
		recovery.beginLeg()
		annealing.rewindForRestart(anchor.stance)
		annealing.noteGuide(anchor.stance)
		horizon.invalidateCommitMemo()
		stats.legSwitches++
	}

	private fun retain(solution: Solution) {

		if (!horizon.canReach(solution.anchor)) return
		if (!executionCompatible(solution.anchor)) return
		val incumbent = best

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

	private fun bodyNearEnd(solution: Solution): Boolean {
		val executing = cursorFrame?.invoke() ?: return true
		return solution.frames - executing <= searchConfig.finalizeArrivalFrames
	}

	private fun finish(solution: Solution): MotionPlanResult {
		stats.exit = "solved"
		val best = improve(solution)
		onExhaustion?.invoke(exhaustion())
		return certify(best)
	}

	private val improver by lazy {
		PlanImprover(
			rollouts = rollouts,

			vocabulary = ActionSet(catalog, field, config, searchConfig, momentum = true),
			finisher = finisher,
			canReach = horizon::canReach,
		)
	}

	private fun improveIncumbent() {
		if (searchConfig.improvementBudget <= 0) return
		val incumbent = best ?: return
		if (improver.rolloutsSpent >= searchConfig.improvementBudget) return
		val slice = minOf(searchConfig.improvementBudget, improver.rolloutsSpent + IMPROVEMENT_SLICE_ROLLOUTS)
		val improved = measuredImprovement { improver.improve(incumbent, slice) } ?: return

		if (!horizon.canReach(improved.anchor) || !executionCompatible(improved.anchor)) return
		stats.improvementSaved += incumbent.frames - improved.frames
		best = improved
		expansionsSinceImprovement = 0
	}

	private inline fun <T> measuredImprovement(block: () -> T): T {
		val before = improver.splices
		val result = block()
		stats.improvementRollouts = improver.rolloutsSpent
		stats.improvementSplices += improver.splices - before
		stats.improvementDiagnosis = improver.diagnosis()
		return result
	}

	private fun improve(solution: Solution): Solution {
		if (searchConfig.improvementBudget <= 0) return solution
		val improved = measuredImprovement { improver.improve(solution, searchConfig.improvementBudget) } ?: return solution

		if (!horizon.canReach(improved.anchor)) return solution
		stats.improvementSaved += solution.frames - improved.frames
		return improved
	}
}

private fun divergenceFrame(action: TrajectoryDecision): Int = action.launchDelayFrames ?: Int.MAX_VALUE

private const val BRAKE_TAIL_FRAMES = 64


private const val VIEW_INTERVAL_MILLIS = 100L

private const val MAX_TREE_NODES = 4096

private const val FINAL_IMPROVEMENT_WINDOW = 256

private const val IMPROVEMENT_INTERVAL = 2000
private const val IMPROVEMENT_SLICE_ROLLOUTS = 250

private const val ARRIVAL_RETURN_FRAMES = 2

private const val OFF_TAPE_RETAIN_GAIN_FRAMES = SWAP_FLOOR_GAIN_TICKS.toInt()

private const val SPINE_ATTEMPTS_PER_EDGE = 8

private const val GOAL_BRAKE_VERTICAL_TOLERANCE = 0.05
