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
import java.util.PriorityQueue
import kotlin.math.floor

/**
 * Bucketed by the stance the body stands on, not by progress along anything.
 *
 * Bucketing by ticks-to-go instead — the route-free analogue of the confined search's
 * route-node index — was tried and is worse: it makes geographically distinct anchors
 * that happen to be equally far from the goal compete for the same three slots, which
 * pruned the only line that certified one corpus case at all.
 */
private data class AnchorKey(
    val stance: Stance,
    val yawBucket: Int,
    val speedBucket: Int,
    /**
     * Which of the committed end's successors this anchor descends through.
     *
     * Anchors on different branches are never comparable. Without this, dominance --
     * the thing that makes the search fast -- quietly destroys the population it is
     * supposed to be choosing from: different opening moves converge on similar
     * stances at similar speeds within a block or two, share a bucket, and all but one
     * are pruned. Measured, that left 175 live candidates offering exactly *one*
     * distinct next stance.
     *
     * Keeping branches apart costs a wider frontier near the committed end and buys
     * the only thing the horizon exists for: several genuinely different ways to spend
     * the next stretch, alive at the moment one of them has to be chosen.
     */
    val branch: Stance?,
)
/**
 * Which anchors are still worth expanding, and in what order.
 *
 * Owns the open queue, the parked candidates and the dominance buckets, and nothing else.
 * The three things [admit] needs to know that are not its own -- which branch an anchor
 * belongs to, whether the body can still be steered onto it, and how good the incumbent
 * is -- arrive as predicates, so the horizon and the endgame never reach in here.
 */
