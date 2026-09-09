package com.lambda.pathing.search

import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import net.minecraft.util.math.Vec3d

internal class Publication(
	val sequence: Long,
	val anchor: ValueAnchor,
	val brakeAnchor: ValueAnchor,

	val frameDependencies: List<Set<com.lambda.pathing.core.VoxelPos>> = emptyList(),
	val planSegments: List<PlanSegment> = emptyList(),
	val initialState: com.lambda.pathing.physics.MovementSimulationState? = null,
)

internal class HorizonController(
	private val searchConfig: ValueFieldSearchConfig,
	private val field: ValueField,
	private val frontier: Frontier,
	private val clock: SearchClock,
	private val cursorFrame: (() -> Int?)?,
	private val adoptedSequence: (() -> Long)?,
	private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
	private val expansionCount: () -> Int,

	private val incumbentAnchor: () -> ValueAnchor?,
	private val brakeToStop: (ValueAnchor) -> Solution?,
	private val certify: (Solution) -> MotionPlanResult,
	private val probe: SearchProbe,
) {
	var publishedTip: ValueAnchor? = null
		private set

	val safeAnchor: ValueAnchor? get() = publishedTip

	var horizonEnd = Int.MAX_VALUE
		private set

	var committed = false
		private set

	var reachableRoot: ValueAnchor? = null
		private set

	private var expansionsAtPublish = 0

	private var publishedSequence = 0L

	private var pendingAckSinceMillis: Long? = null

	var commitAttempts = 0
		private set
	var commitSuppressed = 0
		private set
	var publishRefusals = 0
		private set

	private class CommitInputs(
		val tip: ValueAnchor?,
		val root: ValueAnchor?,
		val acked: Long,
		val along: ValueAnchor?,
		val best: ValueAnchor?,
	)

	private var refusedCommit: CommitInputs? = null

	fun invalidateCommitMemo() {
		refusedCommit = null
	}

	private val publications = ArrayDeque<Publication>()

	var observedCursor: Int = -1
		private set

	private fun cursor(): Int? = cursorFrame?.invoke()?.also { observedCursor = it }

	fun begin() {
		if (searchConfig.localHorizonFrames > 0) horizonEnd = searchConfig.localHorizonFrames
	}

	fun canReach(anchor: ValueAnchor): Boolean =
		!committed || reachableRoot?.let { anchor.descendsFrom(it) } ?: true

	fun advanceExecutedRoot(): ValueAnchor? {
		val raw = cursor() ?: return null
		maybeRollback()
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE

		while (publications.size > 1 && publications[1].sequence <= acked) {
			publications.removeFirst()
		}
		val running = publications.firstOrNull()?.takeIf { it.sequence <= acked } ?: return null

		val executing = raw.coerceAtMost(running.brakeAnchor.elapsed)

		if (executing > running.anchor.elapsed) {

			if (reachableRoot !== running.brakeAnchor) {
				probe.braked(
					running.anchor.elapsed, executing,
					frontier.openSize, frontier.parkedSize, frontier.deepestElapsed,
				)
				reRootOnto(running.brakeAnchor)
				frontier.reopen(running.brakeAnchor)
				return running.brakeAnchor
			}
			return null
		}

		var deepest: ValueAnchor? = null
		var node: ValueAnchor? = running.anchor
		while (node != null) {
			if (node.elapsed <= executing) {
				deepest = node
				break
			}
			node = node.parent
		}
		val root = deepest ?: return null
		val current = reachableRoot
		if (current == null || (root !== current && root.descendsFrom(current))) {
			reRootOnto(root)
		}
		return null
	}

	fun publishPrefix(anchor: ValueAnchor) {
		if (onSafePrefix == null) return

		val remaining = field.guide(anchor.stance)
		if (!remaining.isFinite() || remaining <= searchConfig.finishValueTicks) return

		val cursor = cursor()
		val running = publishedTip

		val pressured = running != null && cursor != null &&
				running.elapsed - cursor <= searchConfig.horizonRunwayFrames

		fun refused(reason: () -> String) {
			publishRefusals++
			if (pressured) probe.publishRefused(reason, anchor.elapsed, running.elapsed, cursor)
		}

		if (anchor.elapsed > publicationCap(cursor ?: -1)) return refused { "cap" }

		if (!field.guide(anchor.stance, SpeedClass.STOPPED).isFinite()) return refused { "no-rest-continuation" }

		incumbentAnchor()?.let { incumbent ->
			if (!incumbent.descendsFrom(anchor)) return refused { "off-incumbent" }
		}
		if (running == null) {
			if (anchor.elapsed < searchConfig.safePrefixFrames) return
			if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return

			if (expansionCount() < searchConfig.minCommitExpansions) return

			if (remaining <= FIRST_PUBLISH_MIN_REMAINING_TICKS) return
		} else {
			if (!ackedUpToDate()) return refused { "unacked" }
			if (anchor.elapsed < running.elapsed + searchConfig.horizonCommitFrames) return refused { "short-extension" }
			if (expansionCount() - expansionsAtPublish < searchConfig.minCommitExpansions) return refused { "work-floor" }
			val executing = cursor ?: return
			if (running.elapsed - executing > searchConfig.horizonRunwayFrames) return
			if (!publishableOver(running, anchor, executing)) return refused { "not-publishable-over" }
		}
		if (!publishSolution(anchor)) refused { "certification" }
	}

	private fun publicationCap(executing: Int): Int =
		maxOf(executing, 0) + searchConfig.horizonCommitFrames * PUBLISH_RUNWAY_CHUNKS

	fun commitFromCandidates(urgent: Boolean, along: ValueAnchor? = null): Boolean {
		if (onSafePrefix == null) return false
		val root = publishedTip

		if (!urgent && expansionCount() - expansionsAtPublish < searchConfig.minCommitExpansions) {
			return false
		}
		if (root != null && !ackedUpToDate()) return false

		val executing = cursor() ?: -1
		val floor = maxOf(reachableRoot?.elapsed ?: 0, executing)

		val leaf = along?.takeIf { it.elapsed > floor }
		val best = leaf?.let { Frontier.OpenEntry(0.0, 0.0, it) }
			?: (if (frontier.hasParked) frontier.parkedEntries.minByOrNull { it.order }
			else frontier.bestLiveEntryDeeperThan(floor))
			?: return false

		val inputs = CommitInputs(
			tip = root,
			root = reachableRoot,
			acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE,
			along = leaf,
			best = best.anchor,
		)
		refusedCommit?.let { seen ->
			if (seen.tip === inputs.tip && seen.root === inputs.root &&
				seen.acked == inputs.acked && seen.along === inputs.along &&
				seen.best === inputs.best
			) {
				commitSuppressed++
				return false
			}
		}
		commitAttempts++

		if (root != null) {
			val progress = field.guide(root.stance, SpeedClass.of(root.speed)) -
					field.guide(best.anchor.stance, SpeedClass.of(best.anchor.speed))
			if (progress < MIN_COMMIT_PROGRESS_TICKS) {
				publishRefusals++
				probe.publishRefused({ "commit-progress" }, best.anchor.elapsed, root.elapsed, executing)
				refusedCommit = inputs
				return false
			}
		}

		val cap = publicationCap(executing)
		val line = lineFrom(reachableRoot, best.anchor).filter { it.elapsed > floor }
		var overRefused = 0
		var certifyRefused = 0
		var finishSkipped = 0
		fun tryPublish(candidate: ValueAnchor): Boolean {
			if (root == null && field.guide(candidate.stance) <= searchConfig.finishValueTicks) {
				finishSkipped++
				return false
			}
			if (root != null && !publishableOver(root, candidate, executing)) {
				overRefused++
				publishRefusals++
				probe.publishRefused({ "cand-$lastRefusalClause" }, candidate.elapsed, root.elapsed, executing)
				return false
			}
			if (publishSolution(candidate)) return true
			certifyRefused++
			return false
		}

		for (candidate in line.filter { it.elapsed <= cap }.sortedByDescending { it.elapsed }) {
			if (tryPublish(candidate)) return true
		}
		for (candidate in line.filter { it.elapsed > cap }.sortedBy { it.elapsed }) {
			if (tryPublish(candidate)) return true
		}
		publishRefusals++
		probe.publishRefused(
			{ "commit-line-refused(line=${line.size},over=$overRefused,certify=$certifyRefused,finish=$finishSkipped)" },
			best.anchor.elapsed, root?.elapsed ?: -1, executing,
		)
		refusedCommit = inputs
		return false
	}

	fun extendHorizon(): Boolean {
		if (searchConfig.localHorizonFrames <= 0) return false
		if (!frontier.hasParked) return false
		val root = reachableRoot ?: return false
		val before = frontier.openSize
		horizonEnd += searchConfig.localHorizonFrames
		frontier.repartition(root, horizonEnd)
		return frontier.openSize > before
	}

	private fun publishableOver(tip: ValueAnchor, candidate: ValueAnchor, executing: Int): Boolean {
		if (executing >= 0) {
			val divergence = executionDivergence(candidate)
			if (divergence < executing + PUBLISH_DIVERGENCE_MARGIN_FRAMES) {
				lastRefusalClause = if (divergence < executing) "divergence-dead" else "divergence-margin"
				return false
			}
		}
		if (candidate.descendsFrom(tip)) {
			if (candidate.elapsed <= tip.elapsed) {
				lastRefusalClause = "descendant-not-deeper"
				return false
			}
			return true
		}

		if (tip.descendsFrom(candidate)) {
			lastRefusalClause = "ancestor-rollback"
			return false
		}
		if (executing >= 0 && candidate.elapsed < executing + searchConfig.horizonCommitFrames) {
			lastRefusalClause = "swap-short-runway"
			return false
		}

		if (tipInvalidated) return true
		val candidateArrival = arrivalEstimate(candidate)
		val stale = ((expansionCount() - expansionsAtPublish - STALE_TIP_FLOOR_EXPANSIONS)
			.toDouble() / STALE_TIP_RAMP_EXPANSIONS).coerceIn(0.0, 1.0) * STALE_TIP_MAX_TICKS

		val tipArrival = tip.elapsed + field.guide(tip.stance, SpeedClass.of(tip.speed)) + stale +
				Solution.COLLISION_FRAME_PENALTY * tip.collisionEvents
		if (candidateArrival + REFINEMENT_GAIN_TICKS > tipArrival) {
			lastRefusalClause = "backtrack-gain"
			return false
		}
		return true
	}

	private var lastRefusalClause: String = "-"

	private fun arrivalEstimate(anchor: ValueAnchor): Double =
		anchor.elapsed + field.guide(anchor.stance, SpeedClass.of(anchor.speed)) +
				Solution.COLLISION_FRAME_PENALTY * anchor.collisionEvents

	private var publishedFinalScore = Int.MAX_VALUE

	fun publishFinished(solution: Solution): Boolean {
		if (onSafePrefix == null) return false
		if (publications.isEmpty()) return false
		if (solution.score >= publishedFinalScore) return false
		if (!adoptable(solution.anchor)) return false
		if (!ackedUpToDate()) return false
		val certified = certify(solution) as? MotionPlanResult.Success ?: return false
		val terminal = terminalAnchorOf(solution, certified)
		publishedTip = terminal
		expansionsAtPublish = expansionCount()
		refusedCommit = null
		publishedSequence++
		publications += Publication(
			publishedSequence, terminal, terminal,
			certified.frameDependencies, certified.planSegments, certified.rollout.initialState,
		)
		while (publications.size > MAX_TRACKED_PUBLICATIONS) publications.removeFirst()
		publishedFinalScore = solution.score
		tipInvalidated = false
		onSafePrefix(certified)
		return true
	}

	private fun terminalAnchorOf(solution: Solution, certified: MotionPlanResult.Success): ValueAnchor {
		val frames = certified.rollout.frames
		val terminal = frames.last().state
		return ValueAnchor(
			state = terminal,
			stance = ValueFieldAnchorSearch.stanceOf(terminal),
			elapsed = frames.size,
			collisionEvents = solution.collisionEvents,
			launchMargin = solution.launchMargin,
			inputSwitches = solution.anchor.inputSwitches,
			parent = solution.anchor,
			inputs = frames.drop(solution.anchor.elapsed).map { it.input },
			boundary = frames.size,
		).also { if (probe.treeEnabled || probe.candidatesEnabled) it.trace = ValueAnchor.traceOf(frames.drop(solution.anchor.elapsed)) }
	}

	private fun publishSolution(anchor: ValueAnchor): Boolean {
		if (onSafePrefix == null) return false
		val braked = brakeToStop(anchor) ?: return false
		var certified = certify(braked) as? MotionPlanResult.Success ?: return false
		val tip = publishedTip
		val running = publications.lastOrNull()
		if (tip != null && running != null) {
			certified = certified.copy(
				arrivalTicksEstimate = arrivalEstimate(anchor),
				comparedRunningArrivalTicks = arrivalEstimate(tip),
				comparedRunningSequence = running.sequence,
			)
		}
		publishedTip = anchor
		expansionsAtPublish = expansionCount()
		refusedCommit = null
		publishedSequence++
		publications += Publication(
			sequence = publishedSequence,
			anchor = anchor,
			brakeAnchor = brakeAnchorOf(anchor, certified),
			frameDependencies = certified.frameDependencies,
			planSegments = certified.planSegments,
			initialState = certified.rollout.initialState,
		)
		tipInvalidated = false
		while (publications.size > MAX_TRACKED_PUBLICATIONS) publications.removeFirst()
		onSafePrefix(certified)
		return true
	}

	private fun brakeAnchorOf(anchor: ValueAnchor, certified: MotionPlanResult.Success): ValueAnchor {
		val frames = certified.rollout.frames
		val tail = frames.drop(anchor.elapsed)
		val terminal = frames.last().state
		return ValueAnchor(
			state = terminal,
			stance = ValueFieldAnchorSearch.stanceOf(terminal),
			elapsed = frames.size,
			collisionEvents = anchor.collisionEvents,
			launchMargin = anchor.launchMargin,
			inputSwitches = anchor.inputSwitches,
			parent = anchor,
			inputs = tail.map { it.input },
			boundary = frames.size,
		).also { if (probe.treeEnabled || probe.candidatesEnabled) it.trace = ValueAnchor.traceOf(tail) }
	}

	private fun maybeRollback() {
		if (ackedUpToDate()) {
			pendingAckSinceMillis = null
			return
		}
		val now = clock.elapsedMillis()
		val since = pendingAckSinceMillis
		if (since == null) {
			pendingAckSinceMillis = now
			return
		}
		if (now - since < ACK_ROLLBACK_MILLIS) return
		pendingAckSinceMillis = null
		val acked = adoptedSequence?.invoke() ?: return
		while (publications.isNotEmpty() && publications.last().sequence > acked) {
			publications.removeLast()
		}
		publishedTip = publications.lastOrNull()?.anchor
	}

	private fun ackedUpToDate(): Boolean {
		val acked = adoptedSequence?.invoke() ?: return true
		val newest = publications.lastOrNull() ?: return true
		return acked >= newest.sequence
	}

	private var runningLineFor: ValueAnchor? = null
	private val runningLine = HashSet<ValueAnchor>()

	private fun runningLine(running: ValueAnchor): Set<ValueAnchor> {
		if (runningLineFor !== running) {
			runningLine.clear()
			var node: ValueAnchor? = running
			while (node != null) {
				runningLine += node
				node = node.parent
			}
			runningLineFor = running
		}
		return runningLine
	}

	private fun divergenceElapsed(running: ValueAnchor, candidate: ValueAnchor): Int {
		val line = runningLine(running)
		var walk: ValueAnchor? = candidate
		while (walk != null) {
			if (walk in line) return walk.elapsed
			walk = walk.parent
		}
		return 0
	}

	fun executionDivergence(anchor: ValueAnchor): Int {
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE
		val running = publications.lastOrNull { it.sequence <= acked } ?: return Int.MAX_VALUE
		if (anchor.divergenceSequence == running.sequence) return anchor.divergenceElapsed
		val divergence = if (anchor.descendsFrom(running.brakeAnchor)) running.brakeAnchor.elapsed
		else divergenceElapsed(running.anchor, anchor)
		anchor.divergenceElapsed = divergence
		anchor.divergenceSequence = running.sequence
		return divergence
	}

	fun adoptable(anchor: ValueAnchor): Boolean =
		forkLife(anchor) >= PUBLISH_DIVERGENCE_MARGIN_FRAMES

	fun finalWindowClosing(anchor: ValueAnchor): Boolean {
		val executing = cursor() ?: return false
		val divergence = executionDivergence(anchor)
		return divergence != Int.MAX_VALUE && divergence - executing < PUBLISH_DIVERGENCE_MARGIN_FRAMES + FINAL_FORK_HEADROOM_FRAMES
	}

	fun forkLife(anchor: ValueAnchor): Int {
		val executing = cursor() ?: return Int.MAX_VALUE
		val divergence = executionDivergence(anchor)
		if (divergence >= anchor.elapsed) return Int.MAX_VALUE
		return divergence - executing
	}

	fun latestBrakeContinuation(): ValueAnchor? {
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE
		val publication = publications.lastOrNull { it.sequence <= acked } ?: return null
		val brake = publication.brakeAnchor
		return brake.continuation()
	}

	fun junctionContinuation(tried: MutableSet<Int>): ValueAnchor? {
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE
		val running = publications.lastOrNull { it.sequence <= acked } ?: return null
		val initial = running.initialState ?: return null
		val graph = PlanGraph.of(running.planSegments, initial) ?: return null
		val cursor = maxOf(observedCursor, 0)
		var junction: PlanJunction? = null
		for (candidate in graph.junctions.asReversed()) {
			if (candidate.index == 0 || !candidate.rejoinable) continue
			if (candidate.frame >= running.anchor.elapsed) continue
			if (candidate.frame < cursor + PUBLISH_DIVERGENCE_MARGIN_FRAMES) break
			if (tried.add(candidate.frame)) {
				junction = candidate; break
			}
		}
		val at = junction ?: return null
		var cut: ValueAnchor? = running.anchor
		while (cut != null && cut.elapsed > at.frame) cut = cut.parent
		if (cut == null || cut.elapsed != at.frame) return null
		tipInvalidated = true
		junctionRestarts++
		return cut.continuation()
	}

	var junctionRestarts = 0
		private set

	fun movingTipContinuation(): ValueAnchor? {
		val tip = publishedTip ?: return null
		return tip.continuation()
	}

	fun reRootForRestart(anchor: ValueAnchor) {
		reRootOnto(anchor)
		frontier.reopen(anchor)
	}

	private var tipInvalidated = false

	var repairs = 0
		private set

	fun repairFor(mutations: Set<com.lambda.pathing.core.PathingSection>): ValueAnchor? {
		if (mutations.isEmpty()) return null
		val running = publications.lastOrNull() ?: return null
		val initial = running.initialState ?: return null
		val first = running.frameDependencies.indexOfFirst { reads ->
			reads.any { com.lambda.pathing.core.PathingSection.containing(it) in mutations }
		}
		if (first < 0) return null
		val graph = PlanGraph.of(running.planSegments, initial) ?: return null
		val cursor = maxOf(observedCursor, 0)
		val junction = graph.repairJunction(first, cursor, PUBLISH_DIVERGENCE_MARGIN_FRAMES) ?: return null
		var cut: ValueAnchor? = running.anchor
		while (cut != null && cut.elapsed > junction.frame) cut = cut.parent
		if (cut == null || cut.elapsed != junction.frame) return null
		frontier.dropDescendants(cut)
		reRootOnto(cut)
		frontier.reopen(cut)
		tipInvalidated = true
		refusedCommit = null
		repairs++
		return cut
	}

	fun publishCandidates() {
		if (onSafePrefix == null || !probe.candidatesEnabled) return
		val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries
		if (pool.isEmpty()) return
		val best = pool.minByOrNull { it.order }
		val shown = pool.sortedBy { it.order }.take(MAX_SHOWN_CANDIDATES)

		val spine = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ValueAnchor, Boolean>())
		var node = publishedTip
		while (node != null) {
			spine += node
			node = node.parent
		}
		probe.candidates(
			shown.map { entry ->
				val branch = ArrayList<ValueAnchor>()
				var cursor: ValueAnchor? = entry.anchor
				while (cursor != null && cursor !in spine) {
					branch += cursor
					cursor = cursor.parent
				}
				branch.reverse()
				val points = ArrayList<Vec3d>()
				(cursor ?: branch.firstOrNull()?.parent)?.let { points += it.state.position }
				branch.forEach { anchor ->
					points += anchor.trace
					points += anchor.state.position
				}
				CandidatePath(points, entry === best)
			}
		)
	}

	companion object {

		const val PUBLISH_DIVERGENCE_MARGIN_FRAMES = 3

		const val FINAL_FORK_HEADROOM_FRAMES = 12

		private const val MIN_COMMIT_PROGRESS_TICKS = 1.0

		const val REFINEMENT_GAIN_TICKS = SWAP_FLOOR_GAIN_TICKS

		const val STALE_TIP_FLOOR_EXPANSIONS = 2000

		const val STALE_TIP_RAMP_EXPANSIONS = 4000

		const val STALE_TIP_MAX_TICKS = 30.0

		const val FIRST_PUBLISH_MIN_REMAINING_TICKS = 30.0

		const val PUBLISH_RUNWAY_CHUNKS = 3

		const val ACK_ROLLBACK_MILLIS = 150L

		const val MAX_SHOWN_CANDIDATES = 12

		const val MAX_TRACKED_PUBLICATIONS = 8

	}

	private fun lineFrom(root: ValueAnchor?, leaf: ValueAnchor): List<ValueAnchor> {
		val chain = ArrayList<ValueAnchor>()
		var node: ValueAnchor? = leaf
		while (node != null && node !== root && node.parent != null) {
			chain += node
			node = node.parent
		}
		return chain.asReversed()
	}

	private fun reRootOnto(anchor: ValueAnchor) {
		reachableRoot = anchor
		frontier.retainDescendants(anchor)

		frontier.reopen(anchor)

		if (searchConfig.localHorizonFrames > 0) {
			horizonEnd = anchor.elapsed + searchConfig.localHorizonFrames
		}

		frontier.repartition(anchor, horizonEnd)

		committed = true
	}
}
