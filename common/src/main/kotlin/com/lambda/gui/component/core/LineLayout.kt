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

import com.lambda.gui.component.layout.Layout
import org.joml.Vector2d
import java.awt.Color

abstract class LineLayout(
    owner: Layout
) : Layout(owner) {
    @UIRenderPr0p3rty var lineWidth = 1.0
    @UIRenderPr0p3rty var dashiness = 1.0
    @UIRenderPr0p3rty var dashPeriod = 1.0
    @UIRenderPr0p3rty var shade = false

    val points = mutableListOf<Point>()

    init {
        properties.interactionPassthrough = true
    }

    fun addPoint(position: Vector2d, color: Color = Color.WHITE) {
        points.add(Point(position, color))
    }

    fun clearPoints() {
        points.clear()
    }

    data class Point(
        val position: Vector2d,
        val color: Color
    )
}
