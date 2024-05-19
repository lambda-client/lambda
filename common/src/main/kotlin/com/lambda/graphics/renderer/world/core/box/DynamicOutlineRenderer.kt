package com.lambda.graphics.renderer.world.core.box

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.world.AbstractEspRenderer
import com.lambda.graphics.renderer.world.DirectionMask
import com.lambda.graphics.renderer.world.DirectionMask.DOWN
import com.lambda.graphics.renderer.world.DirectionMask.EAST
import com.lambda.graphics.renderer.world.DirectionMask.NORTH
import com.lambda.graphics.renderer.world.DirectionMask.SOUTH
import com.lambda.graphics.renderer.world.DirectionMask.UP
import com.lambda.graphics.renderer.world.DirectionMask.WEST
import com.lambda.graphics.renderer.world.DirectionMask.hasDirection
import com.lambda.graphics.renderer.world.core.IESPEntry
import com.lambda.graphics.shader.Shader
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

class DynamicOutlineRenderer : AbstractEspRenderer<IESPEntry.IBoxEntry.Outline>(
    VertexMode.LINES, shader, true
) {
    override fun newEntry(block: IESPEntry.IBoxEntry.Outline.() -> Unit) = Builder(this, block)

    class Builder(
        override val owner: DynamicOutlineRenderer,
        override val updateBlock: IESPEntry.IBoxEntry.Outline.() -> Unit
    ) : IESPEntry.IBoxEntry.Outline {
        override var box by owner.field(Box.from(Vec3d.ZERO))
        override var color by owner.field(Color.WHITE)
        override var sides by owner.field(DirectionMask.ALL)
        override var outlineMode by owner.field(DirectionMask.OutlineMode.OR)

        private var prevBox by owner.field(Box.from(Vec3d.ZERO))

        init {
            update()
            prevBox = box
        }

        override fun update() {
            prevBox = box
            super.update()
        }

        override fun build(ctx: IRenderContext) = ctx.use {
            val pos11 = prevBox.min
            val pos12 = prevBox.max
            val pos21 = box.min
            val pos22 = box.max

            ctx.grow(8)

            val blb by lazy { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color).end() }
            val blf by lazy { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color).end() }
            val brb by lazy { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color).end() }
            val brf by lazy { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color).end() }
            val tlb by lazy { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color).end() }
            val tlf by lazy { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color).end() }
            val trb by lazy { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color).end() }
            val trf by lazy { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color).end() }

            val hasEast  = sides.hasDirection(EAST)
            val hasWest  = sides.hasDirection(WEST)
            val hasUp    = sides.hasDirection(UP)
            val hasDown  = sides.hasDirection(DOWN)
            val hasSouth = sides.hasDirection(SOUTH)
            val hasNorth = sides.hasDirection(NORTH)

            if (outlineMode.check(hasUp, hasNorth)) putLine(tlb, trb)
            if (outlineMode.check(hasUp, hasSouth)) putLine(tlf, trf)
            if (outlineMode.check(hasUp, hasWest))  putLine(tlb, tlf)
            if (outlineMode.check(hasUp, hasEast))  putLine(trf, trb)

            if (outlineMode.check(hasDown, hasNorth)) putLine(blb, brb)
            if (outlineMode.check(hasDown, hasSouth)) putLine(blf, brf)
            if (outlineMode.check(hasDown, hasWest))  putLine(blb, blf)
            if (outlineMode.check(hasDown, hasEast))  putLine(brb, brf)

            if (outlineMode.check(hasWest, hasNorth)) putLine(tlb, blb)
            if (outlineMode.check(hasNorth, hasEast)) putLine(trb, brb)
            if (outlineMode.check(hasEast, hasSouth)) putLine(trf, brf)
            if (outlineMode.check(hasSouth, hasWest)) putLine(tlf, blf)
        }
    }

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_dynamic")
    }
}