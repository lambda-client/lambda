package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.shader.Shader
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import java.awt.Color
import kotlin.math.min

class FilledRectRenderer : AbstractRectRenderer(
    VertexAttrib.Group.RECT_FILLED, shader
) {
    fun build(
        rect: Rect,
        roundRadius: Double = 0.0,
        color: Color = Color.WHITE,
        shade: Boolean = false,
    ) = build(rect, roundRadius, color, color, color, color, shade)

    fun build(
        rect: Rect,
        roundRadius: Double = 0.0,
        leftTop: Color = Color.WHITE,
        rightTop: Color = Color.WHITE,
        rightBottom: Color = Color.WHITE,
        leftBottom: Color = Color.WHITE,
        shade: Boolean = false,
    ) = vao.use {
        val pos1 = rect.leftTop
        val pos2 = rect.rightBottom

        val size = pos2 - pos1

        if (leftTop.alpha < MIN_ALPHA &&
            rightTop.alpha < MIN_ALPHA &&
            rightBottom.alpha < MIN_ALPHA &&
            leftBottom.alpha < MIN_ALPHA
        ) return@use
        if (size.x < MIN_SIZE || size.y < MIN_SIZE) return@use

        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        val round = min(roundRadius, maxRadius)

        val p1 = pos1 - 0.25
        val p2 = pos2 + 0.25
        val s = shade.toInt().toDouble()

        grow(4)

        putQuad(
            vec2(p1.x, p1.y).vec2(0.0, 0.0).vec2(size.x, size.y).float(round).float(s).color(leftTop).end(),
            vec2(p1.x, p2.y).vec2(0.0, 1.0).vec2(size.x, size.y).float(round).float(s).color(leftBottom).end(),
            vec2(p2.x, p2.y).vec2(1.0, 1.0).vec2(size.x, size.y).float(round).float(s).color(rightBottom).end(),
            vec2(p2.x, p1.y).vec2(1.0, 0.0).vec2(size.x, size.y).float(round).float(s).color(rightTop).end()
        )
    }

    override fun render() {
        shader.use()
        super.render()
    }

    companion object {
        private const val MIN_SIZE = 0.5
        private const val MIN_ALPHA = 3

        private val shader = Shader("renderer/rect_filled")
    }
}