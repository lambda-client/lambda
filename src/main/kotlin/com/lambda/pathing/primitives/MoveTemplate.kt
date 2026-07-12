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

import com.lambda.worldview.WorldView

/**
 * One motion primitive as data (research plan §4.3): a displacement plus the
 * voxel conditions that must hold relative to the move's origin anchor. The
 * same template answers both search directions — successors stamp it at the
 * origin, predecessors stamp it at (target − Δ) — which is what makes
 * asymmetric moves (drops) enumerable backward without a reverse move
 * existing.
 *
 * Executor references and entry envelopes (the remaining fields of the §4.3
 * tuple) land with the maneuver layer (WP3); until then the executor keeps
 * inferring segment types from node deltas.
 */
class MoveTemplate(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val cost: Double,
    val cells: List<Cell>,
) {
    /**
     * Condition vocabulary over [WorldView] traits — deliberately the same
     * three predicates the reference walking model uses, so template
     * equivalence is checkable cell-for-cell.
     */
    enum class Condition {
        /** Full-square standable top face (a node's support block). */
        SUPPORT,

        /**
         * Player-footprint slice unobstructed: center column of the voxel
         * clear AND the voxel below not poking above its top (fences).
         * Reads two voxels: the cell and the cell below.
         */
        SLICE,

        /** Center column of the voxel clear (head-height cells). */
        HEAD,
    }

    class Cell(val ox: Int, val oy: Int, val oz: Int, val condition: Condition)

    /** All conditions hold with the origin anchor at (x, y, z). */
    fun matches(view: WorldView, x: Int, y: Int, z: Int): Boolean {
        return matchesFrom(view, x, y, z, 0)
    }

    /**
     * Incoming enumeration has already checked the shared target stance once.
     * Every MoveTable template stores those three cells first, so it can avoid
     * re-reading the same target floor/feet/head for every candidate template.
     */
    internal fun matchesAfterTargetStance(view: WorldView, x: Int, y: Int, z: Int): Boolean =
        matchesFrom(view, x, y, z, TARGET_STANCE_CELL_COUNT)

    private fun matchesFrom(view: WorldView, x: Int, y: Int, z: Int, startIndex: Int): Boolean {
        for (index in startIndex until cells.size) {
            val cell = cells[index]
            val cx = x + cell.ox
            val cy = y + cell.oy
            val cz = z + cell.oz
            when (cell.condition) {
                Condition.SUPPORT -> if (!view.traits(cx, cy, cz).standableFullTop) return false
                Condition.SLICE -> {
                    if (!view.traits(cx, cy, cz).centerPassable) return false
                    if (view.traits(cx, cy - 1, cz).intrudesAbove) return false
                }
                Condition.HEAD -> if (!view.traits(cx, cy, cz).centerPassable) return false
            }
        }
        return true
    }

    /**
     * Origin-relative offsets of every voxel this template's conditions
     * *read* (SLICE reads the cell below too). The union over a move set —
     * inverted — is the exact invalidation extent for a changed block.
     */
    fun readOffsets(): Sequence<Triple<Int, Int, Int>> = sequence {
        for (cell in cells) {
            yield(Triple(cell.ox, cell.oy, cell.oz))
            if (cell.condition == Condition.SLICE) yield(Triple(cell.ox, cell.oy - 1, cell.oz))
        }
    }

    private companion object {
        const val TARGET_STANCE_CELL_COUNT = 3
    }
}
