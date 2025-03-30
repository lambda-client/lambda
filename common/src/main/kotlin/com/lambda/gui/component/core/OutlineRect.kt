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

import com.lambda.graphics.renderer.gui.rect.OutlineRectRenderer.outlineRect
import com.lambda.gui.component.layout.Layout
import java.awt.Color

class OutlineRect(
    owner: Layout
) : Layout(owner) {
    @UIRenderPr0p3rty var roundRadius = 0.0
    @UIRenderPr0p3rty var glowRadius = 1.0

    @UIRenderPr0p3rty var leftTopColor: Color = Color.WHITE
    @UIRenderPr0p3rty var rightTopColor: Color = Color.WHITE
    @UIRenderPr0p3rty var rightBottomColor: Color = Color.WHITE
    @UIRenderPr0p3rty var leftBottomColor: Color = Color.WHITE

    @UIRenderPr0p3rty var shade = false

    init {
        properties.interactionPassthrough = true

        onRender {
            outlineRect(
                rect,
                roundRadius,
                glowRadius,
                leftTopColor,
                rightTopColor,
                rightBottomColor,
                leftBottomColor,
                shade
            )
        }
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
         * Creates an [OutlineRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.outline(
            block: OutlineRect.() -> Unit = {}
        ) = OutlineRect(this).apply(children::add).apply(block)

        /**
         * Adds a [OutlineRect] behind given [layout]
         */
        @UIBuilder
        fun Layout.outlineBehind(
            layout: Layout,
            block: OutlineRect.() -> Unit = {}
        ) = OutlineRect(this).insertLayout(this, layout, false).apply(block)

        /**
         * Creates an [OutlineRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.outlineOver(
            layout: Layout,
            block: OutlineRect.() -> Unit = {}
        ) = OutlineRect(this).insertLayout(this, layout, true).apply(block)
    }
}
