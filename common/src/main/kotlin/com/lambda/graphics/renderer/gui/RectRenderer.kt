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

package com.lambda.graphics.renderer.gui

import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.min

object RectRenderer {
    private val filled  = AbstractGUIRenderer(VertexAttrib.Group.RECT, shader("renderer/rect_filled"))
    private val outline = AbstractGUIRenderer(VertexAttrib.Group.RECT, shader("renderer/rect_outline"))
    private val glow    = AbstractGUIRenderer(VertexAttrib.Group.RECT, shader("renderer/rect_glow"))

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
    ) = filled.putRect(
        rect,
        0.0,
        shade,
        leftTopRadius,
        rightTopRadius,
        rightBottomRadius,
        leftBottomRadius,
        leftTop,
        rightTop,
        rightBottom,
        leftBottom,
    )

    fun outlineRect(
        rect: Rect,
        width: Double = 1.0,
        leftTopRadius: Double = 0.0,
        rightTopRadius: Double = 0.0,
        rightBottomRadius: Double = 0.0,
        leftBottomRadius: Double = 0.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) {
        if (width < 0.01) return

        outline.putRect(
            rect,
            width * 0.25,
            shade,
            leftTopRadius,
            rightTopRadius,
            rightBottomRadius,
            leftBottomRadius,
            leftTop,
            rightTop,
            rightBottom,
            leftBottom,
        )
    }

    fun glowRect(
        rect: Rect,
        outerSpread: Double = 1.0,
        innerSpread: Double = 1.0,
        leftTopInnerRadius: Double = 0.0,
        rightTopInnerRadius: Double = 0.0,
        rightBottomInnerRadius: Double = 0.0,
        leftBottomInnerRadius: Double = 0.0,
        leftTopOuterRadius: Double = 0.0,
        rightTopOuterRadius: Double = 0.0,
        rightBottomOuterRadius: Double = 0.0,
        leftBottomOuterRadius: Double = 0.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) {
        if (outerSpread < 0.01 && innerSpread < 0.01) return

        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        val size = pos2 - pos1
        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        fun Double.clampRadius() =
            this.coerceAtMost(maxRadius)
                .coerceAtLeast(0.0)

        glow.shader.use()
        glow.shader["u_InnerRectWidth"] = innerSpread.coerceAtLeast(1.0)
        glow.shader["u_InnerRoundLeftTop"]     = leftTopInnerRadius    .coerceAtLeast(leftTopOuterRadius)    .clampRadius()
        glow.shader["u_InnerRoundLeftBottom"]  = leftBottomInnerRadius .coerceAtLeast(leftBottomOuterRadius) .clampRadius()
        glow.shader["u_InnerRoundRightBottom"] = rightBottomInnerRadius.coerceAtLeast(rightBottomOuterRadius).clampRadius()
        glow.shader["u_InnerRoundRightTop"]    = rightTopInnerRadius   .coerceAtLeast(rightTopOuterRadius)   .clampRadius()

        glow.putRect(
            rect,
            outerSpread.coerceAtLeast(1.0),
            shade,
            leftTopOuterRadius,
            rightTopOuterRadius,
            rightBottomOuterRadius,
            leftBottomOuterRadius,
            leftTop,
            rightTop,
            rightBottom,
            leftBottom,
        )
    }

    private fun AbstractGUIRenderer.putRect(
        rect: Rect,
        expandIn: Double,
        shade: Boolean,
        leftTopRadius: Double,
        rightTopRadius: Double,
        rightBottomRadius: Double,
        leftBottomRadius: Double,
        leftTop: Color,
        rightTop: Color,
        rightBottom: Color,
        leftBottom: Color,
    )  = render(shade) { shader ->
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        val expand = expandIn.coerceAtLeast(0.0) + 1
        val smoothing = 0.3

        val p1 = pos1 - expand - smoothing
        val p2 = pos2 + expand + smoothing

        val size = pos2 - pos1
        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        val uv1 = Vec2d(
            -expand / size.x,
            -expand / size.y
        )

        val uv2 = Vec2d(
            1.0 + (expand / size.x),
            1.0 + (expand / size.y)
        )

        fun Double.clampRadius() =
            this.coerceAtMost(maxRadius)
                .coerceAtLeast(0.0)

        // Size of the rectangle
        shader["u_Size"] = size

        // Round radius
        shader["u_RoundLeftTop"] = leftTopRadius.clampRadius()
        shader["u_RoundLeftBottom"] = leftBottomRadius.clampRadius()
        shader["u_RoundRightBottom"] = rightBottomRadius.clampRadius()
        shader["u_RoundRightTop"] = rightTopRadius.clampRadius()

        // For glow & outline only
        shader["u_RectWidth"] = expandIn

        upload {
            buildQuad(
                vertex {
                    vec3m(p1.x, p1.y).vec2(uv1.x, uv1.y).color(leftTop)
                },
                vertex {
                    vec3m(p1.x, p2.y).vec2(uv1.x, uv2.y).color(leftBottom)
                },
                vertex {
                    vec3m(p2.x, p2.y).vec2(uv2.x, uv2.y).color(rightBottom)
                },
                vertex {
                    vec3m(p2.x, p1.y).vec2(uv2.x, uv1.y).color(rightTop)
                }
            )
        }
    }
}
