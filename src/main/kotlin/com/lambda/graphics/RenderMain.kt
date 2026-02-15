/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.graphics

import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.graphics.mc.renderer.RendererUtils
import com.lambda.graphics.outline.OutlineIdBuffer
import com.lambda.graphics.outline.OutlineManager
import com.lambda.graphics.outline.OutlineRenderer
import com.lambda.graphics.outline.VertexCapture
import org.joml.Matrix4f

object RenderMain {
    val baseProjectionMatrix = Matrix4f()
    val worldProjectionMatrix = Matrix4f()
    val cameraRotationMatrix = Matrix4f()

    val projModel: Matrix4f
        get() = Matrix4f(worldProjectionMatrix).mul(cameraRotationMatrix)

    @JvmStatic
    fun preRender() {
        OutlineManager.clear()
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
        OutlineRenderer.renderEdges()
        
        RenderEvent.RenderScreen.post()
    }
}
