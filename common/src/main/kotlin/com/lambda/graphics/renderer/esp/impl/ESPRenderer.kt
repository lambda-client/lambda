package com.lambda.graphics.renderer.esp.impl

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.VertexPipeline
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.GlStateUtils.withFaceCulling
import com.lambda.graphics.gl.GlStateUtils.withLineWidth
import com.lambda.graphics.shader.Shader
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.extension.partialTicks

abstract class ESPRenderer(tickedMode: Boolean) {
    val shader: Shader
    val faces: VertexPipeline
    val outlines: VertexPipeline

    init {
        val mode = if (tickedMode) dynamicMode else staticMode

        shader = mode.first
        faces = VertexPipeline(VertexMode.TRIANGLES, mode.second)
        outlines = VertexPipeline(VertexMode.LINES, mode.second)
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
