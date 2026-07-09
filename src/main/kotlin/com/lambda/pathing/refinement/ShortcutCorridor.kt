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

package com.lambda.pathing.refinement

import com.lambda.pathing.primitives.MoveTable
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.worldview.WorldView
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Exact corridor validation for any-angle shortcut segments.
 *
 * A flat shortcut is walkable iff every block column the player's inflated
 * footprint can touch along the straight segment is a valid *swept* standing
 * column ([MoveTable.isSweptStance] — full-cell passability, since the
 * footprint crosses columns off-center where the grid predicate's
 * center-passability proves nothing). Each column is tested once against
 * the segment capsule (Minkowski: segment vs cell expanded by the
 * half-width), which is both *sound* (no finite-step sampling gaps at
 * grazed corners) and dramatically cheaper than the dense box-collision
 * sweeps it replaces.
 */
object ShortcutCorridor {
    private const val EPSILON = 1.0E-6

    /**
     * First blocking column of the corridor from (x0, z0) to (x1, z1) at
     * node level [y], or null if the corridor is fully walkable.
     * [halfWidth] is the player half-width plus any clearance margin.
     */
    fun firstBlockedColumn(
        view: WorldView,
        x0: Double,
        z0: Double,
        x1: Double,
        z1: Double,
        y: Int,
        halfWidth: Double,
    ): FastVector? {
        val reach = halfWidth - EPSILON
        val minBx = floor(min(x0, x1) - reach).toInt()
        val maxBx = floor(max(x0, x1) + reach).toInt()
        val minBz = floor(min(z0, z1) - reach).toInt()
        val maxBz = floor(max(z0, z1) + reach).toInt()

        for (bx in minBx..maxBx) {
            for (bz in minBz..maxBz) {
                if (!segmentIntersectsBox(
                        x0, z0, x1, z1,
                        bx - reach, bz - reach,
                        bx + 1 + reach, bz + 1 + reach,
                    )
                ) continue
                if (!MoveTable.isSweptStance(view, bx, y, bz)) return fastVectorOf(bx, y, bz)
            }
        }
        return null
    }

    /** The segment passes within [halfWidth] of the given column's cell. */
    fun segmentTouchesColumn(
        x0: Double,
        z0: Double,
        x1: Double,
        z1: Double,
        columnX: Int,
        columnZ: Int,
        halfWidth: Double,
    ): Boolean {
        val reach = halfWidth - EPSILON
        return segmentIntersectsBox(
            x0, z0, x1, z1,
            columnX - reach, columnZ - reach,
            columnX + 1 + reach, columnZ + 1 + reach,
        )
    }

    /** 2D segment vs AABB intersection (slab clipping). */
    private fun segmentIntersectsBox(
        x0: Double,
        z0: Double,
        x1: Double,
        z1: Double,
        minX: Double,
        minZ: Double,
        maxX: Double,
        maxZ: Double,
    ): Boolean {
        var tMin = 0.0
        var tMax = 1.0
        val dx = x1 - x0
        val dz = z1 - z0

        // X slab.
        if (dx == 0.0) {
            if (x0 < minX || x0 > maxX) return false
        } else {
            var tEnter = (minX - x0) / dx
            var tExit = (maxX - x0) / dx
            if (tEnter > tExit) {
                val tmp = tEnter
                tEnter = tExit
                tExit = tmp
            }
            tMin = max(tMin, tEnter)
            tMax = min(tMax, tExit)
            if (tMin > tMax) return false
        }

        // Z slab.
        if (dz == 0.0) {
            if (z0 < minZ || z0 > maxZ) return false
        } else {
            var tEnter = (minZ - z0) / dz
            var tExit = (maxZ - z0) / dz
            if (tEnter > tExit) {
                val tmp = tEnter
                tEnter = tExit
                tExit = tmp
            }
            tMin = max(tMin, tEnter)
            tMax = min(tMax, tExit)
            if (tMin > tMax) return false
        }

        return true
    }
}
