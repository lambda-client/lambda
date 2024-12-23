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

package com.lambda.gui.component.core

import com.lambda.graphics.renderer.gui.rect.FilledRectRenderer.filledRect
import com.lambda.gui.component.layout.Layout
import com.lambda.util.math.Rect
import java.awt.Color

class FilledRect(
    owner: Layout
) : Layout(owner, true, true) {
    var rectangle = Rect.ZERO

    var leftTopRadius = 0.0
    var rightTopRadius = 0.0
    var rightBottomRadius = 0.0
    var leftBottomRadius = 0.0

    var leftTopColor: Color = Color.WHITE
    var rightTopColor: Color = Color.WHITE
    var rightBottomColor: Color = Color.WHITE
    var leftBottomColor: Color = Color.WHITE

    var shade = false

    private val updateActions = mutableListOf<FilledRect.() -> Unit>()

    fun onUpdate(block: FilledRect.() -> Unit) {
        updateActions += block
    }

    init {
        properties.interactionPassthrough = true

        onRender {
            updateActions.forEach { action ->
                action(this@FilledRect)
            }

            // make it pressable
            position = rectangle.leftTop
            size = rectangle.size

            filledRect(
                rectangle,
                leftTopRadius,
                rightTopRadius,
                rightBottomRadius,
                leftBottomRadius,
                leftTopColor,
                rightTopColor,
                rightBottomColor,
                leftBottomColor,
                shade
            )
        }
    }

    fun setRadius(radius: Double) {
        leftTopRadius = radius
        rightTopRadius = radius
        rightBottomRadius = radius
        leftBottomRadius = radius
    }

    fun setColor(color: Color) {
        leftTopColor = color
        rightTopColor = color
        rightBottomColor = color
        leftBottomColor = color
    }

    fun setColorH(colorL: Color, colorR: Color) {
        leftTopColor = colorL
        rightTopColor = colorR
        rightBottomColor = colorR
        leftBottomColor = colorL
    }

    fun setColorV(colorT: Color, colorB: Color) {
        leftTopColor = colorT
        rightTopColor = colorT
        rightBottomColor = colorB
        leftBottomColor = colorB
    }

    companion object {
        /**
         * Creates a [FilledRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.rect(block: FilledRect.() -> Unit = {}) =
            FilledRect(this).apply(children::add).apply(block)
    }
}
