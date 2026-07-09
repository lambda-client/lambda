/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.primitives

import com.lambda.config.blocks.PlannerConfig
import com.lambda.pathing.primitives.MoveTemplate.Cell
import com.lambda.pathing.primitives.MoveTemplate.Condition
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.WorldView
import kotlin.math.sqrt

/**
 * Builds the enabled primitive set for a planner config and answers every
 * question the search core asks *derived from that set*: successor and
 * predecessor stamps, heuristic admissibility caps, and invalidation extents.
 * Nothing here is hand-maintained per move type — adding a template updates
 * caps and affected regions by construction (theory note T1's principle).
 */
object MoveTable {
    /** One built move set: the templates plus everything derived from them. */
    class MoveSet internal constructor(
        val templates: List<MoveTemplate>,
        val caps: HeuristicCaps,
        /** Flattened (x, y, z) triples: every origin-relative voxel any edge of a node reads. */
        private val readOffsets: IntArray,
    ) {
        fun successors(view: WorldView, node: FastVector): Map<FastVector, Double> {
            val ox = node.x
            val oy = node.y
            val oz = node.z
            if (!isStance(view, ox, oy, oz)) return emptyMap()

            val result = HashMap<FastVector, Double>(16)
            for (t in templates) {
                if (t.matches(view, ox, oy, oz)) {
                    result[fastVectorOf(ox + t.dx, oy + t.dy, oz + t.dz)] = t.cost
                }
            }
            return result
        }

        /**
         * True incoming edges: every origin whose forward template lands
         * exactly on [node], validated at that origin. The inverse stamp —
         * no symmetric-traversability assumption anywhere.
         */
        fun predecessors(view: WorldView, node: FastVector): Map<FastVector, Double> {
            val tx = node.x
            val ty = node.y
            val tz = node.z
            if (!isStance(view, tx, ty, tz)) return emptyMap()

            val result = HashMap<FastVector, Double>(16)
            for (t in templates) {
                val ox = tx - t.dx
                val oy = ty - t.dy
                val oz = tz - t.dz
                if (isStance(view, ox, oy, oz) && t.matches(view, ox, oy, oz)) {
                    result[fastVectorOf(ox, oy, oz)] = t.cost
                }
            }
            return result
        }

        /**
         * Exact node invalidation for a changed block: a node's edges read
         * voxel v iff v − (read offset) is the node — so invert the derived
         * read extents. Replaces the hand-maintained conservative box.
         */
        fun affectedNodes(changedX: Int, changedY: Int, changedZ: Int): Set<FastVector> = buildSet(readOffsets.size / 3) {
            var i = 0
            while (i < readOffsets.size) {
                add(
                    fastVectorOf(
                        changedX - readOffsets[i],
                        changedY - readOffsets[i + 1],
                        changedZ - readOffsets[i + 2],
                    )
                )
                i += 3
            }
        }
    }

    data class HeuristicCaps(
        val minCostPerHorizontalBlock: Double,
        val minCostPerAscendedBlock: Double,
        val minCostPerDescendedBlock: Double,
    )

