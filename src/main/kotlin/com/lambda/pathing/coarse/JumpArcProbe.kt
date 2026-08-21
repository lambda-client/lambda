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

data class JumpHint(
    val sprint: Boolean,
    val entrySpeed: Double,
    val launchOffsetBlocks: Double,
    val clearance: Double,
)

object JumpArcProbe {
    class Reachable(
        val hint: JumpHint,
        val reads: Set<VoxelPos>,
    )

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

                            if (obstacle.maxY <= swept.minY + FLOOR_CONTACT_EPSILON) continue
                            clearance = minOf(clearance, gap(swept, obstacle))
                        }
                    }
                }
            }
        }
        return clearance
    }

    private fun coreBox(x: Double, y: Double, z: Double) = Box(
        x - CORE_HALF_WIDTH, y, z - CORE_HALF_WIDTH,
        x + CORE_HALF_WIDTH, y + BODY_HEIGHT, z + CORE_HALF_WIDTH,
    )

    private fun gap(a: Box, b: Box): Double {
        val dx = max(max(b.minX - a.maxX, a.minX - b.maxX), 0.0)
        val dy = max(max(b.minY - a.maxY, a.minY - b.maxY), 0.0)
        val dz = max(max(b.minZ - a.maxZ, a.minZ - b.maxZ), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

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

    private class Family(
        val sprint: Boolean,
        val entrySpeed: Double,
        private val baseReach: DoubleArray,
        private val speedGain: DoubleArray,
    ) {
        fun reach(rise: Int): Double = reachAt(entrySpeed, rise)

        fun reachAt(speed: Double, rise: Int): Double {
            val index = (RISE_MAX - rise).coerceIn(0, baseReach.lastIndex)
            return baseReach[index] + speedGain[index] * speed
        }
    }

    private const val RISE_MAX = 1

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

    fun maxReach(entrySpeed: Double, rise: Int): Double =
        FAMILIES.maxOf { family -> family.reachAt(entrySpeed, rise) }

    private const val JUMP_SPEED = 0.42
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98

    private const val MAX_ARC_TICKS = 24

    private const val MAX_LAUNCH_DEPTH = 0.4

    private const val LANDING_TOLERANCE = 0.35

    private const val CORE_HALF_WIDTH = 0.2
    private const val BODY_HEIGHT = 1.8
    private const val CLEARANCE_CAP = 0.5
    private const val FLOOR_CONTACT_EPSILON = 1.0E-7
}
