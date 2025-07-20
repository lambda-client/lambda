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

package com.lambda.gui.component.core

import com.lambda.graphics.renderer.gui.RectRenderer.glowRect
import com.lambda.gui.component.layout.Layout

class GlowRect(
    owner: Layout
) : RectLayout(owner) {
    @UIRenderPr0p3rty var outerSpread = 1.0
    @UIRenderPr0p3rty var innerSpread = 0.0

    @UIRenderPr0p3rty var leftTopInnerRadius = 0.0
    @UIRenderPr0p3rty var rightTopInnerRadius = 0.0
    @UIRenderPr0p3rty var rightBottomInnerRadius = 0.0
    @UIRenderPr0p3rty var leftBottomInnerRadius = 0.0

    init {
        onRender {
            glowRect(
                rect,
                outerSpread,
                innerSpread,

                leftTopInnerRadius,
                rightTopInnerRadius,
                rightBottomInnerRadius,
                leftBottomInnerRadius,

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

    fun setInnerRadius(radius: Double) {
        leftTopInnerRadius = radius
        rightTopInnerRadius = radius
        rightBottomInnerRadius = radius
        leftBottomInnerRadius = radius
    }

    fun setInnerRadiusH(radiusL: Double, radiusR: Double) {
        leftTopInnerRadius = radiusL
        rightTopInnerRadius = radiusR
        rightBottomInnerRadius = radiusR
        leftBottomInnerRadius = radiusL
    }

    fun setInnerRadiusV(radiusT: Double, radiusB: Double) {
        leftTopInnerRadius = radiusT
        rightTopInnerRadius = radiusT
        rightBottomInnerRadius = radiusB
        leftBottomInnerRadius = radiusB
    }

    companion object {
        /**
         * Creates a [GlowRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.glow(
            block: GlowRect.() -> Unit = {}
        ) = GlowRect(this).apply(children::add).apply(block)

        /**
         * Adds a [GlowRect] behind given [layout]
         */
        @UIBuilder
        fun Layout.glowBehind(
            layout: Layout,
            block: GlowRect.() -> Unit = {}
        ) = GlowRect(this).insertLayout(this, layout, false).apply(block)

        /**
         * Adds a [GlowRect] over given [layout]
         */
        @UIBuilder
        fun Layout.glowOver(
            layout: Layout,
            block: GlowRect.() -> Unit = {}
        ) = GlowRect(this).insertLayout(this, layout, true).apply(block)
    }
}