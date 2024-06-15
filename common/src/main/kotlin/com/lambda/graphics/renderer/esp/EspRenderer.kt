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
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

class EspRenderer(
    usage: BufferUsage = BufferUsage.STATIC
) {
    private val filled = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.STATIC_RENDERER, usage, true)
    private val filledVertices = ConcurrentHashMap<Vertex, Int>()

    private val outline = VAO(VertexMode.LINES, VertexAttrib.Group.STATIC_RENDERER, usage, true)
    private val outlineVertices = ConcurrentHashMap<Vertex, Int>()

    private var updateFilled = false
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

    fun buildConvexHull(
        boxes: Set<Box>,
        color: Color
    ) = outline.use {
        updateOutline = true

        // Step 1: Collect all vertices
        val vertices = mutableSetOf<Vertex>()
        boxes.forEach { box ->
            val pos1 = box.min
            val pos2 = box.max

            vertices.add(Vertex(pos1.x, pos1.y, pos1.z, color))
            vertices.add(Vertex(pos1.x, pos1.y, pos2.z, color))
            vertices.add(Vertex(pos2.x, pos1.y, pos1.z, color))
            vertices.add(Vertex(pos2.x, pos1.y, pos2.z, color))
            vertices.add(Vertex(pos1.x, pos2.y, pos1.z, color))
            vertices.add(Vertex(pos1.x, pos2.y, pos2.z, color))
            vertices.add(Vertex(pos2.x, pos2.y, pos1.z, color))
            vertices.add(Vertex(pos2.x, pos2.y, pos2.z, color))
        }

        // Step 2: Find the convex hull
        val convexHull = findConvexHull(vertices)

        // Step 3: Build outline of convex hull
        for (i in convexHull.indices) {
            val v1 = convexHull[i]
            val v2 = convexHull[(i + 1) % convexHull.size]
            val intV1 = vec3(v1.x, v1.y, v1.z).color(v1.color).end()
            val intV2 = vec3(v2.x, v2.y, v2.z).color(v2.color).end()
            putLine(intV1, intV2)
        }
    }

    // Utility function to find the convex hull using the QuickHull algorithm
    private fun findConvexHull(vertices: Set<Vertex>): List<Vertex> {
        if (vertices.size <= 1) return vertices.toList()

        val points = vertices.toMutableList()
        points.sortWith(compareBy({ it.x }, { it.y }))
        val left = points.first()
        val right = points.last()

        val (leftSet, rightSet) = points.partition { isLeft(left, right, it) }

        val hull = mutableListOf<Vertex>()
        hull.add(left)
        hull.addAll(findHull(left, right, leftSet))
        hull.add(right)
        hull.addAll(findHull(right, left, rightSet))

        return hull
    }

    // Check if the point is on the left side of the line from start to end
    private fun isLeft(start: Vertex, end: Vertex, point: Vertex): Boolean {
        return (end.x - start.x) * (point.y - start.y) - (end.y - start.y) * (point.x - start.x) > 0
    }

    // Recursively find the hull points
    private fun findHull(start: Vertex, end: Vertex, points: List<Vertex>): List<Vertex> {
        if (points.isEmpty()) return emptyList()

        val farthest = points.maxByOrNull { distanceFromLine(start, end, it) } ?: return emptyList()

        // Partition points into two sets: those to the left of the line (start, farthest) and those to the left of (farthest, end)
        val (leftSetStartFarthest, _) = points.partition { isLeft(start, farthest, it) }
        val (leftSetFarthestEnd, _) = points.partition { isLeft(farthest, end, it) }

        val hull = mutableListOf<Vertex>()
        hull.addAll(findHull(start, farthest, leftSetStartFarthest))
        hull.add(farthest)
        hull.addAll(findHull(farthest, end, leftSetFarthestEnd))

        return hull
    }

    // Calculate the distance from the line
    private fun distanceFromLine(start: Vertex, end: Vertex, point: Vertex): Double {
        val area = abs((end.x - start.x) * (point.y - start.y) - (end.y - start.y) * (point.x - start.x))
        val base = Math.hypot((end.x - start.x), (end.y - start.y))
        return area / base
    }

    fun buildFilled(box: Box, color: Color, sides: Int = DirectionMask.ALL) = filled.use {
        updateFilled = true
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

    fun buildOutline(box: Box, color: Color, sides: Int = DirectionMask.ALL, outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR) = outline.use {
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

    fun upload() {
        if (updateFilled) {
            updateFilled = false
            filled.upload()
        }

        if (updateOutline) {
            updateOutline = false
            outline.upload()
        }
    }

    fun render() {
        shader.use()
        shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

        withFaceCulling(filled::render)
        withLineWidth(RenderSettings.outlineWidth, outline::render)
    }

    fun clear() {
        filledVertices.clear()
        outlineVertices.clear()

        filled.clear()
        outline.clear()
    }

    private fun IRenderContext.vertex(
        x: Double, y: Double, z: Double,
        color: Color
    ) = lazy {
        val newVertex = {
            vec3(x, y, z).color(color).end()
        }

        if (RenderSettings.vertexMapping) {
            filledVertices.getOrPut(Vertex(x, y, z, color), newVertex)
        } else newVertex()
    }

    private data class Vertex(val x: Double, val y: Double, val z: Double, val color: Color)
    private data class Edge(val vertex1: Vertex, val vertex2: Vertex)

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_static")
    }
}