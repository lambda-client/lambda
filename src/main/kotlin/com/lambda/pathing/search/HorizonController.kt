package com.lambda.pathing.search

import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import net.minecraft.util.math.Vec3d

internal class Publication(
	val sequence: Long,
	val anchor: ValueAnchor,
	val brakeAnchor: ValueAnchor,
	/** What the certified tape read, per frame, and the decisions it compiled from: the repair inputs. */
	val frameDependencies: List<Set<com.lambda.pathing.core.VoxelPos>> = emptyList(),
	val planSegments: List<PlanSegment> = emptyList(),
	val initialState: com.lambda.pathing.physics.MovementSimulationState? = null,
)

/**
 * Publication and commitment are decoupled. Publishing hands the executor a longer
 * certified tape; it never prunes the frontier, so sibling branches stay alive and the
 * search can back out of a bad movement as long as the body has not pressed its frames.
 * The only irrevocable commitment is execution itself: [advanceExecutedRoot] re-roots
 * the frontier onto the deepest published anchor whose frames the body has consumed.
 */
internal class HorizonController(
	private val searchConfig: ValueFieldSearchConfig,
	private val field: ValueField,
	private val frontier: Frontier,
	private val clock: SearchClock,
	private val cursorFrame: (() -> Int?)?,
	private val adoptedSequence: (() -> Long)?,
	private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
	private val expansionCount: () -> Int,
	/**
	 * The anchor of the best full solution to the current goal, when one stands. While
	 * it does, publications follow its lineage: the tape is a prefix of the best known
	 * plan, never whatever anchored first under runway pressure.
	 */
	private val incumbentAnchor: () -> ValueAnchor?,
	private val brakeToStop: (ValueAnchor) -> Solution?,
	private val certify: (Solution) -> MotionPlanResult,
	private val probe: SearchProbe,
) {
	var publishedTip: ValueAnchor? = null
		private set

	// Kept for pacing compatibility: the anchor of the newest publication.
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

	/**
	 * Commit attempts, those suppressed by the refused-commit memo, and publication
	 * refusals. The memo key is the running tip, root, ack state, leaf and best candidate;
	 * the cursor is deliberately excluded (divergence refusals are monotone in it).
	 * See docs/decisions/publication-protocol.md.
	 */
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

	/** Guide values changed under the memo's feet: rescore, route change, field growth. */
	fun invalidateCommitMemo() {
		refusedCommit = null
	}

	private val publications = ArrayDeque<Publication>()

	/**
	 * The last executed frame read from [cursorFrame]. Reading the cursor has side effects
	 * (the manager delivers pending publications), so reporting reads this instead.
	 */
	var observedCursor: Int = -1
		private set

	private fun cursor(): Int? = cursorFrame?.invoke()?.also { observedCursor = it }

	fun begin() {
		if (searchConfig.localHorizonFrames > 0) horizonEnd = searchConfig.localHorizonFrames
	}

	fun canReach(anchor: ValueAnchor): Boolean =
		!committed || reachableRoot?.let { anchor.descendsFrom(it) } ?: true

	/**
	 * Advance the executed root to the deepest acknowledged anchor whose frames the body
	 * has pressed. Returns the settled brake anchor when the cursor has entered the
	 * published brake tail (the brake is now irrevocable), so the caller can continue
	 * the search from rest.
	 */
	fun advanceExecutedRoot(): ValueAnchor? {
		val raw = cursor() ?: return null
		maybeRollback()
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE

		// Drop publications the executor superseded by acknowledging a newer one.
		while (publications.size > 1 && publications[1].sequence <= acked) {
			publications.removeFirst()
		}
		val running = publications.firstOrNull()?.takeIf { it.sequence <= acked } ?: return null

		// The executor can never press frames beyond the acked tape; a source that
		// claims more (a wall-clock harness) is clamped to the hold contract.
		val executing = raw.coerceAtMost(running.brakeAnchor.elapsed)

		if (executing > running.anchor.elapsed) {
			// The body is inside the brake tail: the stop is irrevocable.
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

		// Re-root onto the deepest published-lineage anchor the body has executed.
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

	fun publishPrefix(anchor: ValueAnchor, expansions: Int) {
		val publish = onSafePrefix ?: return

		val remaining = field.guide(anchor.stance)
		if (!remaining.isFinite() || remaining <= searchConfig.finishValueTicks) return

		val cursor = cursor()
		val running = publishedTip
		// Refusals only matter in the frames before a potential stall; report them there.
		val pressured = running != null && cursor != null &&
				running.elapsed - cursor <= searchConfig.horizonRunwayFrames

		fun refused(reason: () -> String) {
			publishRefusals++
			if (pressured) probe.publishRefused(reason, anchor.elapsed, running.elapsed, cursor)
		}

		if (anchor.elapsed > publicationCap(cursor ?: -1)) return refused { "cap" }
		// A published tape brakes to rest at its tip; a rest with no standing-start
		// continuation strands the body (a one-block pillar before a momentum-only
		// catch). Publish an earlier or later anchor instead.
		if (!field.guide(anchor.stance, SpeedClass.STOPPED).isFinite()) return refused { "no-rest-continuation" }
		// With a full solution standing only its own prefix is publishable here; branch
		// changes go through commitFromCandidates, which weighs them.
		incumbentAnchor()?.let { incumbent ->
			if (!incumbent.descendsFrom(anchor)) return refused { "off-incumbent" }
		}
		if (running == null) {
			if (anchor.elapsed < searchConfig.safePrefixFrames) return
			if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return

			if (expansionCount() < searchConfig.minCommitExpansions) return
			// A course this short finalizes whole; a partial first would only hold at its brake.
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

	/**
	 * The deepest frame a publication may reach: [PUBLISH_RUNWAY_CHUNKS] commit chunks past
	 * the body, never the deepest certifiable anchor, so the near future stays open to
	 * refinement. See docs/decisions/publication-protocol.md.
	 */
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

		// Only the best-ranked candidate, never a list; see docs/decisions/publication-protocol.md.
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
		// Deepest-first inside the cap, then the smallest overshoot past it: brakable
		// anchors are sparse on a jump chain and refusing stalls the body.
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

	/**
	 * Push the local horizon out so anchors parked beyond it can be expanded again.
	 * Returns false when nothing moved, so the caller stops rather than spins.
	 */
	fun extendHorizon(): Boolean {
		if (searchConfig.localHorizonFrames <= 0) return false
		if (!frontier.hasParked) return false
		val root = reachableRoot ?: return false
		val before = frontier.openSize
		horizonEnd += searchConfig.localHorizonFrames
		frontier.repartition(root, horizonEnd)
		return frontier.openSize > before
	}

	/**
	 * The swap gate: a descendant must extend past the tip; any other branch must fork
	 * ahead of the cursor (plus in-flight margin), not be an ancestor, leave a commit
	 * chunk of runway, and beat the tip's estimated arrival by the swap floor.
	 * Clause evidence: docs/decisions/publication-protocol.md.
	 */
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
		// An ancestor of the tip is a rollback, not a swap; the arrival gate cannot catch it.
		if (tip.descendsFrom(candidate)) {
			lastRefusalClause = "ancestor-rollback"
			return false
		}
		if (executing >= 0 && candidate.elapsed < executing + searchConfig.horizonCommitFrames) {
			lastRefusalClause = "swap-short-runway"
			return false
		}
		// Estimated arrival, collisions priced as Solution.score prices them; the tip's
		// claim erodes toward a brake stop while it fails to extend (stale-tip discount).
		// A tip whose tail the world invalidated has no arrival claim left to beat.
		if (tipInvalidated) return true
		val candidateArrival = arrivalEstimate(candidate)
		val stale = ((expansionCount() - expansionsAtPublish - STALE_TIP_FLOOR_EXPANSIONS)
			.toDouble() / STALE_TIP_RAMP_EXPANSIONS).coerceIn(0.0, 1.0) * STALE_TIP_MAX_TICKS
		// Not arrivalEstimate(tip) + stale: re-associating the sum is not bit-identical.
		val tipArrival = tip.elapsed + field.guide(tip.stance, SpeedClass.of(tip.speed)) + stale +
				Solution.COLLISION_FRAME_PENALTY * tip.collisionEvents
		if (candidateArrival + REFINEMENT_GAIN_TICKS > tipArrival) {
			lastRefusalClause = "backtrack-gain"
			return false
		}
		return true
	}

	/** Diagnostic only: the clause the last publishableOver refusal took. */
	private var lastRefusalClause: String = "-"

	/** Class-conditioned: a moving tip's claim and a stopped candidate's are priced as the bodies they are. */
	private fun arrivalEstimate(anchor: ValueAnchor): Double =
		anchor.elapsed + field.guide(anchor.stance, SpeedClass.of(anchor.speed)) +
				Solution.COLLISION_FRAME_PENALTY * anchor.collisionEvents

	private var publishedFinalScore = Int.MAX_VALUE

	/**
	 * Publish a sealed finish, terminal tail included, as an ordinary running publication
	 * so the session survives to refine it; a later shorter seal replaces it through the
	 * same gate. Score-gated, and only ever an upgrade of a walk already in motion.
	 * See docs/decisions/publication-protocol.md.
	 */
	fun publishFinished(solution: Solution): Boolean {
		val publish = onSafePrefix ?: return false
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
		publish(certified)
		return true
	}

	/**
	 * The finished tape's end as an anchor: grounded, stopped, its prefix the whole
	 * tape. Doubles as its own brake anchor -- the tape already ends in the certified
	 * stop, so the hold contract is the tape end itself.
	 */
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
		val publish = onSafePrefix ?: return false
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
		publish(certified)
		return true
	}

	/**
	 * The settled continuation point of a published tape: the brake tail as an anchor.
	 * If the body ends up executing the whole tape, the search continues from here --
	 * a grounded, closed-cycle rest state whose prefix is the entire published tape.
	 */
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

	/**
	 * A publication unacknowledged for [ACK_ROLLBACK_MILLIS] was rejected or lost: drop the
	 * unacked tail and fall back to the acknowledged tape.
	 */
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

	/** Memoised ancestor set of the running tip, for [divergenceElapsed]. */
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

	/**
	 * The frame at which [anchor]'s tape departs from the acked running tape.
	 * Frames before this index are byte-identical to what the executor is replaying,
	 * so a solution is execution-compatible iff this is at or past the cursor.
	 */
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

	/**
	 * Whether [anchor] can still lead to an adoptable publication: its fork must be at
	 * least the divergence margin ahead of the cursor. Anchors on the acked tape stay live.
	 */
	fun adoptable(anchor: ValueAnchor): Boolean =
		forkLife(anchor) >= PUBLISH_DIVERGENCE_MARGIN_FRAMES

	/**
	 * Whether a finish sealed at [anchor] must be published now to remain adoptable: its
	 * fork is within margin plus [FINAL_FORK_HEADROOM_FRAMES] of the cursor. Unlike
	 * [forkLife], an on-tape anchor is not exempt -- a fork published at an executed
	 * ancestor dies the same way. See docs/decisions/publication-protocol.md.
	 */
	fun finalWindowClosing(anchor: ValueAnchor): Boolean {
		val executing = cursor() ?: return false
		val divergence = executionDivergence(anchor)
		return divergence != Int.MAX_VALUE && divergence - executing < PUBLISH_DIVERGENCE_MARGIN_FRAMES + FINAL_FORK_HEADROOM_FRAMES
	}

	/**
	 * Frames until execution forecloses [anchor]'s branch: how far its divergence point
	 * sits ahead of the cursor. Anchors on the acked tape (divergence at their own
	 * elapsed) and sessions without a cursor report unbounded life.
	 */
	fun forkLife(anchor: ValueAnchor): Int {
		val executing = cursor() ?: return Int.MAX_VALUE
		val divergence = executionDivergence(anchor)
		if (divergence >= anchor.elapsed) return Int.MAX_VALUE
		if (divergence == Int.MAX_VALUE) return Int.MAX_VALUE
		return divergence - executing
	}

	/**
	 * A fresh settled continuation anchor at the newest acked publication's terminal:
	 * the seed for an in-session search restart when the frontier genuinely drains.
	 */
	fun latestBrakeContinuation(): ValueAnchor? {
		val acked = adoptedSequence?.invoke() ?: Long.MAX_VALUE
		val publication = publications.lastOrNull { it.sequence <= acked } ?: return null
		val brake = publication.brakeAnchor
		return ValueAnchor(
			state = brake.state,
			stance = brake.stance,
			elapsed = brake.elapsed,
			collisionEvents = brake.collisionEvents,
			launchMargin = brake.launchMargin,
			inputSwitches = brake.inputSwitches,
			parent = brake.parent,
			inputs = brake.inputs,
			boundary = brake.boundary,
		).also {
			it.via = brake.via
			it.decision = brake.decision
			it.points = brake.points
			it.trace = brake.trace
		}
	}

	/**
	 * A junction restart: the running tape's last rejoinable junction before its tip that
	 * the body has not reached and that [tried] does not hold yet, as a fresh continuation.
	 * Used when the tip itself has dead-ended: re-rooting one junction back lets the search
	 * take a different line into the obstacle while the body keeps replaying the prefix.
	 * Voids the tip's arrival claim so the new line can publish over it.
	 */
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
		return ValueAnchor(
			state = cut.state,
			stance = cut.stance,
			elapsed = cut.elapsed,
			collisionEvents = cut.collisionEvents,
			launchMargin = cut.launchMargin,
			inputSwitches = cut.inputSwitches,
			parent = cut.parent,
			inputs = cut.inputs,
			boundary = cut.boundary,
		).also {
			it.via = cut.via
			it.decision = cut.decision
			it.points = cut.points
			it.trace = cut.trace
		}
	}

	var junctionRestarts = 0
		private set

	/**
	 * The running tape's tip as a fresh continuation, still moving: same parent and inputs
	 * (so solutions through it replay identically), no attempts recorded, no brake implied.
	 * Tried before [latestBrakeContinuation]; see docs/decisions/session-loop.md.
	 */
	fun movingTipContinuation(): ValueAnchor? {
		val tip = publishedTip ?: return null
		return ValueAnchor(
			state = tip.state,
			stance = tip.stance,
			elapsed = tip.elapsed,
			collisionEvents = tip.collisionEvents,
			launchMargin = tip.launchMargin,
			inputSwitches = tip.inputSwitches,
			parent = tip.parent,
			inputs = tip.inputs,
			boundary = tip.boundary,
		).also {
			it.via = tip.via
			it.decision = tip.decision
			it.points = tip.points
			it.trace = tip.trace
		}
	}

	fun reRootForRestart(anchor: ValueAnchor) {
		reRootOnto(anchor)
		frontier.reopen(anchor)
	}

	/** Set when the running tape's tail was invalidated by a world change; cleared by the next publication. */
	private var tipInvalidated = false

	var repairs = 0
		private set

	/**
	 * The DAG repair: when [mutations] touch what the running tape's tail read, cut the
	 * plan at [PlanGraph.repairJunction] and re-root the search there, discarding every
	 * anchor below the cut (their rollouts read the old world). The executor keeps
	 * replaying the spine up to the cut; the tape published from here is the alternate
	 * that rejoins nothing and replaces the tail. Returns the cut anchor, or null when
	 * nothing published depends on the change or no cut is ahead of the body.
	 * See docs/decisions/publication-protocol.md (local repair).
	 */
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
		// The published spine, by identity: a candidate is drawn from where it forks off it.
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

		/** Frames a fork must sit ahead of the cursor: the in-flight publication margin. */
		const val PUBLISH_DIVERGENCE_MARGIN_FRAMES = 3

		/** Frames of fork life below which a sealed finish is published rather than polished. */
		const val FINAL_FORK_HEADROOM_FRAMES = 12

		private const val MIN_COMMIT_PROGRESS_TICKS = 1.0

		/** The swap floor; see docs/decisions/swap-floor.md. */
		const val REFINEMENT_GAIN_TICKS = SWAP_FLOOR_GAIN_TICKS

		/** Stale-tip discount ramp, in expansions since the last publication; see docs/decisions/publication-protocol.md. */
		const val STALE_TIP_FLOOR_EXPANSIONS = 2000

		const val STALE_TIP_RAMP_EXPANSIONS = 4000

		/** Full staleness discounts the tip's claim by about a brake tail. */
		const val STALE_TIP_MAX_TICKS = 30.0

		const val FIRST_PUBLISH_MIN_REMAINING_TICKS = 30.0

		/** Published runway in commit chunks; see [publicationCap]. */
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
