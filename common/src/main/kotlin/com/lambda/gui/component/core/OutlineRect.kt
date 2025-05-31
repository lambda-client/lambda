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

import com.lambda.graphics.renderer.gui.RectRenderer.outlineRect
import com.lambda.gui.component.layout.Layout

class OutlineRect(
    owner: Layout
) : RectLayout(owner) {
    @UIRenderPr0p3rty var outlineWidth = 1.0

    init {
        onRender {
            outlineRect(
                rect,
                outlineWidth,
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

    companion object {
        /**
         * Creates a [OutlineRect] component - layout-based rect representation
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
         * Adds a [OutlineRect] over given [layout]
         */
        @UIBuilder
        fun Layout.outlineOver(
            layout: Layout,
            block: OutlineRect.() -> Unit = {}
        ) = OutlineRect(this).insertLayout(this, layout, true).apply(block)
    }
}
