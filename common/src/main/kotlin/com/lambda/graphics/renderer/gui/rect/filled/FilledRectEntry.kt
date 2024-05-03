package com.lambda.graphics.renderer.gui.rect.filled

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.renderer.gui.rect.IRectEntry
import com.lambda.util.math.MathUtils.toInt
import java.awt.Color
import kotlin.math.min

class FilledRectEntry(
    override val owner: FilledRectRenderer,
    override val updateBlock: IRectEntry.Filled.() -> Unit
) : IRectEntry.Filled {
    override var position by owner.positionRect()
    override var roundRadius by owner.field(0.0)
    override var shade by owner.field(false)

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

    override fun build(ctx: IRenderContext) = ctx.use {
        val pos1 = position.leftTop
        val pos2 = position.rightBottom

        val size = pos2 - pos1
        if (size.x < MIN_SIZE || size.y < MIN_SIZE) return@use

        val halfSize = size * 0.5
        val maxRadius = min(halfSize.x, halfSize.y)

        val round = min(roundRadius, maxRadius)

        val p1 = pos1 - 0.75
        val p2 = pos2 + 0.75
        val s = shade.toInt().toDouble()

        grow(4)

        putQuad(
            vec2(p1.x, p1.y).vec2(0.0, 0.0).vec2(size.x, size.y).float(round).float(s).color(leftTop).end(),
            vec2(p1.x, p2.y).vec2(0.0, 1.0).vec2(size.x, size.y).float(round).float(s).color(leftBottom).end(),
            vec2(p2.x, p2.y).vec2(1.0, 1.0).vec2(size.x, size.y).float(round).float(s).color(rightBottom).end(),
            vec2(p2.x, p1.y).vec2(1.0, 0.0).vec2(size.x, size.y).float(round).float(s).color(rightTop).end()
        )
    }

    companion object {
        private const val MIN_SIZE = 0.5
    }
}