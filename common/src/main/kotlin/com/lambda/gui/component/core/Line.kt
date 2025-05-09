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

import com.lambda.graphics.renderer.gui.LineRenderer
import com.lambda.gui.component.layout.Layout
import org.joml.Vector2d
import java.awt.Color

class Line(
    owner: Layout
) : LineLayout(owner) {
    init {
        onRender {
            if (points.size >= 2) {
                LineRenderer.lines(
                    dashiness = dashiness,
                    dashPeriod = dashPeriod,
                    batching = false
                ) {
                    line(lineWidth) {
                        points.forEach { point ->
                            point(point.position, point.color)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Creates a [Line] component - layout-based line representation
         */
        @UIBuilder
        fun Layout.line(
            block: Line.() -> Unit = {}
        ) = Line(this).apply(children::add).apply(block)

        /**
         * Adds a [Line] behind given [layout]
         */
        @UIBuilder
        fun Layout.lineBehind(
            layout: Layout,
            block: Line.() -> Unit = {}
        ) = Line(this).insertLayout(this, layout, false).apply(block)

        /**
         * Adds a [Line] over given [layout]
         */
        @UIBuilder
        fun Layout.lineOver(
            layout: Layout,
            block: Line.() -> Unit = {}
        ) = Line(this).insertLayout(this, layout, true).apply(block)
    }
}