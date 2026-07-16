/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Trajectory-layer knowledge a certified probe hands down as a *prior*, never as a
 * promise: which gait to try, where along the approach to press jump, and how much
 * air the arc had. The launch beam tries hinted frames first; certification still
 * decides (C1 -- the probe is a mask + prior, not a discovery mechanism).
 */
data class JumpHint(
    val sprint: Boolean,
    val entrySpeed: Double,
    /** Blocks past the takeoff stance centre where the launch should happen. */
    val launchOffsetBlocks: Double,
    /** Smallest distance between the swept body and any obstacle, capped. */
    val clearance: Double,
)

/**
 * Geometric mask for jump candidates: the player's box swept along the measured
 * ballistic arc, tested against the *real* captured collision shapes instead of
 * boolean voxel traits. This is the actual fix for "the coarse path maps jumps
 * through blocks" -- a slab, fence, or stair the trait mask rounded away is exactly
 * the shape this sweep collides with.
 *
 * Deliberately permissive (v2 §5.1): the swept box is the body's *core* (shrunk
 * laterally, full height), the horizontal profile is a chord approximation, and the
 * reach test allows a landing-pad tolerance. A false positive costs one M6 reroute;
 * a false negative deletes topology forever. No collision resolution, no friction,
 * no inputs -- certification stays with the simulator.
 *
 * Reach comes from the measured jump Jacobian (2026-07-13 calibration, see
 * [[jump-physics-constants]]): landing distance is exactly linear, `D(s, v) =
 * D0(gait, rise) + s + k(rise) * v`, so reachability per entry family is arithmetic,
 * and only the swept collision test needs geometry.
 */
object JumpArcProbe {
    class Reachable(
        val hint: JumpHint,
        /** Every cell whose shape the sweep consulted; exact edge dependencies. */
        val reads: Set<VoxelPos>,
    )

