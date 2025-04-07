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

import com.lambda.graphics.pipeline.VertexPipeline
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.shader.Shader.Companion.shadeUniforms
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.util.math.Rect
import java.awt.Color
import kotlin.math.min

object RectRenderer {
    private val filled  = shader("renderer/rect_filled")
    private val outline = shader("renderer/rect_outline")
    private val glow    = shader("renderer/rect_glow")

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
    ) = putRect(
        filled,
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

        putRect(
            outline,
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

        glow.use()
        glow["u_InnerRectWidth"] = innerSpread.coerceAtLeast(1.0)
        glow["u_InnerRoundLeftTop"]     = leftTopInnerRadius    .coerceAtLeast(leftTopOuterRadius)    .clampRadius()
        glow["u_InnerRoundLeftBottom"]  = leftBottomInnerRadius .coerceAtLeast(leftBottomOuterRadius) .clampRadius()
        glow["u_InnerRoundRightBottom"] = rightBottomInnerRadius.coerceAtLeast(rightBottomOuterRadius).clampRadius()
        glow["u_InnerRoundRightTop"]    = rightTopInnerRadius   .coerceAtLeast(rightTopOuterRadius)   .clampRadius()

        putRect(
            glow,
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

    private fun putRect(
        shader: Shader,
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
    ) {
        if (leftTop.alpha + rightTop.alpha + leftBottom.alpha + rightBottom.alpha == 0) return

        shader.use()
        shader.shadeUniforms(shade)

        val expand = expandIn.coerceAtLeast(0.0) + 1

        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        val size = pos2 - pos1
        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        fun Double.clampRadius() =
            this.coerceAtMost(maxRadius)
                .coerceAtLeast(0.0)

        // Size of the rectangle
        shader["u_Pos"] = pos1
        shader["u_Size"] = size
        shader["u_Expand"] = expand

        // Round radius
        shader["u_RoundLeftTop"] = leftTopRadius.clampRadius()
        shader["u_RoundLeftBottom"] = leftBottomRadius.clampRadius()
        shader["u_RoundRightBottom"] = rightBottomRadius.clampRadius()
        shader["u_RoundRightTop"] = rightTopRadius.clampRadius()

        // Color
        shader["u_ColorLeftTop"] = leftTop
        shader["u_ColorLeftBottom"] = leftBottom
        shader["u_ColorRightBottom"] = rightBottom
        shader["u_ColorRightTop"] = rightTop

        // For glow & outline only
        shader["u_RectWidth"] = expandIn

        VertexPipeline.renderStaticRect()
    }
}
