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
import com.lambda.graphics.gl.GlStateUtils.setupGL
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.renderer.esp.Treed
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.texture.GlTexture
import org.joml.Matrix4f
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER

object RenderMain {
    val projectionMatrix = Matrix4f()
    val modelViewMatrix get() = Matrices.peek()
    val projModel: Matrix4f get() = Matrix4f(projectionMatrix).mul(modelViewMatrix)

    var screenSize = Vec2d.ZERO

    @JvmStatic
    fun render3D(positionMatrix: Matrix4f, projMatrix: Matrix4f) {
        resetMatrices(positionMatrix)
        projectionMatrix.set(projMatrix)

        setupGL {
            val framebuffer = mc.framebuffer
            val prevFramebuffer = (framebuffer.getColorAttachment() as GlTexture).getOrCreateFramebuffer(
                (RenderSystem.getDevice() as GlBackend).framebufferManager,
                null
            )

            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFramebuffer)

            Treed.clear()
            RenderEvent.World().post()
            Treed.upload()
            Treed.render()
        }
    }

    init {
        listen<TickEvent.Post> {
            //Treed.clear()
        }
    }
}
