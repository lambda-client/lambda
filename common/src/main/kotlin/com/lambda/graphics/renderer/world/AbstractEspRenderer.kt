package com.lambda.graphics.renderer.world

import com.lambda.Lambda.mc
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.Renderer
import com.lambda.graphics.shader.Shader
import com.lambda.util.primitives.extension.partialTicks
import net.minecraft.util.math.Vec3d

abstract class AbstractEspRenderer <T: IRenderEntry<T>> (
    vertexMode: VertexMode,
    shader: Shader,
    private val dynamic: Boolean
) : Renderer<T>(shader) {
    override val vao = VAO(vertexMode,
        if (dynamic) VertexAttrib.Group.DYNAMIC_RENDERER
        else VertexAttrib.Group.STATIC_RENDERER
    )

    override fun preRender() {
        if (dynamic) shader["u_TickDelta"] = mc.partialTicks
        shader["u_CameraPosition"] = mc.gameRenderer.camera.pos
    }
}