    /**
     * A valid standing node: full-square support below, feet slice clear,
     * head center clear. The caller-side invariant both stamp directions
     * check once per node (templates carry the *target* stance in their
     * cells; the origin stance is checked here).
     */
    fun isStance(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        view.traits(x, y - 1, z).standableFullTop &&
            view.traits(x, y, z).centerPassable && !view.traits(x, y - 1, z).intrudesAbove &&
            view.traits(x, y + 1, z).centerPassable

    /**
     * A column a swept, off-center footprint may cross: full-square support
     * below with no intrusion, and *fully* passable feet and head voxels.
     * [isStance]'s center-passability is only valid where the agent stands
     * at column centers; an any-angle corridor sweeps the whole column, so
     * partial shapes (fences, walls, open doors) must reject it.
     */
    fun isSweptStance(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        view.traits(x, y - 1, z).standableFullTop && !view.traits(x, y - 1, z).intrudesAbove &&
            view.traits(x, y, z).passable &&
            view.traits(x, y + 1, z).passable

    fun build(config: PlannerConfig): MoveSet {
        val templates = buildList {
            forEachCardinal { dx, dz ->
                walk(dx, dz)
                if (config.allowVertical) {
                    stepUp(dx, dz)
                    for (depth in 1..config.maxDropHeight.coerceAtLeast(1)) drop(dx, dz, depth)
                    if (config.allowJump) {
                        gapJump(dx, dz, rise = 0)
                        gapJump(dx, dz, rise = 1)
                    }
                }
            }
            if (config.allowDiagonal) {
                forEachDiagonal { dx, dz -> walkDiagonal(dx, dz) }
            }
        }
        return MoveSet(templates, deriveCaps(templates), deriveReadOffsets(templates))
    }

    // ------------------------------------------------------------------
    // Template constructors. Cell semantics mirror the reference walking
    // model predicate-for-predicate; MoveTemplateDifferentialTest holds the
    // two implementations equal on randomized worlds.
    // ------------------------------------------------------------------

    /** The target-node stance: what every move requires where it lands. */
    private fun stanceCells(dx: Int, dy: Int, dz: Int): List<Cell> = listOf(
        Cell(dx, dy - 1, dz, Condition.SUPPORT),
        Cell(dx, dy, dz, Condition.SLICE),
        Cell(dx, dy + 1, dz, Condition.HEAD),
    )

    private fun MutableList<MoveTemplate>.walk(dx: Int, dz: Int) {
        add(MoveTemplate(dx, 0, dz, MoveCosts.CARDINAL, stanceCells(dx, 0, dz)))
    }

    /**
     * Diagonal walk: both flanking columns must be player-passable at feet
     * and head height (support not required — the checkerboard walk), on top
     * of the target stance.
     */
    private fun MutableList<MoveTemplate>.walkDiagonal(dx: Int, dz: Int) {
        add(
            MoveTemplate(
                dx, 0, dz, MoveCosts.DIAGONAL,
                stanceCells(dx, 0, dz) + listOf(
                    Cell(dx, 0, 0, Condition.SLICE),
                    Cell(dx, 1, 0, Condition.HEAD),
                    Cell(0, 0, dz, Condition.SLICE),
                    Cell(0, 1, dz, Condition.HEAD),
                ),
            )
        )
    }

    /** Step-up: jump headroom above the origin's head (+2 slice). */
    private fun MutableList<MoveTemplate>.stepUp(dx: Int, dz: Int) {
        add(
            MoveTemplate(
                dx, 1, dz, MoveCosts.STEP_UP,
                stanceCells(dx, 1, dz) + Cell(0, 2, 0, Condition.SLICE),
            )
        )
    }

    /**
     * Walk-off drop of [depth] blocks: the target column must be a clear
     * fall corridor from one above the origin's feet (the step-off pass-through,
     * including the head slice) down to the landing feet.
     */
    private fun MutableList<MoveTemplate>.drop(dx: Int, dz: Int, depth: Int) {
        val corridor = buildList {
            // Slices from one above the origin's feet down to one above the
            // landing feet (the landing feet slice is in the target stance).
            for (k in 1 downTo 1 - depth) add(Cell(dx, k, dz, Condition.SLICE))
        }
        add(MoveTemplate(dx, -depth, dz, MoveCosts.drop(depth), stanceCells(dx, -depth, dz) + corridor))
    }

    /**
     * Gap jump two forward (rise 0 or 1): launch headroom at the origin plus
     * a clear arc over the mid column — feet and head slices, and for the
     * rising variant the apex slice above the landing height as well.
     */
    private fun MutableList<MoveTemplate>.gapJump(dx: Int, dz: Int, rise: Int) {
        val arc = buildList {
            add(Cell(0, 2, 0, Condition.SLICE))
            add(Cell(dx, 0, dz, Condition.SLICE))
            add(Cell(dx, 1, dz, Condition.SLICE))
            if (rise == 1) add(Cell(dx, 2, dz, Condition.SLICE))
        }
        add(
            MoveTemplate(
                2 * dx, rise, 2 * dz,
                if (rise == 0) MoveCosts.JUMP else MoveCosts.JUMP_UP,
                stanceCells(2 * dx, rise, 2 * dz) + arc,
            )
        )
    }

    // ------------------------------------------------------------------
    // Derivations.
    // ------------------------------------------------------------------

    private fun deriveCaps(templates: List<MoveTemplate>): HeuristicCaps {
        var horizontal = Double.POSITIVE_INFINITY
        var ascended = Double.POSITIVE_INFINITY
        var descended = Double.POSITIVE_INFINITY
        for (t in templates) {
            val dxz = sqrt((t.dx * t.dx + t.dz * t.dz).toDouble())
            if (dxz > 0) horizontal = minOf(horizontal, t.cost / dxz)
            if (t.dy > 0) ascended = minOf(ascended, t.cost / t.dy)
            if (t.dy < 0) descended = minOf(descended, t.cost / -t.dy)
        }
        return HeuristicCaps(horizontal, ascended, descended)
    }

    private fun deriveReadOffsets(templates: List<MoveTemplate>): IntArray {
        val offsets = HashSet<Triple<Int, Int, Int>>()
        // The origin stance itself reads these regardless of move type.
        offsets += Triple(0, -1, 0)
        offsets += Triple(0, 0, 0)
        offsets += Triple(0, 1, 0)
        templates.forEach { it.readOffsets().forEach(offsets::add) }

        val flat = IntArray(offsets.size * 3)
        var i = 0
        for ((x, y, z) in offsets) {
            flat[i] = x
            flat[i + 1] = y
            flat[i + 2] = z
            i += 3
        }
        return flat
    }

    private inline fun forEachCardinal(action: (dx: Int, dz: Int) -> Unit) {
        action(-1, 0)
        action(1, 0)
        action(0, -1)
        action(0, 1)
    }

    private inline fun forEachDiagonal(action: (dx: Int, dz: Int) -> Unit) {
        action(-1, -1)
        action(-1, 1)
        action(1, -1)
        action(1, 1)
    }
}
