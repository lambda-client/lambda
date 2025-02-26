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

package com.lambda.pathing

import com.lambda.pathing.goal.Goal
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos

data class Node(
    val pos: FastVector,
    var predecessor: Node? = null,
    var gCost: Double = Double.POSITIVE_INFINITY,
    val hCost: Double
) : Comparable<Node> {
    // use updateable lazy and recompute on gCost change
    private val fCost get() = gCost + hCost

    override fun compareTo(other: Node) =
        fCost.compareTo(other.fCost)

    fun createPathToSource(): Path {
        val path = Path()
        var current: Node? = this
        while (current != null) {
            path.prepend(current)
            current = current.predecessor
        }
        return path
    }

    override fun toString() = "Node(pos=${pos.toBlockPos().toShortString()}, gCost=$gCost, hCost=$hCost)"

    companion object {
        fun FastVector.toNode(goal: Goal) =
            Node(this, hCost = goal.heuristic(this))
    }
}