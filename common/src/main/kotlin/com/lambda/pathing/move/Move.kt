/*
 * Copyright 2025 Lambda
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

package com.lambda.pathing.move

import com.lambda.pathing.Path
import com.lambda.task.Task
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import net.minecraft.util.math.Vec3d

abstract class Move : Comparable<Move>, Task<Unit>() {
    abstract val pos: FastVector
    abstract val hCost: Double
    abstract val nodeType: NodeType
    abstract val feetY: Double
    abstract val cost: Double

    var predecessor: Move? = null
    var gCost: Double = Double.POSITIVE_INFINITY

    // use updateable lazy and recompute on gCost change
    private val fCost get() = gCost + hCost

    val bottomPos: Vec3d get() = Vec3d.ofBottomCenter(pos.toBlockPos())

    override fun compareTo(other: Move) =
        fCost.compareTo(other.fCost)

    fun createPathToSource(): Path {
        val path = Path()
        var current: Move? = this
        while (current != null) {
            path.prepend(current)
            current = current.predecessor
        }
        return path
    }
}