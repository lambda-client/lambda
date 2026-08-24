/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Masks ballistic coarse edges against real collision shapes.
 *
 * The reachability arithmetic lives in [LaunchSolver]; this is the part that needs the
 * world. It asks the solver for the launches that could make the move, ranked by margin,
 * and sweeps each one's body box along the arc it actually flies until one is clear.
 *
 * The contract with the trajectory layer is unchanged and deliberate: this is permissive.
 * A launch published here is a *candidate*, and the trajectory search still has to fly it
 * through the real simulator and certify it. What is new is that the candidate arrives
 * with the take-off that makes it work attached, so the search refines an answer instead
 * of enumerating its way to one.
 */
object JumpArcProbe {
    class Reachable(
        val solution: LaunchSolution,
        val reads: Set<VoxelPos>,
    )

    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        stepX: Int,
        stepZ: Int,
        span: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
    ): Reachable? {
        val to = from.offset(span * stepX, rise, span * stepZ)
        val reads = HashSet<VoxelPos>()

        for (solution in LaunchSolver.solve(from, to, profile, modes)) {
            val clearance = sweepClearance(view, from, stepX, stepZ, solution, reads) ?: continue
            return Reachable(solution.withClearance(clearance), reads)
        }
        return null
    }

    /**
     * Sweeps the body box along [solution]'s arc, returning the tightest gap it passes.
     *
     * Null means the arc is blocked, or crosses a cell the client has not captured -- an
     * unknown cell fails closed rather than being guessed at.
     *
     * The sweep walks the arc's own per-tick positions rather than interpolating between
     * endpoints. Horizontal motion under drag is not linear in time, and the difference
     * lands squarely on the apex, which is exactly where a head-bonk is decided.
     */
    private fun sweepClearance(
        view: CoarseVoxelView,
        from: Stance,
        stepX: Int,
        stepZ: Int,
        solution: LaunchSolution,
        reads: MutableSet<VoxelPos>,
    ): Double? {
        val length = hypot(stepX.toDouble(), stepZ.toDouble())
        if (length <= 0.0) return null
        val unitX = stepX / length
        val unitZ = stepZ / length
        val launchX = from.x + 0.5 + unitX * solution.launchOffset
        val launchZ = from.z + 0.5 + unitZ * solution.launchOffset

        val heights = solution.arc.heights
        val distances = solution.arc.distances

        var clearance = CLEARANCE_CAP
        var previous = coreBox(launchX, from.y.toDouble(), launchZ)
        for (index in heights.indices) {
            val along = distances[index]
            val box = coreBox(
                launchX + unitX * along,
                from.y + heights[index],
                launchZ + unitZ * along,
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

    private const val CORE_HALF_WIDTH = 0.2
    private const val BODY_HEIGHT = 1.8
    private const val CLEARANCE_CAP = 0.5
    private const val FLOOR_CONTACT_EPSILON = 1.0E-7
}
