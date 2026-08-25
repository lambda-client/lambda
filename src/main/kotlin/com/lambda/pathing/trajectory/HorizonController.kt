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

        if (root != null) {
            val progress = field.guide(root.stance) - field.guide(best.anchor.stance)
            if (progress < MIN_COMMIT_PROGRESS_TICKS) return false
        }

        val line = lineFrom(root, best.anchor).filter { it.elapsed > committedElapsed }
        for (candidate in line.sortedBy { it.elapsed }) {

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
