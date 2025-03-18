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

import com.lambda.graphics.buffer.IRenderContext
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGUIRenderer
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.util.math.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform
import java.awt.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object OutlineRectRenderer : AbstractGUIRenderer(
    VertexAttrib.Group.RECT_OUTLINE, shader("renderer/rect_outline")
) {
    private const val QUALITY = 8
    private const val VERTICES_COUNT = QUALITY * 4

    fun outlineRect(
        rect: Rect,
        roundRadius: Double = 0.0,
        glowRadius: Double = 1.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = outlineRect(rect, roundRadius, glowRadius, color, color, color, color, shade)

    fun outlineRect(
        rect: Rect,
        roundRadius: Double = 0.0,
        glowRadius: Double = 1.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) = render(shade) {
        if (glowRadius < 0.1) return@render

        grow(VERTICES_COUNT * 3)

        fun IRenderContext.genVertices(size: Double, isGlow: Boolean): MutableList<Int> {
            val r = rect.expand(size)
            val a = (!isGlow).toInt().toDouble()

            val halfSize = r.size * 0.5
            val maxRadius = min(halfSize.x, halfSize.y) - 0.5
            val round = (roundRadius + size).coerceAtMost(maxRadius).coerceAtLeast(0.0)

            fun MutableList<Int>.buildCorners(base: Vec2d, c: Color, angleRange: IntRange) = repeat(QUALITY) {
                val min = angleRange.first.toDouble()
                val max = angleRange.last.toDouble()
                val p = it.toDouble() / QUALITY
                val angle = lerp(p, min, max).toRadian()

                val pos = base + Vec2d(cos(angle), -sin(angle)) * round

                val uvx = transform(pos.x, rect.left, rect.right, 0.0, 1.0)
                val uvy = transform(pos.y, rect.top, rect.bottom, 0.0, 1.0)

                add(vec3m(pos.x, pos.y, 0.0).vec2(uvx, uvy).float(a).color(c).end())
            }

            val rt = r.rightTop + Vec2d(-round, round)
            val lt = r.leftTop + Vec2d(round, round)
            val lb = r.leftBottom + Vec2d(round, -round)
            val rb = r.rightBottom + Vec2d(-round, -round)

            return mutableListOf<Int>().apply {
                buildCorners(rt, rightTop, 0..90)
                buildCorners(lt, leftTop, 90..180)
                buildCorners(lb, leftBottom, 180..270)
                buildCorners(rb, rightBottom, 270..360)
            }
        }

        val main = genVertices(0.0, false)

        fun drawStripWith(vertices: MutableList<Int>) {
            var prev = main.last() to vertices.last()
            repeat(VERTICES_COUNT) {
                val new = main[it] to vertices[it]
                putQuad(new.first, new.second, prev.second, prev.first)
                prev = new
            }
        }

        drawStripWith(genVertices(-(glowRadius.coerceAtMost(1.0)), true))
        drawStripWith(genVertices(glowRadius, true))
    }
}
