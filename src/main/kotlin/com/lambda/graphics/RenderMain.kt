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

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.mc.renderer.RendererUtils
import com.lambda.graphics.outline.OutlineRenderer
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.abs

object RenderMain {
    val worldProjectionMatrix = Matrix4f()
    val baseProjectionMatrix = Matrix4f()
    val cameraRotationMatrix = Matrix4f()
    
    val modelViewMatrix
        get() = Matrices.peek()
    val projModel: Matrix4f
        get() = Matrix4f(worldProjectionMatrix).mul(cameraRotationMatrix).mul(modelViewMatrix)

    fun worldToScreenNormalized(worldPos: Vec3d): Vector2f? {
        val camera = mc.gameRenderer?.camera ?: return null
        val cameraPos = camera.pos

        val relX = (worldPos.x - cameraPos.x).toFloat()
        val relY = (worldPos.y - cameraPos.y).toFloat()
        val relZ = (worldPos.z - cameraPos.z).toFloat()

        val vec = Vector4f(relX, relY, relZ, 1f)
        projModel.transform(vec)

        val isBehind = vec.w < 0
        val w = if (abs(vec.w) < 0.001f) 0.001f else abs(vec.w)

        var ndcX = vec.x / w
        var ndcY = vec.y / w

        if (isBehind) {
            val len = kotlin.math.sqrt(ndcX * ndcX + ndcY * ndcY)
            if (len > 0.0001f) {
                ndcX = (ndcX / len) * 3f
                ndcY = (ndcY / len) * 3f
            } else ndcY = -3f
        }

        val normalizedX = (ndcX + 1f) * 0.5f
        val normalizedY = (ndcY + 1f) * 0.5f

        return Vector2f(normalizedX, normalizedY)
    }

    fun isOnScreen(worldPos: Vec3d): Boolean {
        val camera = mc.gameRenderer?.camera ?: return false
        val cameraPos = camera.pos

        val relX = (worldPos.x - cameraPos.x).toFloat()
        val relY = (worldPos.y - cameraPos.y).toFloat()
        val relZ = (worldPos.z - cameraPos.z).toFloat()
        val vec = Vector4f(relX, relY, relZ, 1f)
        projModel.transform(vec)
        if (vec.w <= 0) return false
        
        val pos = worldToScreenNormalized(worldPos) ?: return false
        return pos.x in 0f..1f && pos.y in 0f..1f
    }

    @JvmStatic
    fun preRender() {
        com.lambda.graphics.outline.OutlineManager.clear()
        com.lambda.graphics.outline.VertexCapture.clear()
        com.lambda.graphics.outline.OutlineRenderManager.clearFramebuffer()
        com.lambda.graphics.outline.OutlineIdBuffer.beginFrame()
    }

    @JvmStatic
    fun updateState(camRotMatrix: Matrix4f, basicProjMatrix: Matrix4f, projMatrix: Matrix4f) {
        cameraRotationMatrix.set(camRotMatrix)
        worldProjectionMatrix.set(basicProjMatrix)
        baseProjectionMatrix.set(projMatrix)
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
