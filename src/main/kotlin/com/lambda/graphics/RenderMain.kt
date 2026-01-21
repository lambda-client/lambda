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
import com.lambda.event.events.ScreenRenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.graphics.mc.renderer.TickedRenderer
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f

object RenderMain {
    @JvmStatic
    val staticESP = TickedRenderer("Static")

    @JvmStatic
    val dynamicESP = ImmediateRenderer("Dynamic")

    val projectionMatrix = Matrix4f()
    val modelViewMatrix
        get() = Matrices.peek()
    val projModel: Matrix4f
        get() = Matrix4f(projectionMatrix).mul(modelViewMatrix)

    /**
     * Project a world position to normalized screen coordinates (0-1 range).
     * This is the format used by RenderBuilder's screen methods (screenText, screenRect, etc).
     * 
     * Always returns coordinates, even if off-screen or behind camera.
     * For behind-camera positions, the direction is preserved (useful for tracers).
     *
     * @param worldPos The world position to project
     * @return Normalized screen coordinates (x, y)
     */
    fun worldToScreenNormalized(worldPos: Vec3d): Vector2f? {
        val camera = mc.gameRenderer?.camera ?: return null
        val cameraPos = camera.pos

        // Camera-relative position
        val relX = (worldPos.x - cameraPos.x).toFloat()
        val relY = (worldPos.y - cameraPos.y).toFloat()
        val relZ = (worldPos.z - cameraPos.z).toFloat()

        // Apply projection * modelview matrix
        val vec = Vector4f(relX, relY, relZ, 1f)
        projModel.transform(vec)

        val isBehind = vec.w < 0
        val w = if (kotlin.math.abs(vec.w) < 0.001f) 0.001f else kotlin.math.abs(vec.w)

        // Perspective divide to get NDC (-1 to 1)
        var ndcX = vec.x / w
        var ndcY = vec.y / w

        // When behind camera, extend the direction past the screen edge
        // so tracers go off-screen rather than landing on-screen
        if (isBehind) {
            // Normalize the direction and extend to a fixed off-screen distance
            val len = kotlin.math.sqrt(ndcX * ndcX + ndcY * ndcY)
            if (len > 0.0001f) {
                // Extend to 3.0 in NDC space (well past the -1 to 1 range)
                ndcX = (ndcX / len) * 3f
                ndcY = (ndcY / len) * 3f
            } else {
                // If almost directly behind, push down (arbitrary direction)
                // With Y-up, negative Y means down
                ndcY = -3f
            }
        }

        // NDC to normalized 0-1 coordinates 
        // Y-up convention: 0 = bottom, 1 = top (matches screen rendering)
        val normalizedX = (ndcX + 1f) * 0.5f
        val normalizedY = (ndcY + 1f) * 0.5f  // No flip for Y-up

        return Vector2f(normalizedX, normalizedY)
    }

    /** Check if a world position is visible on screen (within 0-1 bounds and in front of camera). */
    fun isOnScreen(worldPos: Vec3d): Boolean {
        val camera = mc.gameRenderer?.camera ?: return false
        val cameraPos = camera.pos
        
        // Check if in front of camera first
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
    fun render3D(positionMatrix: Matrix4f, projMatrix: Matrix4f) {
        resetMatrices(positionMatrix)
        projectionMatrix.set(projMatrix)

        staticESP.render()

        RenderEvent.Render.post()
        dynamicESP.render()
    }

    /**
     * Render all screen-space elements.
     * Called after Minecraft's guiRenderer.render() to ensure Lambda's
     * screen elements appear above all of Minecraft's GUI.
     */
    @JvmStatic
    fun renderScreen() {
        // Render screen-space elements from the main renderers
        staticESP.renderScreen()
        dynamicESP.renderScreen()
        
        // Post event for modules with custom renderers
        ScreenRenderEvent.post()
    }

    init {
        listen<TickEvent.Post> {
            staticESP.clear()
            RenderEvent.UploadStatic.post()
            staticESP.upload()
        }
        listen<RenderEvent.Render> {
            dynamicESP.clear()
            RenderEvent.UploadDynamic.post()
            dynamicESP.upload()
        }
    }
}
