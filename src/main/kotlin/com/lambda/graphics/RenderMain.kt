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
     * Project a world position to screen coordinates. Returns null if the position is behind the
     * camera or off-screen.
     *
     * @param worldPos The world position to project
     * @return Screen coordinates (x, y) in pixels, or null if not visible
     */
    fun worldToScreen(worldPos: Vec3d): Vector2f? {
        val camera = mc.gameRenderer?.camera ?: return null
        val cameraPos = camera.pos

        // Camera-relative position
        val relX = (worldPos.x - cameraPos.x).toFloat()
        val relY = (worldPos.y - cameraPos.y).toFloat()
        val relZ = (worldPos.z - cameraPos.z).toFloat()

        // Apply projection * modelview matrix
        val vec = Vector4f(relX, relY, relZ, 1f)
        projModel.transform(vec)

        // Behind camera check
        if (vec.w <= 0) return null

        // Perspective divide to get NDC
        val ndcX = vec.x / vec.w
        val ndcY = vec.y / vec.w
        val ndcZ = vec.z / vec.w

        // Off-screen check (NDC is -1 to 1)
        if (ndcZ < -1 || ndcZ > 1) return null

        // NDC to screen coordinates (Y is flipped in screen space)
        val window = mc.window
        val screenX = (ndcX + 1f) * 0.5f * window.framebufferWidth
        val screenY = (1f - ndcY) * 0.5f * window.framebufferHeight

        return Vector2f(screenX, screenY)
    }

    /** Check if a world position is visible on screen. */
    fun isOnScreen(worldPos: Vec3d): Boolean = worldToScreen(worldPos) != null

    @JvmStatic
    fun render3D(positionMatrix: Matrix4f, projMatrix: Matrix4f) {
        resetMatrices(positionMatrix)
        projectionMatrix.set(projMatrix)

        staticESP.render()

        RenderEvent.Render.post()
        dynamicESP.render()
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
