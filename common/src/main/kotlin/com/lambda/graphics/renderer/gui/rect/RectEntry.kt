package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.util.math.Rect
import java.awt.Color
import kotlin.math.min

class RectEntry(
    override val owner: RectRenderer,
    override val updateBlock: IRectEntry.() -> Unit
) : IRectEntry {
    override var position by owner.positionRect()
    override var roundRadius by owner.field(0.0)

    private var leftTop     by owner.field(Color.WHITE!!)
    private var rightTop    by owner.field(Color.WHITE!!)
    private var rightBottom by owner.field(Color.WHITE!!)
    private var leftBottom  by owner.field(Color.WHITE!!)

    override fun color(leftTop: Color, rightTop: Color, rightBottom: Color, leftBottom: Color) {
        this.leftTop = leftTop
        this.rightTop = rightTop
        this.rightBottom = rightBottom
        this.leftBottom = leftBottom
    }

    override fun build(ctx: IRenderContext) = ctx.use {
        if (leftTop    .alpha < MIN_ALPHA &&
            rightTop   .alpha < MIN_ALPHA &&
            rightBottom.alpha < MIN_ALPHA &&
            leftBottom .alpha < MIN_ALPHA
        ) return@use

        val pos1 = position.leftTop
        val pos2 = position.rightBottom

        val size = pos2 - pos1
        if (size.x < MIN_SIZE || size.y < MIN_SIZE) return@use

        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        val round = min(roundRadius, maxRadius)

        val p1 = pos1 - 0.75
        val p2 = pos2 + 0.75

        grow(4)

        putQuad(
            vec2(p1.x, p1.y).vec2(0.0, 0.0).vec3(size.x, size.y, round).color(leftTop).end(),
            vec2(p1.x, p2.y).vec2(0.0, 1.0).vec3(size.x, size.y, round).color(leftBottom).end(),
            vec2(p2.x, p2.y).vec2(1.0, 1.0).vec3(size.x, size.y, round).color(rightBottom).end(),
            vec2(p2.x, p1.y).vec2(1.0, 0.0).vec3(size.x, size.y, round).color(rightTop).end()
        )
    }

    companion object {
        private const val MIN_ALPHA = 5
        private const val MIN_SIZE = 0.5
    }
}

interface IRectEntry : IRenderEntry<IRectEntry> {
    var position: Rect
    var roundRadius: Double

    fun color(leftTop: Color, rightTop: Color, rightBottom: Color, leftBottom: Color)

    fun color(color: Color) = color(color, color, color, color)

    fun colorH(left: Color, right: Color) = color(left, right, right, left)

    fun colorV(top: Color, bottom: Color) = color(top, top, bottom, bottom)
}