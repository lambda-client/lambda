package com.lambda.graphics.renderer.esp

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.renderer.esp.DirectionMask.DOWN
import com.lambda.graphics.renderer.esp.DirectionMask.EAST
import com.lambda.graphics.renderer.esp.DirectionMask.NORTH
import com.lambda.graphics.renderer.esp.DirectionMask.SOUTH
import com.lambda.graphics.renderer.esp.DirectionMask.UP
import com.lambda.graphics.renderer.esp.DirectionMask.WEST
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.shader.Shader
import com.lambda.module.Module
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import net.minecraft.util.math.Box
import java.awt.Color

class CachedEspRenderer(val owner: Any) {
    private val filled = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.STATIC_RENDERER)
    private var updateFilled = false

    private val outline = VAO(VertexMode.LINES, VertexAttrib.Group.STATIC_RENDERER)
    private var updateOutline = false

    var outlineWidth = 1.0

    fun build(box: Box, filledColor: Color, outlineColor: Color, sides: Int = DirectionMask.ALL, outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR) {
        buildFilled(box, filledColor, sides)
        buildOutline(box, outlineColor, sides, outlineMode)
    }

    fun buildFilled(box: Box, color: Color, sides: Int = DirectionMask.ALL) = filled.use {
        updateFilled = true
        val pos1 = box.min
        val pos2 = box.max

        grow(8)

        val blb by lazy { vec3(pos1.x, pos1.y, pos1.z).color(color).end() }
        val blf by lazy { vec3(pos1.x, pos1.y, pos2.z).color(color).end() }
        val brb by lazy { vec3(pos2.x, pos1.y, pos1.z).color(color).end() }
        val brf by lazy { vec3(pos2.x, pos1.y, pos2.z).color(color).end() }
        val tlb by lazy { vec3(pos1.x, pos2.y, pos1.z).color(color).end() }
        val tlf by lazy { vec3(pos1.x, pos2.y, pos2.z).color(color).end() }
        val trb by lazy { vec3(pos2.x, pos2.y, pos1.z).color(color).end() }
        val trf by lazy { vec3(pos2.x, pos2.y, pos2.z).color(color).end() }

        if (sides.hasDirection(EAST))  putQuad(brb, brf, trf, trb)
        if (sides.hasDirection(WEST))  putQuad(blb, blf, tlf, tlb)
        if (sides.hasDirection(UP))    putQuad(tlb, tlf, trf, trb)
        if (sides.hasDirection(DOWN))  putQuad(blb, brb, brf, blf)
        if (sides.hasDirection(SOUTH)) putQuad(blf, brf, trf, tlf)
        if (sides.hasDirection(NORTH)) putQuad(blb, brb, trb, tlb)
    }

    fun buildOutline(box: Box, color: Color, sides: Int = DirectionMask.ALL, outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR) = outline.use {
        updateOutline = true
        val pos1 = box.min
        val pos2 = box.max

        grow(8)

        val blb by lazy { vec3(pos1.x, pos1.y, pos1.z).color(color).end() }
        val blf by lazy { vec3(pos1.x, pos1.y, pos2.z).color(color).end() }
        val brb by lazy { vec3(pos2.x, pos1.y, pos1.z).color(color).end() }
        val brf by lazy { vec3(pos2.x, pos1.y, pos2.z).color(color).end() }
        val tlb by lazy { vec3(pos1.x, pos2.y, pos1.z).color(color).end() }
        val tlf by lazy { vec3(pos1.x, pos2.y, pos2.z).color(color).end() }
        val trb by lazy { vec3(pos2.x, pos2.y, pos1.z).color(color).end() }
        val trf by lazy { vec3(pos2.x, pos2.y, pos2.z).color(color).end() }

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

    fun clear() {
        filled.clear()
        outline.clear()
    }

    init {
        owner.listener<RenderEvent.World> {
            if (updateFilled) {
                updateFilled = false
                filled.upload()
            }

            if (updateOutline) {
                updateOutline = false
                outline.upload()
            }

            shader.use()
            shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

            filled.render()
            withLineWidth(outlineWidth, outline::render)
        }
    }

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_static")
    }
}