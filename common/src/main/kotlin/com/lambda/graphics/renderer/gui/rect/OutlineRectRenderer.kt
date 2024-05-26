package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.shader.Shader
import com.lambda.util.math.MathUtils.lerp
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
        innerGlow: Double = 1.0,
        outerGlow: Double = 1.0,
        color: Color = Color.WHITE,
        shade: Boolean = false
    ) = build(rect, roundRadius, innerGlow, outerGlow, color, color, color, color, shade)

    fun build(
        rect: Rect,
        roundRadius: Double = 0.0,
        innerGlow: Double = 1.0,
        outerGlow: Double = 1.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false
    ) = vao.use {
        val drawInner = innerGlow >= 1
        val drawOuter = outerGlow >= 1

        if (!drawInner && !drawOuter) return@use

        grow(verticesCount * (1 + drawInner.toInt() + drawOuter.toInt()))

        fun IRenderContext.genVertices(size: Double, isGlow: Boolean): MutableList<Int> {
            val r = rect.expand(size)
            val a = (!isGlow).toInt().toDouble()

            val halfSize = r.size * 0.5
            val maxRadius = min(halfSize.x, halfSize.y) - 0.5
            val round = (roundRadius + size).coerceAtMost(maxRadius)

            fun MutableList<Int>.buildCorners(base: Vec2d, c: Color, angleRange: IntRange) = repeat(quality) {
                val min = angleRange.first.toDouble()
                val max = angleRange.last.toDouble()
                val p = it.toDouble() / quality
                val angle = lerp(min, max, p).toRadian()

                val pos = base + Vec2d(cos(angle), -sin(angle)) * round
                val s = shade.toInt().toDouble()
                add(vec2(pos.x, pos.y).float(a).float(s).color(c).end())
            }

            val rt = r.rightTop    + Vec2d(-round,  round)
            val lt = r.leftTop     + Vec2d( round,  round)
            val lb = r.leftBottom  + Vec2d( round, -round)
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

        if (drawInner) drawStripWith(genVertices(-innerGlow, true))
        if (drawOuter) drawStripWith(genVertices(outerGlow, true))
    }

    override fun render() {
        shader.use()
        super.render()
    }

    companion object {
        private val shader = Shader("renderer/rect_outline")
    }
}