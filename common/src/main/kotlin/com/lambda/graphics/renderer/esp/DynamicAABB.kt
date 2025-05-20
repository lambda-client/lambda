/*
 * Copyright 2024 Lambda
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

package com.lambda.graphics.renderer.esp

import com.lambda.util.extension.prevPos
import com.lambda.util.math.minus
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box

class DynamicAABB {
    private var prev: Box? = null
    private var curr: Box? = null

    fun update(box: Box): DynamicAABB {
        prev = curr ?: box
        curr = box

        return this
    }

    fun reset() {
        prev = null
        curr = null
    }

    fun getBoxPair(): Pair<Box, Box>? {
        prev?.let { previous ->
            curr?.let { current ->
                return previous to current
            }
        }

        return null
    }

    fun center() = curr?.center ?: prev?.center ?: throw NullPointerException("No box is set")

    companion object {
        val Entity.dynamicBox
            get() = DynamicAABB().apply {
                update(boundingBox.offset(prevPos - pos))
                update(boundingBox)
            }
    }
}
