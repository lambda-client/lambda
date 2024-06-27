package com.lambda.graphics.renderer.esp

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vao.IRenderContext
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withFaceCulling
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.RenderSettings
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

open class ESPRenderer(
    usage: BufferUsage
) {
    val faces = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.STATIC_RENDERER, usage)
    val faceVertices = ConcurrentHashMap<Vertex, Int>()

    val outlines = VAO(VertexMode.LINES, VertexAttrib.Group.STATIC_RENDERER, usage)
    val outlineVertices = ConcurrentHashMap<Vertex, Int>()

    var updateFaces = false
    var updateOutlines = false

    fun upload() {
        if (updateFaces) {
            updateFaces = false
            faces.upload()
        }

        if (updateOutlines) {
            updateOutlines = false
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

    companion object {
        private val shader = Shader("renderer/pos_color", "renderer/box_static")
    }
}