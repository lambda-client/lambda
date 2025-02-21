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
    private const val MIN_ALPHA = 1

    fun filledRect(
        rect: Rect,
        roundRadius: Double = 0.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = filledRect(rect, roundRadius, color, color, color, color, shade)

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
