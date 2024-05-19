package com.lambda.graphics.renderer.gui.rect.outline

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.renderer.gui.rect.IRectEntry
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OutlineRectEntry(
    override val owner: OutlineRectRenderer,
    override val updateBlock: IRectEntry.Outline.() -> Unit
) : IRectEntry.Outline {
    override var position by owner.positionRect()
    override var roundRadius by owner.field(0.0)
    override var shade by owner.field(false)

    override var outerGlow by owner.field(1.0)
    override var innerGlow by owner.field(1.0)

    private var leftTop     by owner.field(Color.WHITE)
    private var rightTop    by owner.field(Color.WHITE)
    private var rightBottom by owner.field(Color.WHITE)
    private var leftBottom  by owner.field(Color.WHITE)

    override fun color(leftTop: Color, rightTop: Color, rightBottom: Color, leftBottom: Color) {
        this.leftTop = leftTop
        this.rightTop = rightTop
        this.rightBottom = rightBottom
        this.leftBottom = leftBottom
    }

    private val quality = 8
    private val verticesCount = quality * 4

    override fun build(ctx: IRenderContext) = ctx.use {
        grow(verticesCount * 3)

        val main  = genVertices(0.0, false)
        val outer = genVertices(outerGlow, true)
        val inner = genVertices(-innerGlow, true)

        fun drawStripWith(vertices: MutableList<Int>) {
            var prev = main.last() to vertices.last()
            repeat(verticesCount) {
                val new = main[it] to vertices[it]
                putQuad(new.first, new.second, prev.second, prev.first)
                prev = new
            }
        }

        drawStripWith(outer)
        drawStripWith(inner)
    }

    private fun IRenderContext.genVertices(size: Double, isGlow: Boolean): MutableList<Int> {
        val r = position.expand(size)
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
}