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

import com.lambda.pathing.Node
import com.lambda.pathing.Node.Companion.toNode
import com.lambda.pathing.goal.Goal
import com.lambda.util.world.FastVector
import com.lambda.util.world.offset
import kotlin.math.abs

enum class Move(val x: Int, val y: Int, val z: Int) {
    TRAVERSE_NORTH(0, 0, -1),
    TRAVERSE_NORTH_EAST(1, 0, -1),
    TRAVERSE_EAST(1, 0, 0),
    TRAVERSE_SOUTH_EAST(1, 0, 1),
    TRAVERSE_SOUTH(0, 0, 1),
    TRAVERSE_SOUTH_WEST(-1, 0, 1),
    TRAVERSE_WEST(-1, 0, 0),
    TRAVERSE_NORTH_WEST(-1, 0, -1),
    PILLAR(0, 1, 0),
    ASCEND_NORTH(0, 1, -1),
    ASCEND_EAST(1, 1, 0),
    ASCEND_SOUTH(0, 1, 1),
    ASCEND_WEST(-1, 1, 0),
    FALL(0, -1, 0),
    DESCEND_NORTH(0, -1, -1),
    DESCEND_EAST(1, -1, 0),
    DESCEND_SOUTH(0, -1, 1),
    DESCEND_WEST(-1, -1, 0);

    fun cost(): Int = abs(x) + abs(y) + abs(z)

    // ToDo: Use DirectionMask
    fun node(origin: FastVector, goal: Goal): Node = origin.offset(x, y, z).toNode(goal)
}