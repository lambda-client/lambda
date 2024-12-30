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

package com.lambda.graphics.pipeline

import com.lambda.graphics.renderer.gui.font.core.GlyphInfo
import com.lambda.util.math.Rect
import com.lambda.util.math.transform

object ScissorAdapter {
    private var stack = ArrayDeque<Rect>()
    private val scissorInstance = ScissorRect()

    fun scissor(rect: Rect, block: () -> Unit) {
        // clamp corners so children scissor boxes can't overlap parent
        val processed = stack.lastOrNull()?.let(rect::clamp) ?: rect

        // push the stack
        stack.add(processed)

        // do render tasks
        block()

        // pop the stack
        stack.removeLast()
    }

    fun scissorTest(x1: Double, y1: Double, x2: Double, y2: Double, glyph: GlyphInfo? = null): ScissorRect {
        reset()

        run {
            val entry = stack.lastOrNull() ?: return@run

            val width = x2 - x1
            val height = y2 - y1

            if (width <= 0 || height <= 0) {
                nullify()
                return@run
            }

            val si = scissorInstance

            si.x1 = transform(entry.left, x1, x2, glyph?.u1 ?: 0.0, glyph?.u2 ?: 1.0)
            si.y1 = transform(entry.top, y1, y2, glyph?.v1 ?: 0.0, glyph?.v2 ?: 1.0)
            si.x2 = transform(entry.right, x1, x2, glyph?.u1 ?: 0.0, glyph?.u2 ?: 1.0)
            si.y2 = transform(entry.bottom, y1, y2, glyph?.v1 ?: 0.0, glyph?.v2 ?: 1.0)
        }

        return scissorInstance
    }

    private fun reset() = scissorInstance.apply {
        x1 = 0.0
        y1 = 0.0
        x2 = 1.0
        y2 = 1.0
    }

    private fun nullify() = scissorInstance.apply {
        x1 = 0.0
        y1 = 0.0
        x2 = 0.0
        y2 = 0.0
    }

    class ScissorRect {
        var x1 = 0.0
        var y1 = 0.0
        var x2 = 1.0
        var y2 = 1.0
    }
}