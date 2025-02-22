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

package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGUIRenderer
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.util.math.Rect
import java.awt.Color
import kotlin.math.min

object FilledRectRenderer : AbstractGUIRenderer(
    VertexAttrib.Group.RECT_FILLED, shader("renderer/rect_filled")
) {
    private const val MIN_SIZE = 0.5
    private const val MIN_ALPHA = 3

    /**
     * Renders a filled rectangle with uniformly rounded corners and a single fill color.
     *
     * This overload delegates to the more detailed version by applying the same color to all corners and using
     * a uniform rounding radius. When shading is enabled, an additional shading effect is applied.
     *
     * @param rect the rectangle defining the position and dimensions.
     * @param roundRadius the uniform radius for rounding each corner; defaults to 0.0.
     * @param color the fill color for the rectangle, applied to all corners; defaults to Color.WHITE.
     * @param shade if true, applies a shading effect; defaults to false.
     */
    fun filledRect(
        rect: Rect,
        roundRadius: Double = 0.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = filledRect(rect, roundRadius, color, color, color, color, shade)

    /**
     * Renders a filled rectangle with rounded corners and individual corner colors.
     *
     * This overload applies a uniform rounding radius to all corners and delegates to the
     * implementation that supports distinct radii for each corner.
     *
     * @param rect the boundaries of the rectangle.
     * @param roundRadius the uniform rounding radius applied to each corner.
     * @param leftTop the color at the top-left corner.
     * @param rightTop the color at the top-right corner.
     * @param rightBottom the color at the bottom-right corner.
     * @param leftBottom the color at the bottom-left corner.
     * @param shade if true, shading is applied to the rectangle.
     */
    fun filledRect(
        rect: Rect,
        roundRadius: Double = 0.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) = filledRect(
        rect,
        roundRadius, roundRadius, roundRadius, roundRadius,
        leftTop, rightTop, rightBottom, leftBottom,
        shade
    )

    /**
     * Renders a filled rectangle with customizable corner rounding and a uniform fill color.
     *
     * This overload simplifies rendering when the same color is applied uniformly to all corners.
     * It delegates to the more detailed version that accepts separate colors for each corner.
     *
     * @param rect The rectangle defining the position and dimensions.
     * @param leftTopRadius The rounding radius of the top-left corner.
     * @param rightTopRadius The rounding radius of the top-right corner.
     * @param rightBottomRadius The rounding radius of the bottom-right corner.
     * @param leftBottomRadius The rounding radius of the bottom-left corner.
     * @param color The color used to fill the rectangle.
     * @param shade If true, enables shading during rendering.
     */
    fun filledRect(
        rect: Rect,
        leftTopRadius: Double = 0.0,
        rightTopRadius: Double = 0.0,
        rightBottomRadius: Double = 0.0,
        leftBottomRadius: Double = 0.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = filledRect(
        rect,
        leftTopRadius, rightTopRadius, rightBottomRadius, leftBottomRadius,
        color, color, color, color,
        shade
    )

    /**
     * Renders a filled rectangle with customizable corner radii and individual corner colors.
     *
     * The function calculates the rectangle's size from its top-left and bottom-right coordinates,
     * and performs early exits if the rectangle is too small or if all corner colors are nearly transparent.
     * Each provided corner radius is clamped to ensure it does not exceed half the rectangle's dimensions.
     * If shading is enabled, a shading effect is applied during rendering.
     *
     * @param rect The rectangle defining the position and dimensions.
     * @param leftTopRadius The rounding radius for the top-left corner.
     * @param rightTopRadius The rounding radius for the top-right corner.
     * @param rightBottomRadius The rounding radius for the bottom-right corner.
     * @param leftBottomRadius The rounding radius for the bottom-left corner.
     * @param leftTop The color for the top-left corner.
     * @param rightTop The color for the top-right corner.
     * @param rightBottom The color for the bottom-right corner.
     * @param leftBottom The color for the bottom-left corner.
     * @param shade If true, applies a shading effect to the rendered rectangle.
     */
    fun filledRect(
        rect: Rect,
        leftTopRadius: Double = 0.0,
        rightTopRadius: Double = 0.0,
        rightBottomRadius: Double = 0.0,
        leftBottomRadius: Double = 0.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) = render(shade) {
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        val size = pos2 - pos1

        if (leftTop.alpha < MIN_ALPHA &&
            rightTop.alpha < MIN_ALPHA &&
            rightBottom.alpha < MIN_ALPHA &&
            leftBottom.alpha < MIN_ALPHA
        ) return@render

        if (size.x < MIN_SIZE || size.y < MIN_SIZE) return@render

        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        val ltr = leftTopRadius.coerceAtMost(maxRadius).coerceAtLeast(0.0)
        val lbr = leftBottomRadius.coerceAtMost(maxRadius).coerceAtLeast(0.0)
        val rbr = rightBottomRadius.coerceAtMost(maxRadius).coerceAtLeast(0.0)
        val rtr = rightTopRadius.coerceAtMost(maxRadius).coerceAtLeast(0.0)

        val p1 = pos1 - 0.25
        val p2 = pos2 + 0.25

        shader["u_Size"] = size
        shader["u_RoundLeftTop"] = ltr
        shader["u_RoundLeftBottom"] = lbr
        shader["u_RoundRightBottom"] = rbr
        shader["u_RoundRightTop"] = rtr

        grow(4)
        putQuad(
            vec3m(p1.x, p1.y, 0.0).vec2(0.0, 0.0).color(leftTop).end(),
            vec3m(p1.x, p2.y, 0.0).vec2(0.0, 1.0).color(leftBottom).end(),
            vec3m(p2.x, p2.y, 0.0).vec2(1.0, 1.0).color(rightBottom).end(),
            vec3m(p2.x, p1.y, 0.0).vec2(1.0, 0.0).color(rightTop).end()
        )
    }
}
