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
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.center
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.floor

private data class AnchorKey(
    val stance: Stance,
    val yawBucket: Int,
    val speedBucket: Int,
    val localXBucket: Int,
    val localZBucket: Int,
    val velocityHeadingBucket: Int,
    val airborne: Boolean,
    val sprinting: Boolean,
    val hazardKnown: Boolean,
)

internal class Frontier(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private var routeIndex: Map<Stance, Int>,
    private val reachable: (ValueAnchor) -> Boolean,
    private val incumbentFrames: () -> Int?,
) {
    class OpenEntry(
        val order: Double,
        val bound: Double,
        val anchor: ValueAnchor,
        internal var sequence: Long = Long.MAX_VALUE,
    )

    private val open = PriorityQueue<OpenEntry>(
        compareBy<OpenEntry>({ it.order }, { it.bound }, { it.anchor.elapsed }, { it.sequence })
    )
    private val parked = ArrayList<OpenEntry>()

    /**
     * Attempts waiting on world knowledge, not on search progress. Kept apart from
     * [parked], whose lifecycle is horizon commitment: these wake on events, carry no
     * penalty, and must never starve the commit machinery's parked pool.
     */
    private class BlockedAttempt(val anchor: ValueAnchor, val action: com.lambda.pathing.movement.TrajectoryDecision)

    private val blocked = ArrayList<BlockedAttempt>()
    /** Explicit bounded beam buckets, not a correctness-preserving dominance proof. */
    private val beamBuckets = HashMap<AnchorKey, MutableList<ValueAnchor>>()
    private var insertionSequence = 0L

    val isExhausted: Boolean get() = open.isEmpty() && parked.isEmpty()
    val hasBlocked: Boolean get() = blocked.isNotEmpty()
    val hasOpen: Boolean get() = open.isNotEmpty()
    val hasParked: Boolean get() = parked.isNotEmpty()
    val parkedEntries: List<OpenEntry> get() = parked
    val openEntries: List<OpenEntry> get() = open.toList()

    var deepestProgress = 0
        private set

    fun poll(): OpenEntry = open.poll()

    fun park(entry: OpenEntry) {
        parked += entry
    }

    fun reopen(anchor: ValueAnchor) {
        if (open.any { it.anchor === anchor }) return
        val guide = field.guide(anchor.stance).takeIf { it.isFinite() } ?: return
        enqueue(entryFor(anchor, guide))
    }

    fun offer(entry: OpenEntry) {
        enqueue(entry)
    }

    fun retainDescendants(root: ValueAnchor) {
        val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(root) }
        open.clear()
        open.addAll(retained)
        blocked.retainAll { it.anchor.descendsFrom(root) }
        beamBuckets.clear()
    }

    /** Parks one attempt until knowledge arrives; the anchor's other actions stay live. */
    fun parkBlocked(anchor: ValueAnchor, action: com.lambda.pathing.movement.TrajectoryDecision) {
        blocked += BlockedAttempt(anchor, action)
    }

    /**
     * Adopts an extended or replaced route: indices are rebuilt, progress is
     * recomputed over the live anchors (it is NOT comparable across routes), and the
     * open queue is re-scored against the refreshed guide field.
     */
    fun updateRoute(newIndex: Map<Stance, Int>) {
        routeIndex = newIndex
        deepestProgress = 0
        open.forEach { deepestProgress = maxOf(deepestProgress, progressOf(it.anchor.stance)) }
        parked.forEach { deepestProgress = maxOf(deepestProgress, progressOf(it.anchor.stance)) }
        rescore()
    }

    /**
     * Re-scores every open entry against the live guide field. Queued scores freeze at
     * insertion, which is fine between knowledge batches -- ordering staleness, never
     * soundness -- and wrong across one: a refreshed field can invalidate or improve
     * whole regions. Wholesale, because the queue is beam-bounded and batches are rare.
     */
    fun rescore() {
        val entries = open.toList()
        open.clear()
        entries.forEach { entry ->
            val guide = field.guide(entry.anchor.stance)
            if (guide.isFinite()) {
                // The original insertion sequence is preserved deliberately: a rescore
                // whose values did not change must reproduce the identical queue, or
                // every world event scrambles tie-breaking and the search goes
                // nondeterministic against its own baseline.
                val fresh = entryFor(entry.anchor, guide)
                fresh.sequence = entry.sequence
                open += fresh
            }
        }
        rebuildBeamBuckets()
    }

    /** Returns every blocked attempt to contention after world events arrived. */
    fun wakeBlocked() {
        if (blocked.isEmpty()) return
        val woken = java.util.IdentityHashMap<ValueAnchor, Unit>()
        blocked.forEach { attempt ->
            attempt.anchor.attempted.remove(attempt.action)
            woken[attempt.anchor] = Unit
        }
        blocked.clear()
        woken.keys.forEach { reopen(it) }
    }

    fun repartition(root: ValueAnchor, horizonEnd: Int) {
        val surviving = parked.filter { it.anchor.descendsFrom(root) }
        parked.clear()
        surviving.forEach { entry ->
            if (entry.anchor.elapsed < horizonEnd) open += entry else parked += entry
        }
        rebuildBeamBuckets()
    }

    fun admit(anchor: ValueAnchor) {
        val guide = field.guide(anchor.stance)
            .takeIf { it.isFinite() }
            ?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
        deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

        val key = keyOf(anchor)
        if (!reachable(anchor)) return

        val bucket = beamBuckets.getOrPut(key) { ArrayList() }
        if (bucket.any { it.preferredForBeamOver(anchor) }) return
        bucket.removeAll { anchor.preferredForBeamOver(it) }
        if (bucket.size >= searchConfig.frontierPerKey) {
            val worst = bucket.maxByOrNull { it.elapsed } ?: return
            if (worst.elapsed <= anchor.elapsed) return
            bucket.remove(worst)
        }
        bucket += anchor

        incumbentFrames()?.let { if (anchor.elapsed + guide >= it) return }

        enqueue(entryFor(anchor, guide))
    }

    fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

    // Deliberately unweighted. Inflating the guide term (weighted-A* style, 1.1-1.3) was
    // measured on the corpus: it collapses the plateau of near-equal anchors, but the
    // horizon controller commits prefixes irreversibly, and a greedier ordering commits
    // onto lines it cannot back out of -- bedrock-traverse stopped arriving at any weight
    // tried. Expansion breadth is the price of safe commitment here.
    private fun entryFor(anchor: ValueAnchor, guide: Double) = OpenEntry(
        order = anchor.elapsed + momentumAdjusted(anchor, guide),
        bound = anchor.elapsed +
            (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
        anchor = anchor,
    )

    private fun ValueAnchor.preferredForBeamOver(other: ValueAnchor): Boolean =
        elapsed <= other.elapsed &&
            speed >= other.speed - SPEED_DOMINANCE_SLACK &&
            collisionEvents <= other.collisionEvents &&
            inputSwitches <= other.inputSwitches

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

    private fun keyOf(anchor: ValueAnchor): AnchorKey {
        val localX = anchor.state.position.x - floor(anchor.state.position.x)
        val localZ = anchor.state.position.z - floor(anchor.state.position.z)
        val velocityYaw = Math.toDegrees(atan2(anchor.state.velocity.z, anchor.state.velocity.x))
        return AnchorKey(
            stance = anchor.stance,
            yawBucket = yawBucket(anchor),
            speedBucket = speedBucket(anchor),
            localXBucket = floor(localX / POSITION_BUCKET_BLOCKS).toInt(),
            localZBucket = floor(localZ / POSITION_BUCKET_BLOCKS).toInt(),
            velocityHeadingBucket = floor(
                ((velocityYaw % 360.0) + 360.0) % 360.0 / VELOCITY_HEADING_BUCKET_DEGREES
            ).toInt(),
            airborne = !anchor.state.onGround,
            sprinting = anchor.state.isSprinting,
            hazardKnown = anchor.hazardFrame != null,
        )
    }

    private fun enqueue(entry: OpenEntry) {
        if (entry.sequence == Long.MAX_VALUE) entry.sequence = insertionSequence++
        open += entry
    }

    private fun rebuildBeamBuckets() {
        beamBuckets.clear()
        (open.asSequence() + parked.asSequence()).forEach { entry ->
            beamBuckets.getOrPut(keyOf(entry.anchor)) { ArrayList() } += entry.anchor
        }
    }

    private companion object {

        const val SPEED_DOMINANCE_SLACK = 0.01
        const val POSITION_BUCKET_BLOCKS = 0.125
        const val VELOCITY_HEADING_BUCKET_DEGREES = 15.0
    }
}
