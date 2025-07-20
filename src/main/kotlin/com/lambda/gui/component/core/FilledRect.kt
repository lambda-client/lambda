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

import com.lambda.graphics.renderer.gui.RectRenderer.filledRect
import com.lambda.gui.component.layout.Layout

class FilledRect(
    owner: Layout
) : RectLayout(owner) {
    init {
        onRender {
            filledRect(
                rect,
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
