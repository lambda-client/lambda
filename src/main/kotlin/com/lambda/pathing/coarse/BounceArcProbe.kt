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
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Masks a bounce edge against the terrain it depends on.
 *
 * A jump only has to miss things. A bounce has to *hit* one: the slime has to be exactly
 * where the body arrives, or the move is an ordinary fall into a pit. So this asks the solver
 * where the trough lands and then checks that cell, which is a question no other movement in
 * the catalogue needs to ask.
 *
 * Permissive in the same way [JumpArcProbe] is: what comes out is a candidate carrying the
 * take-off that makes it work, and the trajectory layer still flies it through the real
 * simulator before anything replays it.
 */
object BounceArcProbe {
    class Reachable(
        val solution: BounceSolution,
        packedReads: LongOpenHashSet,
    ) {
        /** Materialized only when repair wants it, like [JumpArcProbe.Reachable.reads]. */
        val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            JumpArcProbe.unpackReads(packedReads)
        }
    }

    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        drop: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        launchHeight: Double = from.y.toDouble(),
    ): Reachable? {
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length <= 0.0) return null

        val reads = LongOpenHashSet()
        for (sprint in SPRINT_ORDER) {
            for (holdForward in HOLD_ORDER) {
                val solution = BounceSolver.solve(
                    horizontalDistance = length,
                    drop = drop,
                    rise = rise,
                    profile = profile,
                    sprint = sprint,
                    holdForward = holdForward,
                ) ?: continue

                if (!bouncyAtContact(view, from, dx, dz, length, drop, solution, reads)) continue
                val clearance = JumpArcProbe.sweepClearance(
                    view, from, dx, dz, solution.arc,
                    launchOffset = solution.launchOffset,
                    launchHeight = launchHeight,
                    reads = reads,
                ) ?: continue
                if (clearance < 0.0) continue

                return Reachable(solution, reads)
            }
        }
        return null
    }

    /**
     * Whether the cell the body actually meets is one that bounces.
     *
     * The trough's position is a solved quantity, not a guess: [BounceSolution.contactDistance]
     * is where along the heading the body is deepest, so the cell to test is that point at the
     * fall's depth. Checking anywhere else -- under the take-off, under the landing -- would
     * accept edges whose slime the body flies straight past.
     *
     * A little either side of the exact point is accepted, because the body is 0.6 wide and
     * lands on whatever its feet are over rather than on a mathematical point.
     */
    private fun bouncyAtContact(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        length: Double,
        drop: Int,
        solution: BounceSolution,
        reads: LongOpenHashSet,
    ): Boolean {
        val unitX = dx / length
        val unitZ = dz / length
        val reach = solution.launchOffset + solution.contactDistance
        val contactX = from.x + 0.5 + unitX * reach
        val contactZ = from.z + 0.5 + unitZ * reach
        // The block whose *top* is the surface the body met, which is one below that surface.
        val padY = from.y - drop - 1

        for (offsetX in -CONTACT_SLACK..CONTACT_SLACK) {
            for (offsetZ in -CONTACT_SLACK..CONTACT_SLACK) {
                val x = floor(contactX).toInt() + offsetX
                val z = floor(contactZ).toInt() + offsetZ
                reads.add(BlockPos.asLong(x, padY, z))
                if (view.voxel(x, padY, z).bouncy) return true
            }
        }
        return false
    }

    /** Sprinting first: it reaches further, and a shorter bounce is the fallback. */
    private val SPRINT_ORDER = listOf(true, false)

    /** Held first, because a bounce's value is its reach. */
    private val HOLD_ORDER = listOf(true, false)

    /**
     * Cells either side of the solved contact point that still count, in blocks.
     *
     * One, because the body is 0.6 wide: it lands on whatever its feet are over, and the
     * solved point is the centre of that footprint rather than the whole of it.
     */
    private const val CONTACT_SLACK = 1
}
