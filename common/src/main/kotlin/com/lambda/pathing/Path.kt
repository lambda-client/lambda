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

import com.lambda.graphics.renderer.esp.builders.ofBox
import com.lambda.graphics.renderer.esp.global.StaticESP
import com.lambda.pathing.move.Move
import com.lambda.util.collections.updatableLazy
import com.lambda.util.math.component1
import com.lambda.util.math.component2
import com.lambda.util.math.component3
import com.lambda.util.math.setAlpha
import com.lambda.util.world.dist
import com.lambda.util.world.toBlockPos
import net.minecraft.util.math.Box
import java.awt.Color

data class Path(
    val moves: ArrayDeque<Move> = ArrayDeque(),
) {
    fun append(move: Move) {
        moves.addLast(move)
        length.clear()
    }

    fun prepend(move: Move) {
        moves.addFirst(move)
        length.clear()
    }

    private val length = updatableLazy {
        moves.zipWithNext { a, b -> a.pos dist b.pos }.sum()
    }

    fun render(renderer: StaticESP, color: Color) {
        moves.zipWithNext { current, next ->
            val start = current.pos.toBlockPos().toCenterPos()
            val end = next.pos.toBlockPos().toCenterPos()
            val direction = end.subtract(start)
            val distance = direction.length()
            if (distance <= 0) return@zipWithNext

            val stepSize = 0.2
            val steps = (distance / stepSize).toInt()
            val stepDirection = direction.normalize().multiply(stepSize)

            var currentPos = start

            (0 until steps).forEach { _ ->
                val (x, y, z) = currentPos
                val d = 0.03
                val box = Box(x - d, y - d, z - d, x + d, y + d, z + d)
                renderer.ofBox(box, color.brighter().setAlpha(0.25), color.darker())
                currentPos = currentPos.add(stepDirection)
            }
        }
    }

    fun length() = length.value

    val size get() = moves.size

    override fun toString() =
        moves.joinToString(" -> ") { "(${it.pos.toBlockPos().toShortString()})" }
}