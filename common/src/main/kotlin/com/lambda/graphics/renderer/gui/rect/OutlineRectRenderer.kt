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
import com.lambda.graphics.shader.Shader
import com.lambda.util.math.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OutlineRectRenderer : AbstractRectRenderer(
    VertexAttrib.Group.RECT_OUTLINE, shader
) {
    private val quality = 8
    private val verticesCount = quality * 4

    fun build(
        rect: Rect,
        roundRadius: Double = 0.0,
        glowRadius: Double = 1.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = build(rect, roundRadius, glowRadius, color, color, color, color, shade)

    fun build(
        rect: Rect,
        roundRadius: Double = 0.0,
        glowRadius: Double = 1.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) = pipeline.use {
        if (glowRadius < 1) return@use

        grow(verticesCount * 3)

        fun IRenderContext.genVertices(size: Double, isGlow: Boolean): MutableList<Int> {
            val r = rect.expand(size)
            val a = (!isGlow).toInt().toDouble()

            val halfSize = r.size * 0.5
            val maxRadius = min(halfSize.x, halfSize.y) - 0.5
            val round = (roundRadius + size).coerceAtMost(maxRadius).coerceAtLeast(0.0)

            fun MutableList<Int>.buildCorners(base: Vec2d, c: Color, angleRange: IntRange) = repeat(quality) {
                val min = angleRange.first.toDouble()
                val max = angleRange.last.toDouble()
                val p = it.toDouble() / quality
                val angle = lerp(p, min, max).toRadian()

                val pos = base + Vec2d(cos(angle), -sin(angle)) * round
                val s = shade.toInt().toDouble()
                add(vec2m(pos.x, pos.y).float(a).float(s).color(c).end())
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
            repeat(verticesCount) {
                val new = main[it] to vertices[it]
                putQuad(new.first, new.second, prev.second, prev.first)
                prev = new
            }
        }

        drawStripWith(genVertices(-(glowRadius.coerceAtMost(1.0)), true))
        drawStripWith(genVertices(glowRadius, true))
    }

    companion object {
        private val shader = Shader("renderer/rect_outline")
    }
}
