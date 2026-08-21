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

private data class AnchorKey(
    val stance: Stance,
    val yawBucket: Int,
    val speedBucket: Int,
)

internal class Frontier(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val routeIndex: Map<Stance, Int>,
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
        open += entryFor(anchor, guide)
    }

    fun offer(entry: OpenEntry) {
        open += entry
    }

    fun retainDescendants(root: ValueAnchor) {
        val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(root) }
        open.clear()
        open.addAll(retained)
    }

    fun repartition(root: ValueAnchor, horizonEnd: Int) {
        val surviving = parked.filter { it.anchor.descendsFrom(root) }
        parked.clear()
        surviving.forEach { entry ->
            if (entry.anchor.elapsed < horizonEnd) open += entry else parked += entry
        }
    }

    fun admit(anchor: ValueAnchor) {
        val guide = field.guide(anchor.stance)
            .takeIf { it.isFinite() }
            ?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
        deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

        val key = AnchorKey(anchor.stance, yawBucket(anchor), speedBucket(anchor))
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

        incumbentFrames()?.let { if (anchor.elapsed + guide >= it) return }

        open += entryFor(anchor, guide)
    }

    fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

    private fun entryFor(anchor: ValueAnchor, guide: Double) = OpenEntry(
        order = anchor.elapsed + momentumAdjusted(anchor, guide),
        bound = anchor.elapsed +
            (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
        anchor = anchor,
    )

    private fun ValueAnchor.dominates(other: ValueAnchor): Boolean =
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

    private companion object {
        const val SPEED_DOMINANCE_SLACK = 0.01
    }
}
