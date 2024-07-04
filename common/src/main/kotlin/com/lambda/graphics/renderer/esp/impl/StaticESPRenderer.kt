package com.lambda.graphics.renderer.esp.impl

import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

open class StaticESPRenderer(
    usage: BufferUsage = BufferUsage.STATIC
) : ESPRenderer(usage, false) {
    val faceVertices = ConcurrentHashMap<Vertex, Int>()
    val outlineVertices = ConcurrentHashMap<Vertex, Int>()

    var updateFaces = false
    var updateOutlines = false

    override fun upload() {
        if (updateFaces) {
            updateFaces = false
            faces.upload()
        }

        if (updateOutlines) {
            updateOutlines = false
            outlines.upload()
        }
    }

    override fun clear() {
        faceVertices.clear()
        outlineVertices.clear()
        super.clear()
    }

    fun IRenderContext.vertex(
        storage: MutableMap<Vertex, Int>,
        x: Double, y: Double, z: Double,
        color: Color
    ) = lazy {
        storage.getOrPut(Vertex(x, y, z, color)) {
            vec3(x, y, z).color(color).end()
        }
    }

    data class Vertex(val x: Double, val y: Double, val z: Double, val color: Color)
}