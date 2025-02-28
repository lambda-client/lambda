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
) : Layout(owner) {
    @UIRenderPr0p3rty var rectangle = Rect.ZERO

    @UIRenderPr0p3rty var leftTopRadius = 0.0
    @UIRenderPr0p3rty var rightTopRadius = 0.0
    @UIRenderPr0p3rty var rightBottomRadius = 0.0
    @UIRenderPr0p3rty var leftBottomRadius = 0.0

    @UIRenderPr0p3rty var leftTopColor: Color = Color.WHITE
    @UIRenderPr0p3rty var rightTopColor: Color = Color.WHITE
    @UIRenderPr0p3rty var rightBottomColor: Color = Color.WHITE
    @UIRenderPr0p3rty var leftBottomColor: Color = Color.WHITE

    @UIRenderPr0p3rty var shade = false

    init {
        properties.interactionPassthrough = true

        onUpdate {
            // make it pressable
            position = rectangle.leftTop
            size = rectangle.size
        }

        onRender {
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

    fun setRadius(
        leftTopRadius: Double,
        rightTopRadius: Double,
        rightBottomRadius: Double,
        leftBottomRadius: Double,
    ) {
        this.leftTopRadius = leftTopRadius
        this.rightTopRadius = rightTopRadius
        this.rightBottomRadius = rightBottomRadius
        this.leftBottomRadius = leftBottomRadius
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
        fun Layout.rect(
            block: FilledRect.() -> Unit = {}
        ) = FilledRect(this).apply(children::add).apply(block)

        /**
         * Adds a [FilledRect] behind given [layout]
         */
        @UIBuilder
        fun Layout.rectBehind(
            layout: Layout,
            block: FilledRect.() -> Unit = {}
        ) = FilledRect(this).insertLayout(this, layout, false).apply(block)

        /**
         * Adds a [FilledRect] over given [layout]
         */
        @UIBuilder
        fun Layout.rectOver(
            layout: Layout,
            block: FilledRect.() -> Unit = {}
        ) = FilledRect(this).insertLayout(this, layout, true).apply(block)
    }
}
