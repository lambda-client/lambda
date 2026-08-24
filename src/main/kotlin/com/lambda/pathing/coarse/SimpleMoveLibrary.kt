/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.coarse

import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.VoxelPos
import kotlin.math.abs
import kotlin.math.hypot

class SimpleMoveLibrary private constructor(
    val catalog: MovementCatalog,
    val templates: List<MotionTemplate>,
    private val readOffsets: Set<VoxelPos>,
    val heuristicCaps: HeuristicCaps,
) {
    private val minReadX = readOffsets.minOf(VoxelPos::x)
    private val maxReadX = readOffsets.maxOf(VoxelPos::x)
    private val minReadZ = readOffsets.minOf(VoxelPos::z)
    private val maxReadZ = readOffsets.maxOf(VoxelPos::z)

    data class HeuristicCaps(
        /** Cheapest lower-bound cost for one unmatched X or Z block. */
        val axisTicksPerBlock: Double,
        /** Cheapest lower-bound cost for changing X and Z by one together. */
        val diagonalTicksPerPair: Double,
        val ascentTicksPerBlock: Double,
        val descentTicksPerBlock: Double,
        /**
         * Cheapest ticks per block of straight-line horizontal progress, over every move.
         *
         * The octile pair carries an assumption from eight-connected grids: that progress is
         * made in whole steps and diagonal ones cost their own rate. Once the graph offers
         * arbitrary offsets that decomposition is no longer the tightest bound available --
         * and worse, admitting cheap off-axis moves forces the diagonal cap down to stay
         * consistent, weakening the heuristic everywhere including on straight runs.
         *
         * A straight-line rate has no such coupling: every template satisfies it by
         * construction, and it is exact for the moves the octile form has to discount.
         */
        val straightTicksPerBlock: Double,
    )

    /**
     * Whether the graph will enumerate edges out of [stance].
     *
     * Standing on a floor is one way to be somewhere, not the only one, so any registered
     * movement may also claim a cell. Keeping the walking test first means the common case
     * costs three reads and no dispatch.
     */
    fun isStance(view: CoarseVoxelView, stance: Stance): Boolean {
        if (stance.y !in view.simulableStanceY) return false
        val support = view.voxel(stance.x, stance.y - 1, stance.z)
        val stands = view.standingSurface(stance.x, stance.y - 1, stance.z) != null &&
            !support.intrudesAbove &&
            view.voxel(stance.x, stance.y, stance.z).centerPassable &&
            view.voxel(stance.x, stance.y + 1, stance.z).centerPassable
        return stands || catalog.movements.any { it.occupies(view, stance) }
    }

    fun edgesFrom(view: CoarseVoxelView, origin: Stance): List<CoarseEdge> {
        if (!isStance(view, origin)) return emptyList()
        return templates.mapNotNull { it.edge(view, origin) }
    }

    fun edgesTo(view: CoarseVoxelView, target: Stance): List<CoarseEdge> {
        if (!isStance(view, target)) return emptyList()
        return templates.mapNotNull { template ->
            val origin = target.offset(-template.dx, -template.dy, -template.dz)
            if (isStance(view, origin)) template.edge(view, origin) else null
        }
    }

    fun successorCosts(view: CoarseVoxelView, origin: Stance): Map<Stance, Double> =
        edgesFrom(view, origin).minimumCostsBy { it.to }

    fun predecessorCosts(view: CoarseVoxelView, target: Stance): Map<Stance, Double> =
        edgesTo(view, target).minimumCostsBy { it.from }

    fun heuristic(from: Stance, to: Stance): Double {
        val dx = abs(to.x - from.x)
        val dz = abs(to.z - from.z)
        val paired = minOf(dx, dz)
        val straight = maxOf(dx, dz) - paired
        // The larger of two admissible bounds is admissible, and consistent if both are,
        // so nothing is lost by asking each and keeping the better answer. They lead in
        // different places: octile is tighter where movement really is step-shaped, the
        // straight-line rate where the graph can cut a corner an octile walk cannot.
        val octile = paired * heuristicCaps.diagonalTicksPerPair.finiteOrZero() +
            straight * heuristicCaps.axisTicksPerBlock.finiteOrZero()
        val straightLine = hypot(dx.toDouble(), dz.toDouble()) *
            heuristicCaps.straightTicksPerBlock.finiteOrZero()
        val horizontal = maxOf(octile, straightLine)
        val dy = to.y - from.y
        val vertical = when {
            dy > 0 -> dy * heuristicCaps.ascentTicksPerBlock.finiteOrZero()
            dy < 0 -> -dy * heuristicCaps.descentTicksPerBlock.finiteOrZero()
            else -> 0.0
        }
        return maxOf(horizontal, vertical)
    }

    fun affectedOrigins(changed: VoxelPos): Set<Stance> = buildSet(readOffsets.size) {
        for ((x, y, z) in readOffsets) {
            add(Stance(changed.x - x, changed.y - y, changed.z - z))
        }
    }

    /** Known origins whose possible edge reads overlap a refreshed chunk. */
    fun affectedOrigins(chunk: PathingChunk, candidates: Iterable<Stance>): Set<Stance> {
        val chunkMinX = chunk.x shl 4
        val chunkMaxX = chunkMinX + 15
        val chunkMinZ = chunk.z shl 4
        val chunkMaxZ = chunkMinZ + 15
        val originX = (chunkMinX - maxReadX)..(chunkMaxX - minReadX)
        val originZ = (chunkMinZ - maxReadZ)..(chunkMaxZ - minReadZ)
        return candidates.filterTo(HashSet()) { it.x in originX && it.z in originZ }
    }

    private fun List<CoarseEdge>.minimumCostsBy(node: (CoarseEdge) -> Stance): Map<Stance, Double> {
        val result = HashMap<Stance, Double>(size)
        for (edge in this) result.merge(node(edge), edge.lowerBoundTicks, ::minOf)
        return result
    }

    private fun Double.finiteOrZero() = if (isFinite()) this else 0.0

    companion object {
        /**
         * Builds the move set from the registered movements.
         *
         * This used to be one long hardcoded list of every stride, rise, step-down and jump
         * the planner knew, which meant a new kind of motion was a change here rather than
         * a new file. The catalogue owns that now; what remains in this class is the graph
         * machinery -- matching templates against terrain, enumerating backward, and
         * working out which stances a block change invalidates -- none of which cares what
         * the movements are.
         */
        fun build(
            costs: CoarseMoveCosts,
            options: SimpleMoveOptions = SimpleMoveOptions(),
            ballistics: BallisticProfile = BallisticProfile.VANILLA,
            movements: List<Movement> = MovementCatalog.REGISTERED,
        ): SimpleMoveLibrary = of(MovementCatalog.build(costs, options, ballistics, movements))

        fun of(catalog: MovementCatalog): SimpleMoveLibrary {
            val templates = catalog.templates
            val offsets = templates.flatMapTo(HashSet()) { it.readOffsets().toList() }
            return SimpleMoveLibrary(catalog, templates, offsets, deriveCaps(templates))
        }

        private fun deriveCaps(templates: List<MotionTemplate>): HeuristicCaps {
            var axis = Double.POSITIVE_INFINITY
            var diagonal = Double.POSITIVE_INFINITY
            var ascent = Double.POSITIVE_INFINITY
            var descent = Double.POSITIVE_INFINITY
            var straight = Double.POSITIVE_INFINITY
            for (template in templates) {
                val reach = hypot(template.dx.toDouble(), template.dz.toDouble())
                if (reach > 0.0) straight = minOf(straight, template.minimumTicks / reach)
            }
            for (template in templates) {
                val dx = abs(template.dx)
                val dz = abs(template.dz)
                when {
                    dx > 0 && dz == 0 -> axis = minOf(axis, template.minimumTicks / dx)
                    dz > 0 && dx == 0 -> axis = minOf(axis, template.minimumTicks / dz)
                    dx == dz && dx > 0 -> diagonal = minOf(diagonal, template.minimumTicks / dx)
                }
                if (template.dy > 0) ascent = minOf(ascent, template.minimumTicks / template.dy)
                if (template.dy < 0) descent = minOf(descent, template.minimumTicks / abs(template.dy))
            }
            // Alternating opposing diagonals can make pure axial progress, while two
            // axial moves can make diagonal progress. Closing the caps under both
            // combinations keeps this octile lower bound consistent for every edge.
            axis = minOf(axis, diagonal)
            diagonal = minOf(diagonal, 2.0 * axis)

            // An off-axis template belongs to neither bucket above, but it still closes
            // octile distance -- three across and one to the side is one diagonal pair plus
            // two straights. Leaving those out let the heuristic claim more progress than
            // such a move costs, which is inconsistency: D* answers that by expanding a node
            // it has already settled, and the route it returns is no longer the shortest.
            //
            // The diagonal cap gives way first, and only far enough. Both caps could simply
            // be scaled together, but the axis one carries the whole heuristic over a long
            // straight run -- which is most of any real path -- so spending it to satisfy a
            // constraint the diagonal term is responsible for makes the search markedly
            // slower for nothing. Both give way only when the diagonal alone cannot, since
            // an axis move must never be priced above a diagonal one.
            //
            // Shrinking only, so a constraint satisfied earlier in the pass stays satisfied.
            for (template in templates) {
                val a = abs(template.dx)
                val b = abs(template.dz)
                if (a == 0 || b == 0 || a == b) continue
                val pairs = minOf(a, b)
                val straights = abs(a - b)
                val claimed = pairs * diagonal + straights * axis
                if (!claimed.isFinite() || claimed <= template.minimumTicks) continue

                val roomForDiagonal = (template.minimumTicks - straights * axis) / pairs
                if (roomForDiagonal >= axis) {
                    diagonal = roomForDiagonal
                } else {
                    val even = template.minimumTicks / (pairs + straights)
                    axis = even
                    diagonal = even
                }
            }

            return HeuristicCaps(axis, diagonal, ascent, descent, straight)
        }
    }
}
