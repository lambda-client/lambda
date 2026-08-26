package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import net.minecraft.util.math.Vec3d

internal interface CommitSupport {
    val expansionCount: Int

    fun brakeToStop(anchor: ValueAnchor): Solution?

    fun certify(solution: Solution): MotionPlanResult
}

internal class Publication(
    val sequence: Long,
    val anchor: ValueAnchor,
    val brakeAnchor: ValueAnchor,
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
    private val field: CoarseValueField,
    private val frontier: Frontier,
    private val clock: SearchClock,
    private val cursorFrame: (() -> Int?)?,
    private val adoptedSequence: (() -> Long)?,
    private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
    private val support: CommitSupport,
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

    private val publications = ArrayDeque<Publication>()

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
        val raw = cursorFrame?.invoke() ?: return null
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

        val running = publishedTip
        if (running == null) {
            if (anchor.elapsed < searchConfig.safePrefixFrames) return
            if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return

            if (support.expansionCount < searchConfig.minCommitExpansions) return
            // On a course this short the full solution lands within the same breath;
            // publishing a partial first only sets up a divergence rejection and a
            // hold at its brake. Let the session finalize instead.
            if (remaining <= FIRST_PUBLISH_MIN_REMAINING_TICKS) return
        } else {
            if (!ackedUpToDate()) return
            if (anchor.elapsed < running.elapsed + searchConfig.horizonCommitFrames) return
            if (support.expansionCount - expansionsAtPublish < searchConfig.minCommitExpansions) return
            val executing = cursorFrame?.invoke() ?: return
            if (running.elapsed - executing > searchConfig.horizonRunwayFrames) return
            if (!publishableOver(running, anchor, executing)) return
        }
        publishSolution(anchor)
    }

    fun commitFromCandidates(urgent: Boolean, along: ValueAnchor? = null): Boolean {
        if (onSafePrefix == null) return false
        val root = publishedTip

        if (!urgent && support.expansionCount - expansionsAtPublish < searchConfig.minCommitExpansions) {
            return false
        }
        if (root != null && !ackedUpToDate()) return false

        val executing = cursorFrame?.invoke() ?: -1
        val floor = maxOf(reachableRoot?.elapsed ?: 0, executing)

        val leaf = along?.takeIf { it.elapsed > floor }
        val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries.filter {
            it.anchor.elapsed > floor
        }
        val best = leaf?.let { Frontier.OpenEntry(0.0, 0.0, it) }
            ?: pool.minByOrNull { it.order }
            ?: return false

        if (root != null) {
            val progress = field.guide(root.stance) - field.guide(best.anchor.stance)
            if (progress < MIN_COMMIT_PROGRESS_TICKS) return false
        }

        val line = lineFrom(reachableRoot, best.anchor).filter { it.elapsed > floor }
        for (candidate in line.sortedByDescending { it.elapsed }) {

            if (root == null && field.guide(candidate.stance) <= searchConfig.finishValueTicks) continue
            if (root != null && !publishableOver(root, candidate, executing)) continue
            if (publishSolution(candidate)) return true
        }
        return false
    }

    /**
     * A new publication must genuinely improve on the running tape: a descendant must
     * extend past the tip; any other branch is a deliberate backtrack, allowed only
     * when it departs ahead of the executed frames and gets meaningfully closer to
     * the goal than the tip -- otherwise siblings thrash the tape back and forth.
     */
    private fun publishableOver(tip: ValueAnchor, candidate: ValueAnchor, executing: Int): Boolean {
        // The cursor keeps advancing while a publication is in flight: without a
        // margin, a tape that diverges just ahead of the cursor arrives diverging
        // just behind it and is refused.
        if (executing >= 0 &&
            executionDivergence(candidate) < executing + PUBLISH_DIVERGENCE_MARGIN_FRAMES
        ) {
            return false
        }
        if (candidate.descendsFrom(tip)) return candidate.elapsed > tip.elapsed
        // A publication on another branch must improve the ESTIMATED ARRIVAL, not
        // just the coarse guide: comparing guides alone let the tape swap onto a
        // branch with less progress -- a physical loop the body then walks.
        val candidateArrival = candidate.elapsed + field.guide(candidate.stance)
        val tipArrival = tip.elapsed + field.guide(tip.stance)
        return candidateArrival + REFINEMENT_GAIN_TICKS <= tipArrival
    }

    private fun publishSolution(anchor: ValueAnchor): Boolean {
        val publish = onSafePrefix ?: return false
        val braked = support.brakeToStop(anchor) ?: return false
        val certified = support.certify(braked) as? MotionPlanResult.Success ?: return false
        publishedTip = anchor
        expansionsAtPublish = support.expansionCount
        publishedSequence++
        publications += Publication(
            sequence = publishedSequence,
            anchor = anchor,
            brakeAnchor = brakeAnchorOf(anchor, certified),
        )
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
        )
    }

    /**
     * A publication the executor has not acknowledged within the timeout was rejected
     * or lost -- most often the cursor advanced past its divergence point while it was
     * in flight. Drop the unacked tail and fall back to the acknowledged tape, so the
     * search publishes extensions of what the body is actually replaying instead of
     * jamming forever behind a tape that will never be installed.
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

    /** Elapsed frame at which [candidate]'s lineage departs from [running]'s. */
    private fun divergenceElapsed(running: ValueAnchor, candidate: ValueAnchor): Int {
        val runningLine = HashSet<ValueAnchor>()
        var node: ValueAnchor? = running
        while (node != null) {
            runningLine += node
            node = node.parent
        }
        var walk: ValueAnchor? = candidate
        while (walk != null) {
            if (walk in runningLine) return walk.elapsed
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
        if (anchor.descendsFrom(running.brakeAnchor)) return running.brakeAnchor.elapsed
        return divergenceElapsed(running.anchor, anchor)
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
        )
    }

    fun reRootForRestart(anchor: ValueAnchor) {
        reRootOnto(anchor)
        frontier.reopen(anchor)
    }

    fun publishCandidates() {
        if (onSafePrefix == null || !probe.candidatesEnabled) return
        val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries
        if (pool.isEmpty()) return
        val root = publishedTip
        val best = pool.minByOrNull { it.order }
        val shown = pool.sortedBy { it.order }.take(MAX_SHOWN_CANDIDATES)
        probe.candidates(
            shown.map { entry ->
                val points = ArrayList<Vec3d>()
                root?.let { points += it.state.position }
                lineFrom(root, entry.anchor).forEach { points += it.state.position }
                CandidatePath(points, entry === best)
            }
        )
    }

    private companion object {

        const val MIN_COMMIT_PROGRESS_TICKS = 1.0

        const val REFINEMENT_GAIN_TICKS = 3.0

        const val FIRST_PUBLISH_MIN_REMAINING_TICKS = 30.0

        const val ACK_ROLLBACK_MILLIS = 150L

        const val PUBLISH_DIVERGENCE_MARGIN_FRAMES = 3

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