    /**
     * Probes one jump candidate of [span] stances along (stepX, stepZ) landing
     * [rise] blocks up or down. Returns null when no entry-speed family clears.
     */
    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        stepX: Int,
        stepZ: Int,
        span: Int,
        rise: Int,
    ): Reachable? {
        val spanDistance = hypot((span * stepX).toDouble(), (span * stepZ).toDouble())
        val heights = arcHeights(rise)
        val reads = HashSet<VoxelPos>()
        var best: JumpHint? = null

        for (family in FAMILIES) {
            val reach = family.reach(rise)
            // The launch may happen up to MAX_LAUNCH_DEPTH past the stance centre, and
            // a landing short of the pad centre still lands on the pad.
            if (reach + MAX_LAUNCH_DEPTH < spanDistance - LANDING_TOLERANCE) continue

            val launchOffset = (spanDistance - reach).coerceIn(0.0, MAX_LAUNCH_DEPTH)
            val clearance = sweepClearance(
                view, from, stepX, stepZ, spanDistance, launchOffset, heights, reads,
            ) ?: continue

            val hint = JumpHint(family.sprint, family.entrySpeed, launchOffset, clearance)
            val incumbent = best
            if (incumbent == null || hint.clearance > incumbent.clearance ||
                (hint.clearance == incumbent.clearance && hint.sprint && !incumbent.sprint)
            ) {
                best = hint
            }
        }

        return best?.let { Reachable(it, reads) }
    }

    /**
     * Sweeps the body core tick by tick from launch to landing. Null when the sweep
     * intersects any shape; otherwise the smallest gap observed (capped).
     */
    private fun sweepClearance(
        view: CoarseVoxelView,
        from: Stance,
        stepX: Int,
        stepZ: Int,
        spanDistance: Double,
        launchOffset: Double,
        heights: DoubleArray,
        reads: MutableSet<VoxelPos>,
    ): Double? {
        val unitX = stepX / hypot(stepX.toDouble(), stepZ.toDouble())
        val unitZ = stepZ / hypot(stepX.toDouble(), stepZ.toDouble())
        val launchX = from.x + 0.5 + unitX * launchOffset
        val launchZ = from.z + 0.5 + unitZ * launchOffset
        val landingX = from.x + 0.5 + unitX * spanDistance
        val landingZ = from.z + 0.5 + unitZ * spanDistance
        val ticks = heights.size

        var clearance = CLEARANCE_CAP
        var previous = coreBox(launchX, from.y.toDouble(), launchZ)
        for (tick in 1..ticks) {
            val fraction = tick.toDouble() / ticks
            val box = coreBox(
                launchX + (landingX - launchX) * fraction,
                from.y + heights[tick - 1],
                launchZ + (landingZ - launchZ) * fraction,
            )
            val swept = previous.union(box)
            previous = box

            val margin = swept.expand(CLEARANCE_CAP)
            for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
                for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
                    for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
                        reads += VoxelPos(x, y, z)
                        val shape = view.collisionShape(x, y, z) ?: return null
                        if (shape.isEmpty) continue
                        for (bounds in shape.boundingBoxes) {
                            val obstacle = bounds.offset(x.toDouble(), y.toDouble(), z.toDouble())
                            if (obstacle.intersects(swept)) return null
                            // Ground the arc launches from, lands on, or passes over is
                            // not an obstacle; clearance measures the air the arc *threads*.
                            if (obstacle.maxY <= swept.minY + FLOOR_CONTACT_EPSILON) continue
                            clearance = minOf(clearance, gap(swept, obstacle))
                        }
                    }
                }
            }
        }
        return clearance
    }

    /**
     * Body core at a feet position: full height (an apex head bonk must block the
     * arc), laterally shrunk from the real 0.3 half-width (the chord approximation's
     * permissiveness knob -- a borderline pass costs one certification attempt).
     */
    private fun coreBox(x: Double, y: Double, z: Double) = Box(
        x - CORE_HALF_WIDTH, y, z - CORE_HALF_WIDTH,
        x + CORE_HALF_WIDTH, y + BODY_HEIGHT, z + CORE_HALF_WIDTH,
    )

    /** Euclidean gap between two non-intersecting boxes. */
    private fun gap(a: Box, b: Box): Double {
        val dx = max(max(b.minX - a.maxX, a.minX - b.maxX), 0.0)
        val dy = max(max(b.minY - a.maxY, a.minY - b.maxY), 0.0)
        val dz = max(max(b.minZ - a.maxZ, a.minZ - b.maxZ), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Feet heights above the takeoff, one entry per airborne tick, ending exactly at
     * [rise]. Vanilla's vertical recursion (`v' = (v - 0.08) * 0.98` after moving) --
     * identical for every gait, so it is computed once per rise.
     */
    internal fun arcHeights(rise: Int): DoubleArray {
        val heights = ArrayList<Double>(MAX_ARC_TICKS)
        var y = 0.0
        var vy = JUMP_SPEED
        while (heights.size < MAX_ARC_TICKS) {
            y += vy
            vy = (vy - GRAVITY) * VERTICAL_DRAG
            if (vy < 0.0 && y <= rise) {
                heights += rise.toDouble()
                break
            }
            heights += y
        }
        return heights.toDoubleArray()
    }

    /**
     * One measured entry-speed family. [baseReach]/[speedGain] indexed by rise
     * (+1 down to -3); reach = base + gain * entrySpeed, from the stance centre.
     * Rises outside the measured band clamp to the nearest row, which under-states
     * the reach of deeper drops -- the safe direction.
     */
    private class Family(
        val sprint: Boolean,
        val entrySpeed: Double,
        private val baseReach: DoubleArray,
        private val speedGain: DoubleArray,
    ) {
        fun reach(rise: Int): Double {
            val index = (RISE_MAX - rise).coerceIn(0, baseReach.lastIndex)
            return baseReach[index] + speedGain[index] * entrySpeed
        }
    }

    private const val RISE_MAX = 1

    /** Measured strides (b/t): the entry speed each gait sustains into the launch. */
    private val FAMILIES = listOf(
        Family(
            sprint = true,
            entrySpeed = 0.2806,
            baseReach = doubleArrayOf(2.4635, 3.0345, 3.4795, 3.8695, 4.204),
            speedGain = doubleArrayOf(4.71, 5.17, 5.47, 5.67, 5.84),
        ),
        Family(
            sprint = false,
            entrySpeed = 0.2159,
            baseReach = doubleArrayOf(1.107, 1.5005, 1.804, 2.0725, 2.307),
            speedGain = doubleArrayOf(4.62, 5.13, 5.44, 5.65, 5.82),
        ),
    )

    private const val JUMP_SPEED = 0.42
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98

    /** Deepest usable drop plus headroom; a longer recursion is a broken candidate. */
    private const val MAX_ARC_TICKS = 24

    /** How far past the stance centre a launch may still be pressed. */
    private const val MAX_LAUNCH_DEPTH = 0.4

    /** Landing this far short of the pad centre still lands on the pad. */
    private const val LANDING_TOLERANCE = 0.35

    private const val CORE_HALF_WIDTH = 0.2
    private const val BODY_HEIGHT = 1.8
    private const val CLEARANCE_CAP = 0.5
    private const val FLOOR_CONTACT_EPSILON = 1.0E-7
}
