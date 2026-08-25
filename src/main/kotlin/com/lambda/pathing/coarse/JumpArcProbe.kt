/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.launch.ArcSample
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.VoxelPos
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import java.util.concurrent.ConcurrentHashMap
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
        packedReads: LongOpenHashSet,
    ) {
        /**
         * Materialized only when someone asks. The sweep records cells as packed longs so
         * a refused probe -- which is most probes -- never allocates a position object,
         * and an accepted one pays for its read set the first time repair wants it.
         */
        val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            unpackReads(packedReads)
        }
    }

    internal fun unpackReads(packed: LongOpenHashSet): Set<VoxelPos> {
        val result = HashSet<VoxelPos>(packed.size * 2)
        val iterator = packed.iterator()
        while (iterator.hasNext()) {
            val key = iterator.nextLong()
            result += VoxelPos(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key))
        }
        return result
    }

    /**
     * Launches for a relative move, cached: the solver reads nothing but the offset
     * geometry, so every origin of the same template solves to the same list. Keyed on the
     * exact rise the body flies, which quantizes to the handful of surface heights blocks
     * actually have.
     */
    private data class SolveKey(
        val dx: Int,
        val dz: Int,
        val riseBits: Long,
        val modes: List<LaunchMode>,
        val profile: BallisticProfile,
    )

    private val solutionCache = ConcurrentHashMap<SolveKey, List<LaunchSolution>>()

    private fun solutions(
        from: Stance,
        to: Stance,
        dx: Int,
        dz: Int,
        profile: BallisticProfile,
        modes: List<LaunchMode>,
        riseHeight: Double,
    ): List<LaunchSolution> {
        if (solutionCache.size > SOLUTION_CACHE_LIMIT) solutionCache.clear()
        return solutionCache.computeIfAbsent(
            SolveKey(dx, dz, riseHeight.toRawBits(), modes, profile)
        ) { LaunchSolver.solve(from, to, profile, modes, rise = riseHeight) }
    }

    fun probe(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        rise: Int,
        profile: BallisticProfile = BallisticProfile.VANILLA,
        modes: List<LaunchMode> = LaunchMode.entries,
        /**
         * The height the body really flies, and the height its feet really leave from.
         *
         * Both default to the whole-block reading, so terrain made of cubes behaves as it
         * always did. On a slab or a snow layer they do not agree with the stance, and the
         * arc has to be swept where the body actually goes -- an arc launched half a block
         * too high clears obstacles the real one hits.
         */
        riseHeight: Double = (rise).toDouble(),
        launchHeight: Double = from.y.toDouble(),
    ): Reachable? {
        val to = from.offset(dx, rise, dz)
        val reads = LongOpenHashSet()

        for (solution in solutions(from, to, dx, dz, profile, modes, riseHeight)) {
            val clearance = sweepClearance(
                view, from, dx, dz, solution.arc, solution.launchOffset, launchHeight, reads,
            ) ?: continue
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
    internal fun sweepClearance(
        view: CoarseVoxelView,
        from: Stance,
        dx: Int,
        dz: Int,
        arc: ArcSample,
        launchOffset: Double,
        launchHeight: Double,
        reads: LongOpenHashSet,
    ): Double? {
        val length = hypot(dx.toDouble(), dz.toDouble())
        if (length <= 0.0) return null
        val unitX = dx / length
        val unitZ = dz / length
        val launchX = from.x + 0.5 + unitX * launchOffset
        val launchZ = from.z + 0.5 + unitZ * launchOffset

        val heights = arc.heights
        val distances = arc.distances

        var clearance = CLEARANCE_CAP
        var previous = coreBox(launchX, launchHeight, launchZ)
        for (index in heights.indices) {
            val along = distances[index]
            val box = coreBox(
                launchX + unitX * along,
                launchHeight + heights[index],
                launchZ + unitZ * along,
            )
            val swept = previous.union(box)
            previous = box

            val margin = swept.expand(CLEARANCE_CAP)
            for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
                for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
                    for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
                        reads.add(BlockPos.asLong(x, y, z))
                        // Classified rather than fetched: almost every cell an arc crosses
                        // is air or a plain cube, and both are decidable without touching a
                        // VoxelShape -- whose box list is rebuilt on every read.
                        when (view.collisionClass(x, y, z)) {
                            CollisionClass.EMPTY -> {}

                            CollisionClass.UNKNOWN -> return null

                            CollisionClass.FULL -> {
                                if (swept.intersects(
                                        x.toDouble(), y.toDouble(), z.toDouble(),
                                        x + 1.0, y + 1.0, z + 1.0,
                                    )
                                ) return null
                                if (y + 1.0 > swept.minY + FLOOR_CONTACT_EPSILON) {
                                    clearance = minOf(clearance, gapToCell(swept, x, y, z))
                                }
                            }

                            CollisionClass.PARTIAL -> {
                                val shape = view.collisionShape(x, y, z) ?: return null
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
            }
        }
        return clearance
    }

    internal fun coreBox(x: Double, y: Double, z: Double) = Box(
        x - CORE_HALF_WIDTH, y, z - CORE_HALF_WIDTH,
        x + CORE_HALF_WIDTH, y + BODY_HEIGHT, z + CORE_HALF_WIDTH,
    )

    internal fun gap(a: Box, b: Box): Double {
        val dx = max(max(b.minX - a.maxX, a.minX - b.maxX), 0.0)
        val dy = max(max(b.minY - a.maxY, a.minY - b.maxY), 0.0)
        val dz = max(max(b.minZ - a.maxZ, a.minZ - b.maxZ), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** [gap] against the unit cube at (x, y, z), with no box constructed for it. */
    private fun gapToCell(a: Box, x: Int, y: Int, z: Int): Double {
        val dx = max(max(x - a.maxX, a.minX - (x + 1.0)), 0.0)
        val dy = max(max(y - a.maxY, a.minY - (y + 1.0)), 0.0)
        val dz = max(max(z - a.maxZ, a.minZ - (z + 1.0)), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    internal const val CORE_HALF_WIDTH = 0.2
    private const val BODY_HEIGHT = 1.8
    private const val CLEARANCE_CAP = 0.5
    private const val FLOOR_CONTACT_EPSILON = 1.0E-7

    /** Rise heights quantize to block surface steps, so growth past this is a leak. */
    private const val SOLUTION_CACHE_LIMIT = 100_000
}