internal class Frontier(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val routeIndex: Map<Stance, Int>,
    private val branchKey: (ValueAnchor) -> Stance?,
    private val reachable: (ValueAnchor) -> Boolean,
    private val incumbentFrames: () -> Int?,
) {
    class OpenEntry(val order: Double, val bound: Double, val anchor: ValueAnchor)

    private val open = PriorityQueue<OpenEntry>(compareBy { it.order })
    private val parked = ArrayList<OpenEntry>()
    private val dominance = HashMap<AnchorKey, MutableList<ValueAnchor>>()

    val isExhausted: Boolean get() = open.isEmpty() && parked.isEmpty()
    val hasOpen: Boolean get() = open.isNotEmpty()
    val hasParked: Boolean get() = parked.isNotEmpty()
    val parkedCount: Int get() = parked.size
    val parkedEntries: List<OpenEntry> get() = parked
    val openEntries: List<OpenEntry> get() = open.toList()

    var bestValue = Double.POSITIVE_INFINITY
        private set

    var deepestProgress = 0
        private set

    fun poll(): OpenEntry = open.poll()

    fun park(entry: OpenEntry) {
        parked += entry
    }

    /** Puts an already-popped anchor back on the frontier, ranked as [admit] would. */
    fun reopen(anchor: ValueAnchor) {
        if (open.any { it.anchor === anchor }) return
        val guide = field.guide(anchor.stance).takeIf { it.isFinite() } ?: return
        open += OpenEntry(
            order = anchor.elapsed + searchConfig.tailWeight * momentumAdjusted(anchor, guide),
            bound = anchor.elapsed +
                (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
            anchor = anchor,
        )
    }

    fun offer(entry: OpenEntry) {
        open += entry
    }

    /** Drops open entries the body can no longer be steered onto. */
    fun retainDescendants(root: ValueAnchor) {
        val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(root) }
        open.clear()
        open.addAll(retained)
    }

    /** Lets parked candidates the advanced horizon has moved past start growing again. */
    fun repartition(root: ValueAnchor, horizonEnd: Int) {
        val surviving = parked.filter { it.anchor.descendsFrom(root) }
        parked.clear()
        surviving.forEach { entry ->
            if (entry.anchor.elapsed < horizonEnd) open += entry else parked += entry
        }
    }

    fun clearBuckets() {
        dominance.clear()
    }

fun admit(anchor: ValueAnchor) {
    // The body's own stance can be unmapped (it may stand somewhere the coarse
    // layer never labelled). The root still has to be expanded, so it falls back
    // to the admissible bound; every later anchor is refused instead of guessed.
    val guide = field.guide(anchor.stance)
        .takeIf { it.isFinite() }
        ?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
    if (guide < bestValue) bestValue = guide
    deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

    val key = AnchorKey(
        stance = anchor.stance,
        yawBucket = yawBucket(anchor),
        speedBucket = speedBucket(anchor),
        branch = branchKey(anchor),
    )
    // Past the commitment point only lines the body can still be steered onto may
    // be published -- which is anything sharing the frames it has already walked,
    // not only the line that was committed.
    if (!reachable(anchor)) return

    val bucket = dominance.getOrPut(key) { ArrayList() }
    if (bucket.any { it.dominates(anchor) }) return
    bucket.removeAll { anchor.dominates(it) }
    if (bucket.size >= searchConfig.frontierPerKey) {
        val worst = bucket.maxByOrNull { it.elapsed } ?: return
        if (worst.elapsed <= anchor.elapsed) return
        bucket.remove(worst)
    }
    bucket += anchor
    // Incumbent cut. `guide` estimates the remaining travel closely (it is the
    // coarse layer's measured cost-to-go), so an anchor already projected to
    // finish later than a certified tape is not worth a rollout. The admissible
    // bound below still governs *termination*; this only declines to open a
    // branch the field says is already beaten — without it the free search keeps
    // fanning out across open ground long after it has a good tape, which is the
    // whole of the planning-latency regression.
    incumbentFrames()?.let { if (anchor.elapsed + guide >= it) return }

    open += OpenEntry(
        order = anchor.elapsed + searchConfig.tailWeight * momentumAdjusted(anchor, guide),
        // Only the credit may touch an admissible bound, and only as its constant
        // maximum: subtracting a constant from a lower bound leaves a lower bound,
        // while charging this anchor's turn cost to one would not.
        bound = anchor.elapsed +
            (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
        anchor = anchor,
    )
}

private fun ValueAnchor.dominates(other: ValueAnchor): Boolean =
    elapsed <= other.elapsed &&
        speed >= other.speed - SPEED_DOMINANCE_SLACK &&
        collisionEvents <= other.collisionEvents &&
        inputSwitches <= other.inputSwitches

/**
 * The stance value, corrected for what this body is actually doing.
 *
 * The coarse value prices a stationary body, so without this an anchor sprinting
 * at the goal, one stopped on the same block, and one sprinting *away* all score
 * the same — and a frontier that cannot separate them stops being best-first.
 * Ordering only; [MomentumValue] explains why the bound may not use it.
 */
private fun momentumAdjusted(anchor: ValueAnchor, guide: Double): Double {
    val next = field.steps(anchor.stance, 1, heading = anchor.heading())
        .firstOrNull()?.to?.center() ?: return guide
    val alignment = headingAlignment(
        anchor.state.velocity.x, anchor.state.velocity.z,
        next.x - anchor.state.position.x, next.z - anchor.state.position.z,
    )
    val headingError = Math.toDegrees(kotlin.math.acos(alignment.coerceIn(-1.0, 1.0)))
    return guide -
        momentumCredit(anchor.speed, alignment) +
        momentumTurnCost(anchor.speed, headingError, config.maxYawDegreesPerFrame)
}

        private fun yawBucket(anchor: ValueAnchor): Int = floor(
    ((anchor.state.rotation.yaw % 360.0) + 360.0) % 360.0 / searchConfig.yawBucketDegrees
).toInt()

private fun speedBucket(anchor: ValueAnchor): Int =
    floor(anchor.speed / searchConfig.speedBucketBlocks).toInt()

/** Best-effort route index for reporting only; the search never steers by it. */
fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

    private companion object {
        const val SPEED_DOMINANCE_SLACK = 0.01
    }
}
