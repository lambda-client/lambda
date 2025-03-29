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
import java.awt.Color

abstract class RectLayout(
    owner: Layout
) : Layout(owner) {
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
    }

    fun setRadius(radius: Double) {
        leftTopRadius = radius
        rightTopRadius = radius
        rightBottomRadius = radius
        leftBottomRadius = radius
    }

    fun setRadiusH(radiusL: Double, radiusR: Double) {
        leftTopRadius = radiusL
        rightTopRadius = radiusR
        rightBottomRadius = radiusR
        leftBottomRadius = radiusL
    }

    fun setRadiusV(radiusT: Double, radiusB: Double) {
        leftTopRadius = radiusT
        rightTopRadius = radiusT
        rightBottomRadius = radiusB
        leftBottomRadius = radiusB
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
}
