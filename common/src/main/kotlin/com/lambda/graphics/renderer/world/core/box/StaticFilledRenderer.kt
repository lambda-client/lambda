package com.lambda.graphics.renderer.world.core.box

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.world.AbstractEspRenderer
import com.lambda.graphics.renderer.world.DirectionMask
import com.lambda.graphics.renderer.world.DirectionMask.hasDirection
import com.lambda.graphics.renderer.world.core.IESPEntry
import com.lambda.graphics.shader.Shader
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

class StaticFilledRenderer : AbstractEspRenderer<IESPEntry.IBoxEntry.Filled>(
    VertexMode.TRIANGLES, shader, false
) {
    override fun newEntry(block: IESPEntry.IBoxEntry.Filled.() -> Unit) = Builder(this, block)

    class Builder(
        override val owner: StaticFilledRenderer,
        override val updateBlock: IESPEntry.IBoxEntry.Filled.() -> Unit
    ) : IESPEntry.IBoxEntry.Filled {
        override var box by owner.field(Box.from(Vec3d.ZERO))
        override var sides by owner.field(DirectionMask.ALL)
        override var color by owner.field(Color.WHITE)

        override fun build(ctx: IRenderContext) = ctx.use {
            val pos1 = box.min
            val pos2 = box.max

            ctx.grow(8)

            val blb by lazy { vec3(pos1.x, pos1.y, pos1.z).color(color).end() }
            val blf by lazy { vec3(pos1.x, pos1.y, pos2.z).color(color).end() }
            val brb by lazy { vec3(pos2.x, pos1.y, pos1.z).color(color).end() }
            val brf by lazy { vec3(pos2.x, pos1.y, pos2.z).color(color).end() }
            val tlb by lazy { vec3(pos1.x, pos2.y, pos1.z).color(color).end() }
            val tlf by lazy { vec3(pos1.x, pos2.y, pos2.z).color(color).end() }
            val trb by lazy { vec3(pos2.x, pos2.y, pos1.z).color(color).end() }
            val trf by lazy { vec3(pos2.x, pos2.y, pos2.z).color(color).end() }

            if (sides.hasDirection(DirectionMask.EAST))  putQuad(brb, brf, trf, trb)
            if (sides.hasDirection(DirectionMask.WEST))  putQuad(blb, blf, tlf, tlb)
            if (sides.hasDirection(DirectionMask.UP))    putQuad(tlb, tlf, trf, trb)
            if (sides.hasDirection(DirectionMask.DOWN))  putQuad(blb, brb, brf, blf)
            if (sides.hasDirection(DirectionMask.SOUTH)) putQuad(blf, brf, trf, tlf)
            if (sides.hasDirection(DirectionMask.NORTH)) putQuad(blb, brb, trb, tlb)
        }
    }

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_static")
    }
}