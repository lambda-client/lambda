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

    /**
     * Sets a uniform radius for all four corners of the rectangle.
     *
     * This method assigns the given radius to each of the rectangle's corner properties.
     *
     * @param radius the radius value to apply to all corners.
     */
    fun setRadius(radius: Double) {
        leftTopRadius = radius
        rightTopRadius = radius
        rightBottomRadius = radius
        leftBottomRadius = radius
    }

    /**
     * Sets the same color for all corners of the filled rectangle.
     *
     * Updates the left top, right top, right bottom, and left bottom corner colors
     * to the specified value.
     *
     * @param color the color to apply to all corners.
     */
    fun setColor(color: Color) {
        leftTopColor = color
        rightTopColor = color
        rightBottomColor = color
        leftBottomColor = color
    }

    /**
     * Applies a horizontal gradient by setting the left corners to [colorL] and the right corners to [colorR].
     *
     * @param colorL the color for the top-left and bottom-left corners.
     * @param colorR the color for the top-right and bottom-right corners.
     */
    fun setColorH(colorL: Color, colorR: Color) {
        leftTopColor = colorL
        rightTopColor = colorR
        rightBottomColor = colorR
        leftBottomColor = colorL
    }

    /**
     * Updates the rectangle's corner colors with a vertical gradient.
     *
     * Sets both top corners to the specified [colorT] and both bottom corners to [colorB].
     *
     * @param colorT the color for the top corners.
     * @param colorB the color for the bottom corners.
     */
    fun setColorV(colorT: Color, colorB: Color) {
        leftTopColor = colorT
        rightTopColor = colorT
        rightBottomColor = colorB
        leftBottomColor = colorB
    }

    companion object {
        /**
             * Creates and adds a new [FilledRect] component to the current layout.
             *
             * The function instantiates a [FilledRect] using the receiver as its owner, automatically adds it to the layout's children,
             * and then applies an optional configuration block for further customization.
             *
             * @param block an optional lambda to configure the newly created [FilledRect].
             * @return the newly created and configured [FilledRect] component.
             */
        @UIBuilder
        fun Layout.rect(block: FilledRect.() -> Unit = {}) =
            FilledRect(this).apply(children::add).apply(block)
    }
}
