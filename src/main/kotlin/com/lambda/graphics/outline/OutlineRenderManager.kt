/*
 * Copyright 2026 Lambda
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

package com.lambda.graphics.outline


import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.render.command.RenderDispatcher
import net.minecraft.client.render.state.WorldRenderState
import net.minecraft.client.util.math.MatrixStack

object OutlineRenderManager {
    private var framebuffer: SimpleFramebuffer? = null
    private val renderCommandQueue = LambdaOutlineRenderCommandQueue()
    private var renderDispatcher: RenderDispatcher? = null
    private var activeProvider: VertexConsumerProvider? = null
    private val vertexConsumerProvider = LambdaOutlineVertexConsumerProvider()
    
    @JvmStatic
    fun onPushEntityRenders(
        matrices: MatrixStack,
        worldState: WorldRenderState,
        worldRenderer: WorldRenderer
    ) {
        if (!OutlineManager.hasEntityOutlines()) return
        
        val mc = MinecraftClient.getInstance()

        ensureFramebuffer(mc)
        val fb = framebuffer ?: return

        if (renderDispatcher == null) {
            renderDispatcher = RenderDispatcher(
                renderCommandQueue,
                mc.blockRenderManager,
                WrapperImmediateVertexConsumerProvider { activeProvider },
                mc.atlasManager,
                NoopOutlineVertexConsumerProvider,
                NoopImmediateVertexConsumerProvider,
                mc.textRenderer
            )
        }

        val camera = worldState.cameraRenderState.pos

        var hasContent = false
        
        for (state in worldState.entityRenderStates) {
            if (state !is IEntityRenderState) continue
            
            val entityId = (state as IEntityRenderState).`lambda$getEntityId`()
            val outlineStyle = OutlineManager.getEntityOutline(entityId) ?: continue

            renderCommandQueue.setColor(outlineStyle.color.rgb)

            val renderer = mc.entityRenderManager.getRenderer(state)
            val offset = renderer.getPositionOffset(state)

            matrices.push()
            matrices.translate(
                state.x - camera.x + offset.x,
                state.y - camera.y + offset.y,
                state.z - camera.z + offset.z
            )

            renderer.render(state, matrices, renderCommandQueue, worldState.cameraRenderState)
            
            matrices.pop()
            
            hasContent = true
        }
        
        if (!hasContent) return

        val iwr = worldRenderer as IWorldRenderer
        iwr.`lambda$pushEntityOutlineFramebuffer`(fb)
        
        activeProvider = vertexConsumerProvider
        renderDispatcher?.render()
        vertexConsumerProvider.draw() // Actually submit the geometry to the GPU
        renderCommandQueue.onNextFrame()
        activeProvider = null
        
        iwr.`lambda$popEntityOutlineFramebuffer`()
    }
    
    fun clearFramebuffer() {
        val fb = framebuffer ?: return
        
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Lambda Clear Outline FB" },
            fb.colorAttachmentView,
            java.util.OptionalInt.of(0),
            fb.depthAttachmentView,
            java.util.OptionalDouble.of(1.0)
        )?.close()
    }
    
    fun getFramebuffer(): SimpleFramebuffer? = framebuffer
    
    fun onResized(width: Int, height: Int) {
        framebuffer?.resize(width, height)
    }
    
    private fun ensureFramebuffer(mc: MinecraftClient) {
        val width = mc.window.framebufferWidth
        val height = mc.window.framebufferHeight
        
        if (framebuffer == null) {
            framebuffer = SimpleFramebuffer(
                "Lambda Entity Outline",
                width,
                height,
                true
            )
        }
    }
    
    private class WrapperImmediateVertexConsumerProvider(
        private val providerGetter: () -> VertexConsumerProvider?
    ) : VertexConsumerProvider.Immediate(null, null) {
        
        override fun getBuffer(layer: RenderLayer): VertexConsumer {
            return providerGetter()?.getBuffer(layer) 
                ?: throw IllegalStateException("No active outline provider")
        }
        
        override fun draw() {}
        override fun draw(layer: RenderLayer) {}
    }
    
    private object NoopOutlineVertexConsumerProvider : net.minecraft.client.render.OutlineVertexConsumerProvider() {
        override fun getBuffer(layer: RenderLayer): VertexConsumer = NoopVertexConsumer
    }
    
    private object NoopImmediateVertexConsumerProvider : VertexConsumerProvider.Immediate(null, null) {
        override fun getBuffer(layer: RenderLayer): VertexConsumer = NoopVertexConsumer
        override fun draw() {}
        override fun draw(layer: RenderLayer) {}
    }
    
    private object NoopVertexConsumer : VertexConsumer {
        override fun vertex(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = this
        override fun color(argb: Int): VertexConsumer = this
        override fun texture(u: Float, v: Float): VertexConsumer = this
        override fun overlay(u: Int, v: Int): VertexConsumer = this
        override fun light(u: Int, v: Int): VertexConsumer = this
        override fun normal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun lineWidth(width: Float): VertexConsumer = this
    }
}

