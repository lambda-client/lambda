package com.lambda.graphics.renderer.esp

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withFaceCulling
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.renderer.esp.DirectionMask.DOWN
import com.lambda.graphics.renderer.esp.DirectionMask.EAST
import com.lambda.graphics.renderer.esp.DirectionMask.NORTH
import com.lambda.graphics.renderer.esp.DirectionMask.SOUTH
import com.lambda.graphics.renderer.esp.DirectionMask.UP
import com.lambda.graphics.renderer.esp.DirectionMask.WEST
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

class EspRenderer(
    usage: BufferUsage = BufferUsage.STATIC
) {
    private val faces = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.STATIC_RENDERER, usage, true)
    private val faceVertices = ConcurrentHashMap<Vertex, Int>()

    private val outlines = VAO(VertexMode.LINES, VertexAttrib.Group.STATIC_RENDERER, usage, true)
    private val outlineVertices = ConcurrentHashMap<Vertex, Int>()

    private var updateFaces = false
    private var updateOutline = false

    fun build(
        box: Box,
        filledColor: Color,
        outlineColor: Color,
        sides: Int = DirectionMask.ALL,
        outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
    ) {
        buildFilled(box, filledColor, sides)
        buildOutline(box, outlineColor, sides, outlineMode)
    }

    fun buildFilled(box: Box, color: Color, sides: Int = DirectionMask.ALL) = faces.use {
        updateFaces = true
        val pos1 = box.min
        val pos2 = box.max

        grow(8)

        val blb by vertex(pos1.x, pos1.y, pos1.z, color)
        val blf by vertex(pos1.x, pos1.y, pos2.z, color)
        val brb by vertex(pos2.x, pos1.y, pos1.z, color)
        val brf by vertex(pos2.x, pos1.y, pos2.z, color)
        val tlb by vertex(pos1.x, pos2.y, pos1.z, color)
        val tlf by vertex(pos1.x, pos2.y, pos2.z, color)
        val trb by vertex(pos2.x, pos2.y, pos1.z, color)
        val trf by vertex(pos2.x, pos2.y, pos2.z, color)

        if (sides.hasDirection(EAST))  putQuad(brb, trb, trf, brf)
        if (sides.hasDirection(WEST))  putQuad(blb, blf, tlf, tlb)
        if (sides.hasDirection(UP))    putQuad(tlb, tlf, trf, trb)
        if (sides.hasDirection(DOWN))  putQuad(blb, brb, brf, blf)
        if (sides.hasDirection(SOUTH)) putQuad(blf, brf, trf, tlf)
        if (sides.hasDirection(NORTH)) putQuad(blb, tlb, trb, brb)
    }

    fun buildOutline(
        box: Box,
        color: Color,
        sides: Int = DirectionMask.ALL,
        outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
    ) = outlines.use {
        updateOutline = true
        val pos1 = box.min
        val pos2 = box.max

        grow(8)

        val blb by vertex(pos1.x, pos1.y, pos1.z, color)
        val blf by vertex(pos1.x, pos1.y, pos2.z, color)
        val brb by vertex(pos2.x, pos1.y, pos1.z, color)
        val brf by vertex(pos2.x, pos1.y, pos2.z, color)
        val tlb by vertex(pos1.x, pos2.y, pos1.z, color)
        val tlf by vertex(pos1.x, pos2.y, pos2.z, color)
        val trb by vertex(pos2.x, pos2.y, pos1.z, color)
        val trf by vertex(pos2.x, pos2.y, pos2.z, color)

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

    infix operator fun Vec3d.compareTo(other: Vec3d) =
        lengthSquared().compareTo(other.lengthSquared())

    fun buildMesh(boxes: Set<Box>, color: Color) {
        val edges = hashMapOf<Edge, Int>()
        val faces = hashMapOf<Face, Int>()

        fun addFace(v1: Vec3d, v2: Vec3d, v3: Vec3d, v4: Vec3d) {
            val face = Face(v1, v2, v3, v4)
            faces[face] = faces.getOrDefault(face, 0) + 1
        }

        fun addEdge(v1: Vec3d, v2: Vec3d) {
            val edge = if (v1 < v2) Edge(v1, v2) else Edge(v2, v1)
            edges[edge] = edges.getOrDefault(edge, 0) + 1
        }

        boxes.forEach { box ->
            val pos1 = box.min
            val pos2 = box.max

            val blb = Vec3d(pos1.x, pos1.y, pos1.z)
            val blf = Vec3d(pos1.x, pos1.y, pos2.z)
            val brb = Vec3d(pos2.x, pos1.y, pos1.z)
            val brf = Vec3d(pos2.x, pos1.y, pos2.z)
            val tlb = Vec3d(pos1.x, pos2.y, pos1.z)
            val tlf = Vec3d(pos1.x, pos2.y, pos2.z)
            val trb = Vec3d(pos2.x, pos2.y, pos1.z)
            val trf = Vec3d(pos2.x, pos2.y, pos2.z)

            addFace(blb, blf, brf, brb)
            addFace(tlb, tlf, trf, trb)
            addFace(blb, brb, trb, tlb)
            addFace(blf, brf, trf, tlf)
            addFace(blb, blf, tlf, tlb)
            addFace(brb, brf, trf, trb)

            addEdge(tlb, trb)
            addEdge(tlf, trf)
            addEdge(tlb, tlf)
            addEdge(trf, trb)

            addEdge(blb, brb)
            addEdge(blf, brf)
            addEdge(blb, blf)
            addEdge(brb, brf)

            addEdge(tlb, blb)
            addEdge(trb, brb)
            addEdge(trf, brf)
            addEdge(tlf, blf)
        }

        this.faces.use {
            updateFaces = true
            faces.forEach { (face, count) ->
                if (count % 2 == 0) return@forEach
                grow(1)
                putQuad(
                    vec3(face.v1.x, face.v1.y, face.v1.z).color(color).end(),
                    vec3(face.v2.x, face.v2.y, face.v2.z).color(color).end(),
                    vec3(face.v3.x, face.v3.y, face.v3.z).color(color).end(),
                    vec3(face.v4.x, face.v4.y, face.v4.z).color(color).end()
                )
            }
        }

        outlines.use {
            updateOutline = true
            edges.forEach { (edge, count) ->
                if (count % 2 == 0) return@forEach
                grow(1)
                putLine(
                    vec3(edge.start.x, edge.start.y, edge.start.z).color(color).end(),
                    vec3(edge.end.x, edge.end.y, edge.end.z).color(color).end()
                )
            }
        }
    }

    fun upload() {
        if (updateFaces) {
            updateFaces = false
            faces.upload()
        }

        if (updateOutline) {
            updateOutline = false
            outlines.upload()
        }
    }

    fun render() {
        shader.use()
        shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

        withFaceCulling(faces::render)
        withLineWidth(RenderSettings.outlineWidth, outlines::render)
    }

    fun clear() {
        faceVertices.clear()
        outlineVertices.clear()

        faces.clear()
        outlines.clear()
    }

    private fun IRenderContext.vertex(
        x: Double, y: Double, z: Double,
        color: Color
    ) = lazy {
        val newVertex = {
            vec3(x, y, z).color(color).end()
        }

        if (RenderSettings.vertexMapping) {
            faceVertices.getOrPut(Vertex(x, y, z, color), newVertex)
        } else newVertex()
    }

    data class Vertex(val x: Double, val y: Double, val z: Double, val color: Color)
    data class Edge(val start: Vec3d, val end: Vec3d)
    data class Face(val v1: Vec3d, val v2: Vec3d, val v3: Vec3d, val v4: Vec3d)

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_static")
    }
}