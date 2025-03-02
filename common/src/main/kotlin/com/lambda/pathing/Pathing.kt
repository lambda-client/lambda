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
import com.lambda.pathing.goal.Goal
import com.lambda.pathing.move.Move
import com.lambda.pathing.move.MoveFinder
import com.lambda.pathing.move.MoveFinder.findPathType
import com.lambda.pathing.move.MoveFinder.getFeetY
import com.lambda.pathing.move.MoveFinder.moveOptions
import com.lambda.pathing.move.TraverseMove
import com.lambda.util.Communication.warn
import com.lambda.util.world.FastVector
import com.lambda.util.world.WorldUtils.isPathClear
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.y
import java.util.PriorityQueue

object Pathing {
    fun SafeContext.findPathAStar(start: FastVector, goal: Goal, config: PathingConfig): Path {
        MoveFinder.clean()
        val startedAt = System.currentTimeMillis()
        val openSet = PriorityQueue<Move>()
        val closedSet = mutableSetOf<FastVector>()
        val startFeetY = getFeetY(start.toBlockPos())
        val startNode = TraverseMove(start, goal.heuristic(start), findPathType(start), startFeetY, 0.0)
        startNode.gCost = 0.0
        openSet.add(startNode)

        println("Starting pathfinding at ${start.toBlockPos().toShortString()} to $goal")

        while (openSet.isNotEmpty() && startedAt + config.cutoffTimeout > System.currentTimeMillis()) {
            val current = openSet.remove()
            //            println("Considering node: ${current.pos.toBlockPos()}")
            if (goal.inGoal(current.pos)) {
                println("Not yet considered nodes: ${openSet.size}")
                println("Closed nodes: ${closedSet.size}")
                return current.createPathToSource()
            }

            closedSet.add(current.pos)

            moveOptions(current, goal, config).forEach { move ->
//                println("Considering move: $move")
                if (closedSet.contains(move.pos)) return@forEach
                val tentativeGCost = current.gCost + move.cost
                if (tentativeGCost >= move.gCost) return@forEach
                move.predecessor = current
                move.gCost = tentativeGCost
                openSet.add(move)
//                println("Using move: $move")
            }
        }

        warn("Only partial path found!")
        return if (openSet.isNotEmpty()) openSet.remove().createPathToSource() else Path()
    }

    fun SafeContext.thetaStarClearance(path: Path, config: PathingConfig): Path {
        if (path.moves.isEmpty()) return Path()

        val cleanedPath = Path()
        var currentIndex = 0

        while (currentIndex < path.moves.size) {
            // Always add the current node to the cleaned path
            val startMove = path.moves[currentIndex]
            cleanedPath.append(startMove)

            // Attempt to skip over as many nodes as possible
            // by checking if they share the same Y and have a clear path
            var nextIndex = currentIndex + 1
            while (nextIndex < path.moves.size) {
                val candidateMove = path.moves[nextIndex]

                // Only try to skip if both moves are on the same Y level
                if (startMove.pos.y != candidateMove.pos.y) break

                // Verify there's a clear path from the start move to the candidate
                if (
                    isPathClear(
                        startMove.pos.toBlockPos(),
                        candidateMove.pos.toBlockPos(),
                        config.pathClearanceCheckDistance
                    )
                ) nextIndex++ else break
            }

            // Move to the last node that was confirmed reachable
            // (subtract 1 because 'nextIndex' might have gone one too far)
            currentIndex = if (nextIndex > currentIndex + 1) nextIndex - 1 else nextIndex
        }

        return cleanedPath
    }


}