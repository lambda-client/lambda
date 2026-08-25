/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import kotlin.math.abs

/**
 * One relative move a body can make, and the cell tests that admit it.
 *
 * A template knows nothing about which movement produced it beyond [movement], which is
 * how the graph stayed indifferent to the vocabulary growing: conditions are opaque
 * [CellCondition]s rather than cases of a shared enum, so a movement can test for water or
 * a ladder without every other movement learning about it.
 */
class MotionTemplate internal constructor(
    val id: MotionTemplateId,
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val movement: MovementId,
    val lowerBoundTicks: Double,
    private val conditions: List<CellCondition>,
    private val arc: ArcSpec? = null,
    private val strideCost: Double? = null,
) {
    /**
     * A template whose edge is flown rather than walked.
     *
     * [modes] is what makes a drop a drop: restricting the solver to the non-jumping modes
     * is the difference between stepping off a ledge and leaping off it.
     */
    data class ArcSpec(
        /**
         * The whole horizontal offset the arc covers, not a direction and a multiple of it.
         *
         * It used to be a unit step plus a span, which quietly made the eight compass rays
         * the only jumps that could exist: three across and one to the side has no unit step
         * whose multiples reach it. Carrying the offset itself costs nothing -- the sweep
         * normalises it into a heading anyway -- and lets the graph offer every landing the
         * ballistics can actually reach.
         */
        val dx: Int,
        val dz: Int,
        val rise: Int,
        val modes: List<LaunchMode> = LaunchMode.entries,
        /**
         * How far the body falls onto a bouncy surface on the way, when it does.
         *
         * Set only by a bounce, and it changes which probe answers: an arc with a bounce in
         * it is not a ballistic flight the launch solver can describe, because its impulse
         * comes from the fall rather than from a key.
         */
        val bounceDrop: Int? = null,
    )

    init {
        require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
        require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
            "Template lower bound must be finite and positive: $lowerBoundTicks"
        }
    }

    fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

    /** Whether this move keeps its feet down -- its edge needs no arc probe to issue. */
    internal val flightless: Boolean get() = arc == null

    /**
     * The cheapest edge this template can ever issue.
     *
     * The heuristic's per-block caps are derived from this rather than from
     * [lowerBoundTicks], and the difference is not cosmetic: a step-up template charges a
     * stride when the real riser is half a block, so a heuristic built on the nominal cost
     * over-estimates every ascent over slab terrain. An over-estimating heuristic is an
     * inadmissible one, and D* answers an inadmissible heuristic by terminating on a route
     * that is merely good -- which is how a body ended up jumping over a staircase it could
     * have walked up.
     */
    internal val minimumTicks: Double = minOf(lowerBoundTicks, strideCost ?: lowerBoundTicks)

    /**
     * A bounce edge, which carries a [com.lambda.pathing.launch.BounceSolution] rather than a
     * launch.
     *
     * Separate from the ballistic path because the two solved objects are different shapes:
     * a launch names a take-off the body has to reach, while a bounce names the fall that
     * supplies its impulse and the point the slime has to be at.
     */
    private fun bounceEdge(
        view: CoarseVoxelView,
        origin: Stance,
        spec: ArcSpec,
        drop: Int,
        launchOffset: Double,
    ): CoarseEdge? {
        val probed = BounceArcProbe.probe(
            view, origin, spec.dx, spec.dz, drop, spec.rise,
            launchHeight = origin.y + launchOffset,
        ) ?: return null

        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            movement = movement,
            lowerBoundTicks = lowerBoundTicks,
            readSet = LazyReadSet {
                buildSet {
                    readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
                    addAll(probed.reads)
                }
            },
            bounce = probed.solution,
        )
    }

    /** How far below its nominal height a stance's feet sit, from the cell holding it up. */
    private fun surfaceOffset(view: CoarseVoxelView, stance: Stance): Double =
        view.surfaceOffset(stance.x, stance.y - 1, stance.z)

    /** The height between the two standing surfaces, which is [dy] on whole-block terrain. */
    private fun realRise(view: CoarseVoxelView, origin: Stance): Double =
        dy + surfaceOffset(view, target(origin)) - surfaceOffset(view, origin)

    internal fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
        conditions.all { it.matches(view, origin.x, origin.y, origin.z) }

    internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
        if (!matches(view, origin)) return null
        val probed = arc?.let { spec ->
            // Where the feet actually are at each end. On whole blocks both offsets are
            // zero and this is exactly the stance arithmetic; over a slab or a snow layer
            // it is not, and the arc has to be solved for the height the body really flies
            // rather than the one its stance implies.
            val launchOffset = surfaceOffset(view, origin)
            val landingOffset = surfaceOffset(view, target(origin))
            spec.bounceDrop?.let { drop ->
                return bounceEdge(view, origin, spec, drop, launchOffset)
            }
            JumpArcProbe.probe(
                view, origin, spec.dx, spec.dz, spec.rise, modes = spec.modes,
                riseHeight = spec.rise + landingOffset - launchOffset,
                launchHeight = origin.y + launchOffset,
            ) ?: return null
        }
        // Only asked where the answer can change the price: two extra cell reads on every
        // walk edge is not free, and on whole-block terrain the answer is always the same.
        val ticks = if (strideCost != null && realRise(view, origin) <= CoarseMoveRates.FREE_STEP_RISE) {
            strideCost
        } else {
            lowerBoundTicks
        }

        return CoarseEdge(
            id = CoarseEdgeId(id, origin),
            from = origin,
            to = target(origin),
            movement = movement,
            lowerBoundTicks = ticks,
            readSet = LazyReadSet {
                buildSet {
                    readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
                    probed?.let { addAll(it.reads) }
                }
            },
            launch = probed?.solution,
        )
    }

    /**
     * Every cell this template can read, relative to its origin.
     *
     * Incremental repair inverts this to find which stances a block change invalidates, so
     * anything read and not declared here becomes an edge nothing ever refreshes.
     *
     * Materialized once per template: the offsets are constant, and this list is walked on
     * every edge the template issues.
     */
    internal val readOffsets: List<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            addAll(ORIGIN_STANCE_READS)
            conditions.forEach { addAll(it.reads()) }

            arc?.let { spec ->
                // Sampled along the arc's own line rather than along a unit step, because an
                // off-axis jump has no unit step. One sample per block of the longer axis keeps
                // the declared cells a superset of the ones the sweep touches, which is what
                // stops an edge going stale when a block under the flight path changes.
                val steps = maxOf(abs(spec.dx), abs(spec.dz))
                for (step in 0..steps) {
                    val alongX = if (steps == 0) 0 else Math.round(spec.dx.toDouble() * step / steps).toInt()
                    val alongZ = if (steps == 0) 0 else Math.round(spec.dz.toDouble() * step / steps).toInt()
                    val floorReach = minOf(spec.rise, -(spec.bounceDrop ?: 0), 0) - 2
                    for (y in floorReach..ARC_READ_CEILING) {
                        for (ox in -1..1) {
                            for (oz in -1..1) {
                                add(VoxelPos(alongX + ox, y, alongZ + oz))
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val ORIGIN_STANCE_READS = listOf(
            VoxelPos(0, -1, 0),
            VoxelPos(0, 0, 0),
            VoxelPos(0, 1, 0),
        )

        const val ARC_READ_CEILING = 4
    }
}
