package com.lambda.graphics.renderer.esp.global

import com.lambda.Lambda
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.shader.Shader
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import com.lambda.util.primitives.extension.partialTicks
import com.lambda.util.primitives.extension.prevPos
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import java.awt.Color

object EntityESPRenderer {
    private val faces = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.DYNAMIC_RENDERER)
    private val outlines = VAO(VertexMode.LINES, VertexAttrib.Group.DYNAMIC_RENDERER)
    private val shader = Shader("renderer/pos_color", "renderer/box_dynamic")

    fun build(
        entity: Entity,
        filledColor: Color,
        outlineColor: Color,
        sides: Int = DirectionMask.ALL,
        outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
    ) {
        buildFilled(entity, filledColor, sides)
        buildOutline(entity, outlineColor, sides, outlineMode)
    }

    fun buildFilled(
        entity: Entity,
        color: Color,
        sides: Int = DirectionMask.ALL
    ) = faces.use {
        val box = entity.boundingBox

        val delta = entity.prevPos.subtract(entity.pos)
        val prevBox = Box(box.min.add(delta), box.max.add(delta))

        val pos11 = prevBox.min
        val pos12 = prevBox.max
        val pos21 = box.min
        val pos22 = box.max

        grow(8)

        val blb by lazy { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color).end() }
        val blf by lazy { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color).end() }
        val brb by lazy { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color).end() }
        val brf by lazy { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color).end() }
        val tlb by lazy { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color).end() }
        val tlf by lazy { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color).end() }
        val trb by lazy { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color).end() }
        val trf by lazy { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color).end() }

        if (sides.hasDirection(DirectionMask.EAST))  putQuad(brb, brf, trf, trb)
        if (sides.hasDirection(DirectionMask.WEST))  putQuad(blb, blf, tlf, tlb)
        if (sides.hasDirection(DirectionMask.UP))    putQuad(tlb, tlf, trf, trb)
        if (sides.hasDirection(DirectionMask.DOWN))  putQuad(blb, brb, brf, blf)
        if (sides.hasDirection(DirectionMask.SOUTH)) putQuad(blf, brf, trf, tlf)
        if (sides.hasDirection(DirectionMask.NORTH)) putQuad(blb, brb, trb, tlb)
    }

    fun buildOutline(
        entity: Entity,
        color: Color,
        sides: Int = DirectionMask.ALL,
        outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
    ) = outlines.use {
        val box = entity.boundingBox

        val delta = entity.prevPos.subtract(entity.pos)
        val prevBox = Box(box.min.add(delta), box.max.add(delta))

        val pos11 = prevBox.min
        val pos12 = prevBox.max
        val pos21 = box.min
        val pos22 = box.max

        grow(8)

        val blb by lazy { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color).end() }
        val blf by lazy { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color).end() }
        val brb by lazy { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color).end() }
        val brf by lazy { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color).end() }
        val tlb by lazy { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color).end() }
        val tlf by lazy { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color).end() }
        val trb by lazy { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color).end() }
        val trf by lazy { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color).end() }

        val hasEast  = sides.hasDirection(DirectionMask.EAST)
        val hasWest  = sides.hasDirection(DirectionMask.WEST)
        val hasUp    = sides.hasDirection(DirectionMask.UP)
        val hasDown  = sides.hasDirection(DirectionMask.DOWN)
        val hasSouth = sides.hasDirection(DirectionMask.SOUTH)
        val hasNorth = sides.hasDirection(DirectionMask.NORTH)

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

    fun render() {
        shader.use()
        shader["u_TickDelta"] = Lambda.mc.partialTicks
        shader["u_CameraPosition"] = Lambda.mc.gameRenderer.camera.pos

        faces.render()
        outlines.render()
    }

    init {
        listener<TickEvent.Post> {
            faces.clear()
            outlines.clear()

            RenderEvent.EntityESP().post()

            faces.upload()
            outlines.upload()
        }
    }
}