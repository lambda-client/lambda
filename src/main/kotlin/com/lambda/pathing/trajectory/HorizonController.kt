/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import net.minecraft.util.math.Vec3d

/**
 * Decides when to stop improving and lock motion in.
 *
 * Holds everything about the committed line -- how far it reaches, what the body may still
 * be steered onto, when the last commitment was made -- and nothing about how a tape is
 * certified. It asks the endgame for a brake and a certification through [brakeFrom] and
 * [certify]; whether the answer is safe is not its question.
 */
internal class HorizonController(
    private val searchConfig: ValueFieldSearchConfig,
    private val field: CoarseValueField,
    private val frontier: Frontier,
    private val clock: SearchClock,
    private val cursorFrame: (() -> Int?)?,
    private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
    private val expansions: () -> Int,
    private val incumbent: () -> Solution?,
    private val brakeFrom: (ValueAnchor) -> Solution?,
    private val certify: (Solution) -> MotionPlanResult,
) {
    /** The anchor whose brake was published; the end of committed motion. */
    var safeAnchor: ValueAnchor? = null
        private set

    /** Elapsed frames past which nothing is expanded; advances with each commitment. */
    var horizonEnd = Int.MAX_VALUE
        private set

    /** Whether the search has re-rooted onto the prefix the body is walking. */
    var committed = false
        private set

    /** What every live candidate must descend from, as far as the body has walked it. */
    var reachableRoot: ValueAnchor? = null
        private set

    /** Earliest certified *partial* plan: committed motion plus a stop. */
    var firstSafe: Solution? = null
        private set

    var firstSafeRollouts = 0
        private set

    private var expansionsAtCommit = 0

    fun begin() {
        if (searchConfig.localHorizonFrames > 0) horizonEnd = searchConfig.localHorizonFrames
    }

    /** Whether an anchor is still on a line the body can be steered onto. */
    fun canReach(anchor: ValueAnchor): Boolean =
        !committed || reachableRoot?.let { anchor.descendsFrom(it) } ?: true

    /**
     * The anytime unit: once enough motion is committed, find out whether the body
     * can simply stop there. A prefix that ends in a certified stop is safe to
     * execute on its own, so it can be published while the search carries on
     * improving what comes after it.
     *
     * Published at most once per search, and only once the search has been running
     * long enough that standing still is the worse option. Publishing re-roots the
     * search onto this line, so it is a commitment, not a hint.
     */
    fun publishPrefix(anchor: ValueAnchor, expansions: Int) {
        val publish = onSafePrefix ?: return
        // Inside the finish horizon the terminal sweep owns the approach, and braking
        // to a stop there is not a partial plan at all: it is a worse complete one.
        // Three of the four stop-and-replan legs in the live corpus were this -- a
        // prefix that braked to rest *past* the goal, so the body then had to plan a
        // second leg to walk back the block it had just overshot.
        val remaining = field.guide(anchor.stance)
        if (!remaining.isFinite() || remaining <= searchConfig.finishValueTicks) return

        // The bootstrap, and only the bootstrap: the body has to start moving before
        // there is a population of candidates to choose between. Every commitment after
        // this one is made by [commitFromCandidates], which compares the parked
        // candidates and locks in the beginning of whichever is best.
        //
        // Committing whichever anchor the search happened to admit -- which is all this
        // path can do -- means committing to a line nobody weighed against the
        // alternatives, and it produced single commitments of 266 frames on a route
        // whose considered ones were six to fourteen.
        val running = safeAnchor
        if (running == null) {
            if (anchor.elapsed < searchConfig.safePrefixFrames) return
            if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return
        } else {
            // Committing straight off an admitted anchor, without weighing it against
            // anything. A genuine last resort: it fires only when nothing has been
            // evaluated across the window at all, because a line nobody compared is
            // exactly what makes consecutive commitments disagree with each other.
            // Removing it entirely is worse -- the search then has nothing to commit
            // and the body stops -- but it must not compete with the weighed path.
            if (!searchConfig.commitContinuously) return
            if (frontier.hasParked) return
            if (!anchor.descendsFrom(running)) return
            if (anchor.elapsed < running.elapsed + searchConfig.horizonCommitFrames) return
            if (expansions() - expansionsAtCommit < searchConfig.minCommitExpansions) return
            val executing = cursorFrame?.invoke() ?: return
            if (running.elapsed - executing > searchConfig.horizonRunwayFrames) return
        }
        val braked = brakeFrom.invoke(anchor) ?: return
        val certified = certify.invoke(braked) as? MotionPlanResult.Success ?: return
        if (firstSafe == null) {
            firstSafe = braked
            firstSafeRollouts = expansions()
        }
        safeAnchor = anchor
        expansionsAtCommit = expansions()
        reRootOnto(anchor)
        publish(certified)
    }
    /**
     * Drops everything the body can no longer be steered onto.
     *
     * Done the moment a prefix is *published*, not when the executor starts running
     * it. Waiting for the cursor loses the race: on a staircase the complete plan was
     * found in the same tick the partial was installed, on a branch the body could no
     * longer join, and was refused. Re-rooting here means every solution the search
     * finds afterwards shares the walked tape by construction, so there is nothing
     * left for adoption to reject.
     */
    /**
     * Locks in the beginning of the best candidate, and only its beginning.
     *
     * Called when the body is about to run out of committed motion, or when every
     * candidate has reached the horizon and nothing can grow without more room. The
     * choice is made as late as possible on purpose: until this moment every candidate
     * is still being improved, and whichever is best now is the best-informed decision
     * available.
     *
     * What gets locked in is a *chunk* of the winner, not the winner. Candidates that
     * begin the same way survive; the rest are dropped, because the body can no longer
     * be steered onto them. Then the horizon slides forward and the survivors grow
     * again.
     */
    fun commitFromCandidates(urgent: Boolean, along: ValueAnchor? = null): Boolean {
        if (!searchConfig.commitContinuously) return false
        val publish = onSafePrefix ?: return false
        val root = safeAnchor
        // The quality floor: a commitment should be backed by real exploration, not by
        // whatever the search happened to have when the body arrived. Waived when the
        // body is genuinely about to run out -- stopping is worse than deciding early.
        if (root != null && !urgent &&
            expansions() - expansionsAtCommit < searchConfig.minCommitExpansions
        ) return false

        val committedElapsed = root?.elapsed ?: 0
        val target = committedElapsed + searchConfig.horizonCommitFrames
        // Only lines that have been evaluated across the whole window. A half-explored
        // anchor from the open frontier looks cheap precisely because nothing has
        // tested it yet, so committing off one commits to a line nobody compared --
        // and picking a different such line each time is a body walking back and
        // forth. The frontier is used only before anything has reached the horizon.
        //
        // Everything parked already descends from the committed end: `admit` refuses
        // anything else once committed, and re-rooting prunes the rest. So no descent
        // check is needed on that path, which matters -- this runs while the body
        // walks, and the check is linear in the population times its depth.
        // A preferred line, not a demand. Once a complete solution exists the sensible
        // thing is to commit along *it*, in the ordinary small steps, so that by the
        // time the search runs out there is almost nothing left to publish. But if that
        // line is not committable the population is still there, and refusing outright
        // just defers the whole remainder to one final jump.
        //
        // It only counts if it actually continues what is committed: `lineFrom` walks
        // to the *search* root when handed an anchor that is not a descendant, so
        // committing off one moves the tape backwards -- observed as a published tape
        // shrinking by 339 frames, which is not a commitment at all.
        val leaf = along?.takeIf {
            it.elapsed > committedElapsed && (root == null || it.descendsFrom(root))
        }
        val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries.filter {
            it.anchor.elapsed > committedElapsed && (root == null || it.anchor.descendsFrom(root))
        }
        val best = leaf?.let { Frontier.OpenEntry(0.0, 0.0, it) }
            ?: pool.minByOrNull { it.order }
            ?: return false

        // Distinct *commitments* on offer, not distinct first steps. What matters is
        // how many different things this decision could actually lock in, and the
        // chunk is several anchors deep -- candidates that share a first step may
        // still diverge before the commit point, and candidates that differ at the
        // first step may converge before it.
        while (ValueFieldAnchorSearch.candidateCensus.size >= ValueFieldAnchorSearch.MAX_CENSUS_ENTRIES) ValueFieldAnchorSearch.candidateCensus.poll()
        ValueFieldAnchorSearch.candidateCensus += intArrayOf(
            pool.size,
            pool.mapNotNullTo(HashSet()) { entry ->
                lineFrom(root, entry.anchor)
                    .lastOrNull { it.elapsed <= target }?.stance
                    ?: lineFrom(root, entry.anchor).firstOrNull()?.stance
            }.size,
            frontier.parkedCount,
        )

        // The *earliest* point on the winner's line the body could stop at -- the
        // smallest commitment available, not the one nearest some wanted length.
        // Everything not yet committed is still open to being improved, so committing
        // less is strictly better as long as the search can keep up, and commitments
        // are cheap now: a brake and a replay, no search.
        //
        // Aiming at a target length instead let a single commitment cover 61 frames
        // when an earlier anchor would have done, because that anchor happened to sit
        // nearer the target. The body can only stop where a brake certifies -- mid-jump
        // it cannot -- so later anchors remain the fallback, in order.
        val line = lineFrom(root, best.anchor).filter { it.elapsed > committedElapsed }
        for (candidate in line.sortedBy { it.elapsed }) {
            val braked = brakeFrom.invoke(candidate) ?: continue
            val certified = certify.invoke(braked) as? MotionPlanResult.Success ?: continue
            if (firstSafe == null) {
                firstSafe = braked
                firstSafeRollouts = expansions()
            }
            safeAnchor = candidate
            expansionsAtCommit = expansions()
            reRootOnto(candidate)
            publish(certified)
            return true
        }
        return false
    }
    /**
     * The stance this anchor's line takes first, leaving the committed end.
     *
     * Its branch identity. Null for the committed anchor itself, which belongs to no
     * branch because every branch leaves from it.
     */
    fun branchOf(anchor: ValueAnchor): Stance? {
        val root = safeAnchor
        var node: ValueAnchor = anchor
        while (true) {
            val parent = node.parent ?: return null
            if (parent === root || parent.parent == null) return node.stance
            node = parent
        }
    }
    /** Hands the live population to the renderer, best first-flagged. */
    fun publishCandidates() {
        if (!searchConfig.commitContinuously) return
        val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries
        if (pool.isEmpty()) return
        val root = safeAnchor
        val best = pool.minByOrNull { it.order }
        val shown = pool.sortedBy { it.order }.take(ValueFieldAnchorSearch.MAX_SHOWN_CANDIDATES)
        PlanningDebugChannel.publishCandidates(
            shown.map { entry ->
                val points = ArrayList<Vec3d>()
                root?.let { points += it.state.position }
                lineFrom(root, entry.anchor).forEach { points += it.state.position }
                PlanningDebugChannel.CandidateLine(points, entry === best)
            }
        )
    }
    /** The shallowest ancestor of [anchor] that has reached at least [elapsed] frames. */
    private fun ancestorAt(anchor: ValueAnchor, elapsed: Int): ValueAnchor {
        var node: ValueAnchor = anchor
        while (true) {
            val parent = node.parent ?: return node
            if (parent.elapsed < elapsed) return node
            node = parent
        }
    }
    /** The anchors strictly between [root] and [leaf], oldest first; [leaf] included. */
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
        // Prune to the committed line, from the first commitment onward.
        //
        // Two looser rules were tried and both broke the live walk, for the same
        // reason. Pruning to the *cursor* (alternatives branching ahead of the body are
        // formally still adoptable) and letting the *bootstrap* commitment prune
        // nothing both let candidates leave the published line -- and the executor
        // refuses a tape that disagrees with frames it has already pressed. Live that
        // is 4 to 7 halts against none; the headless bench missed it entirely because
        // it accepts every publication instead of modelling adoption.
        //
        // The cost is real and worth naming: the bootstrap is made before anything has
        // been compared, so a bad first commitment fences the search into a bad line.
        // On the headless corpus, where nothing is executed, keeping the alternatives
        // rescues two of six routes. Live, the body has already started walking that
        // line and the alternatives are gone regardless.
        val root = anchor
        reachableRoot = anchor
        frontier.retainDescendants(anchor)
        // The anchor just committed to has itself been popped, and the successors it
        // will have do not exist yet -- so pruning to its descendants can leave nothing
        // at all to expand, and the search ends the instant it commits. It goes back in
        // with whatever actions it has not tried.
        frontier.reopen(anchor)
        // The horizon slides forward with the commitment, and the candidates that still
        // begin the way the body is now committed to begin come back to life. The rest
        // are unreachable and are simply dropped.
        if (searchConfig.localHorizonFrames > 0) {
            horizonEnd = anchor.elapsed + searchConfig.localHorizonFrames
        }
        // The population survives the commitment. Candidates the new horizon has moved
        // past go back to growing; the rest stay parked and remain candidates for the
        // *next* decision.
        //
        // Clearing it wholesale -- which is what this did -- destroyed every evaluated
        // line at every commitment and made them all re-earn their depth from scratch.
        // The search could not keep up, and it left nothing parked to choose between,
        // so the next commitment fell back on the half-explored open frontier.
        frontier.repartition(anchor, horizonEnd)
        // Branch identity is relative to the committed end, so every key in here now
        // describes a partition that no longer exists. Keeping them would prune new
        // branches against the buckets of old ones.
        if (searchConfig.rootFanFrames > 0) frontier.clearBuckets()
        committed = true
    }
}
