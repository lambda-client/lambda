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
import com.lambda.pathing.debug.PlanningDebugChannel
import net.minecraft.util.math.Vec3d

internal class HorizonController(
    private val searchConfig: ValueFieldSearchConfig,
    private val field: CoarseValueField,
    private val frontier: Frontier,
    private val clock: SearchClock,
    private val cursorFrame: (() -> Int?)?,
    private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
    private val expansions: () -> Int,
    private val brakeFrom: (ValueAnchor) -> Solution?,
    private val certify: (Solution) -> MotionPlanResult,
) {
    var safeAnchor: ValueAnchor? = null
        private set

    var horizonEnd = Int.MAX_VALUE
        private set

    var committed = false
        private set

    var reachableRoot: ValueAnchor? = null
        private set

    private var expansionsAtCommit = 0

    fun begin() {
        if (searchConfig.localHorizonFrames > 0) horizonEnd = searchConfig.localHorizonFrames
    }

    fun canReach(anchor: ValueAnchor): Boolean =
        !committed || reachableRoot?.let { anchor.descendsFrom(it) } ?: true

    fun publishPrefix(anchor: ValueAnchor, expansions: Int) {
        val publish = onSafePrefix ?: return

        val remaining = field.guide(anchor.stance)
        if (!remaining.isFinite() || remaining <= searchConfig.finishValueTicks) return

        val running = safeAnchor
        if (running == null) {
            if (anchor.elapsed < searchConfig.safePrefixFrames) return
            if (clock.elapsedMillis() < searchConfig.safePrefixDelayMillis) return
            // The first commit shapes the whole tape and gets the same minimum-work
            // floor as every later one. Wall time alone made the tape a function of
            // machine load: a busy JVM reached the delay with a double-digit attempt
            // count and committed whatever existed.
            if (expansions() < searchConfig.minCommitExpansions) return
        } else {
            if (frontier.hasParked) return
            if (!anchor.descendsFrom(running)) return
            if (anchor.elapsed < running.elapsed + searchConfig.horizonCommitFrames) return
            if (expansions() - expansionsAtCommit < searchConfig.minCommitExpansions) return
            val executing = cursorFrame?.invoke() ?: return
            if (running.elapsed - executing > searchConfig.horizonRunwayFrames) return
        }
        val braked = brakeFrom.invoke(anchor) ?: return
        val certified = certify.invoke(braked) as? MotionPlanResult.Success ?: return
        safeAnchor = anchor
        expansionsAtCommit = expansions()
        reRootOnto(anchor)
        publish(certified)
    }

    fun commitFromCandidates(urgent: Boolean, along: ValueAnchor? = null): Boolean {
        val publish = onSafePrefix ?: return false
        val root = safeAnchor

        if (!urgent && expansions() - expansionsAtCommit < searchConfig.minCommitExpansions) {
            return false
        }

        val committedElapsed = root?.elapsed ?: 0

        val leaf = along?.takeIf {
            it.elapsed > committedElapsed && (root == null || it.descendsFrom(root))
        }
        val pool = if (frontier.hasParked) frontier.parkedEntries else frontier.openEntries.filter {
            it.anchor.elapsed > committedElapsed && (root == null || it.anchor.descendsFrom(root))
        }
        val best = leaf?.let { Frontier.OpenEntry(0.0, 0.0, it) }
            ?: pool.minByOrNull { it.order }
            ?: return false

        // A pressured commit must still go somewhere. At the capture frontier every
        // forward rollout dies on terrain the client does not have yet, the surviving
        // candidates are lateral wander, and committing them is what walked the body
        // around in circles at the streamed edge. If the best line's LEAF makes no
        // guide progress over what is already committed, let the tape end in its
        // certified stop instead -- standing still until the capture catches up reads
        // as a pause; wandering reads as broken. The leaf is judged, not the committed
        // midpoint, so a reposition-then-jump line keeps its backup step.
        if (root != null) {
            val progress = field.guide(root.stance) - field.guide(best.anchor.stance)
            if (progress < MIN_COMMIT_PROGRESS_TICKS) return false
        }

        val line = lineFrom(root, best.anchor).filter { it.elapsed > committedElapsed }
        for (candidate in line.sortedBy { it.elapsed }) {
            // The FIRST commitment must not land inside finishing range: it forces every
            // complete solution to descend from a near-goal stop or be rejected as
            // divergent -- live this stranded a 7-block walk 2.8 blocks short, then
            // failed the replan. Short routes skip the premature prefix and publish
            // their one complete tape instead. Later commitments are exempt: the
            // endgame of a long walk legitimately commits near the goal, and blocking
            // that stopped corpus routes short of arrival.
            if (root == null && field.guide(candidate.stance) <= searchConfig.finishValueTicks) continue
            val braked = brakeFrom.invoke(candidate) ?: continue
            val certified = certify.invoke(braked) as? MotionPlanResult.Success ?: continue
            safeAnchor = candidate
            expansionsAtCommit = expansions()
            reRootOnto(candidate)
            publish(certified)
            return true
        }
        return false
    }

    fun publishCandidates() {
        if (onSafePrefix == null) return
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

    private companion object {
        /** Minimum guide improvement a pressured commit's leaf must deliver, in ticks. */
        const val MIN_COMMIT_PROGRESS_TICKS = 1.0
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
