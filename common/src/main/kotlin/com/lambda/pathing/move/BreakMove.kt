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

import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos

class BreakMove(
    override val pos: FastVector,
    override val hCost: Double,
    override val nodeType: NodeType,
    override val feetY: Double,
    override val cost: Double
) : Move() {
    override val name: String = "Break"

    override fun toString() = "BreakMove(pos=(${pos.toBlockPos().toShortString()}), hCost=$hCost, nodeType=$nodeType, feetY=$feetY, cost=$cost)"
}