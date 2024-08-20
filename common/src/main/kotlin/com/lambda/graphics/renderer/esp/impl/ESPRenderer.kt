package com.lambda.graphics.renderer.esp.impl

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withFaceCulling
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.extension.partialTicks

abstract class ESPRenderer(
    usage: BufferUsage,
    tickedMode: Boolean
) {
    val shader: Shader
    val faces: VAO
    val outlines: VAO

    init {
        val mode = if (tickedMode) dynamicMode else staticMode

        shader = mode.first
        faces = VAO(VertexMode.TRIANGLES, mode.second, usage)
        outlines = VAO(VertexMode.LINES, mode.second, usage)
    }

    open fun upload() {
        faces.upload()
        outlines.upload()
    }

    fun render() {
        shader.use()
        shader["u_TickDelta"] = mc.partialTicks
        shader["u_CameraPosition"] = mc.gameRenderer.camera.pos

        withFaceCulling(faces::render)
        withLineWidth(RenderSettings.outlineWidth, outlines::render)
    }

    open fun clear() {
        faces.clear()
        outlines.clear()
    }

    companion object {
        private val staticMode = Shader(
            "renderer/pos_color",
            "renderer/box_static"
        ) to VertexAttrib.Group.STATIC_RENDERER

        private val dynamicMode = Shader(
            "renderer/pos_color",
            "renderer/box_dynamic"
        ) to VertexAttrib.Group.DYNAMIC_RENDERER
    }
}
