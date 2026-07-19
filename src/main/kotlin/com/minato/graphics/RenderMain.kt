
package com.minato.graphics

import com.minato.event.EventFlow.post
import com.minato.event.events.RenderEvent
import com.minato.graphics.mc.renderer.RendererUtils
import com.minato.graphics.outline.OutlineHandler
import com.minato.graphics.outline.OutlineIdBuffer
import com.minato.graphics.outline.OutlineRenderer
import com.minato.graphics.outline.VertexCapture
import org.joml.Matrix4f

object RenderMain {
    val baseProjectionMatrix = Matrix4f()
    val worldProjectionMatrix = Matrix4f()
    val cameraRotationMatrix = Matrix4f()

    val projModel: Matrix4f
        get() = Matrix4f(worldProjectionMatrix).mul(cameraRotationMatrix)

    @JvmStatic
    fun preRender() {
        OutlineHandler.clear()
        VertexCapture.clear()
        OutlineIdBuffer.beginFrame()
    }

    @JvmStatic
    fun updateState(camRotMatrix: Matrix4f, basicProjMatrix: Matrix4f, projMatrix: Matrix4f) {
        baseProjectionMatrix.set(projMatrix)
        worldProjectionMatrix.set(basicProjMatrix)
        cameraRotationMatrix.set(camRotMatrix)
    }

    @JvmStatic
    fun render() {
        RendererUtils.clearXrayDepthBuffer()

        RenderEvent.RenderWorld.post()

        OutlineRenderer.renderAllIDPasses()
        
        RenderEvent.RenderScreen.post()
    }
}
