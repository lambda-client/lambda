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

import com.lambda.context.SafeContext
import com.lambda.pathing.Node.Companion.toNode
import com.lambda.pathing.goal.Goal
import com.lambda.pathing.move.Move
import com.lambda.util.world.FastVector
import com.lambda.util.world.WorldUtils.traversable
import com.lambda.util.world.toBlockPos
import java.util.PriorityQueue

object AStar {
    fun SafeContext.findPathAStar(start: FastVector, goal: Goal): Path {
        val openSet = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Node>()
        val startNode = start.toNode(goal)
        startNode.gCost = 0.0
        openSet.add(startNode)

        while (openSet.isNotEmpty()) {
            val current = openSet.remove()
            if (goal.inGoal(current.pos)) {
//                println("Not yet considered nodes: ${openSet.size}")
//                println("Closed nodes: ${closedSet.size}")
                return current.createPathToSource()
            }

            closedSet.add(current)

            moveOptions(current.pos).forEach { move ->
                val successor = move.node(current.pos, goal)
                if (closedSet.contains(successor)) return@forEach
                val tentativeGCost = current.gCost + move.cost()
                if (tentativeGCost >= successor.gCost) return@forEach
                successor.predecessor = current
                successor.gCost = tentativeGCost
                openSet.add(successor)
            }
        }

        println("No path found")
        return Path()
    }

    private fun SafeContext.moveOptions(origin: FastVector): List<Move> {
        val originPos = origin.toBlockPos()
        return Move.entries.filter { move ->
            traversable(originPos.add(move.x, move.y, move.z))
        }
    }

//    class Move(
//        private val origin: FastVector,
//        val offset: FastVector,
//    ) {
//        val cost: Double = origin.distManhattan(offset).toDouble()
//
//        fun nextNode(goal: Goal) =
//            origin.plus(offset).toNode(goal)
//    }
}