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

/**
 * Coordinates entity outline rendering using MC's native outline RenderLayers.
 * 
 * Called from WorldRendererMixin during entity render pass to re-render
 * targeted entities to a custom framebuffer with their outline colors.
 */
object OutlineRenderManager {
    
    private var framebuffer: SimpleFramebuffer? = null
    private val renderCommandQueue = LambdaOutlineRenderCommandQueue()
    private var renderDispatcher: RenderDispatcher? = null
    private var activeProvider: VertexConsumerProvider? = null
    private val vertexConsumerProvider = LambdaOutlineVertexConsumerProvider()
    
    /**
     * Called from WorldRendererMixin.onPushEntityRenders.
     * Re-renders outlined entities to our custom framebuffer.
     */
    @JvmStatic
    fun onPushEntityRenders(
        matrices: MatrixStack,
        worldState: WorldRenderState,
        worldRenderer: WorldRenderer
    ) {
        // Skip if no outlines registered
        if (!OutlineManager.hasEntityOutlines()) return
        
        val mc = MinecraftClient.getInstance()
        
        // Ensure framebuffer exists
        ensureFramebuffer(mc)
        val fb = framebuffer ?: return
        
        // Ensure render dispatcher exists 
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
        
        // Get camera position
        val camera = worldState.cameraRenderState.pos
        
        // Check if we have any entities to render
        var hasContent = false
        
        for (state in worldState.entityRenderStates) {
            if (state !is IEntityRenderState) continue
            
            val entityId = (state as IEntityRenderState).`lambda$getEntityId`()
            val outlineStyle = OutlineManager.getEntityOutline(entityId) ?: continue
            
            // Set the outline color for this entity
            renderCommandQueue.setColor(outlineStyle.color.rgb)
            
            // Get renderer for this entity state
            val renderer = mc.entityRenderManager.getRenderer(state)
            val offset = renderer.getPositionOffset(state)
            
            // Push matrix and translate to entity position
            matrices.push()
            matrices.translate(
                state.x - camera.x + offset.x,
                state.y - camera.y + offset.y,
                state.z - camera.z + offset.z
            )
            
            // Render entity to our render command queue (which applies outline colors)
            renderer.render(state, matrices, renderCommandQueue, worldState.cameraRenderState)
            
            matrices.pop()
            
            hasContent = true
        }
        
        if (!hasContent) return
        
        // Swap framebuffer and render
        val iwr = worldRenderer as IWorldRenderer
        iwr.`lambda$pushEntityOutlineFramebuffer`(fb)
        
        activeProvider = vertexConsumerProvider
        renderDispatcher?.render()
        vertexConsumerProvider.draw() // Actually submit the geometry to the GPU
        renderCommandQueue.onNextFrame()
        activeProvider = null
        
        iwr.`lambda$popEntityOutlineFramebuffer`()
    }
    
    /**
     * Clear the outline framebuffer at the start of each frame.
     */
    fun clearFramebuffer() {
        val fb = framebuffer ?: return
        
        // Always clear the framebuffer to avoid ghosting from previous frames.
        // We use a render pass with clear colors to ensure submission via .close()
        // following the pattern in RendererUtils.
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Lambda Clear Outline FB" },
            fb.colorAttachmentView,
            java.util.OptionalInt.of(0),        // Clear color to 0 (fully transparent)
            fb.depthAttachmentView,
            java.util.OptionalDouble.of(1.0)    // Clear depth to 1.0 (far)
        )?.close()
    }
    
    /**
     * Get the framebuffer for post-processing.
     */
    fun getFramebuffer(): SimpleFramebuffer? = framebuffer
    
    /**
     * Handle window resize.
     */
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
    
    /**
     * Wrapper that extends VertexConsumerProvider.Immediate to satisfy RenderDispatcher.
     * Delegates actual buffer creation to the active provider.
     */
    private class WrapperImmediateVertexConsumerProvider(
        private val providerGetter: () -> VertexConsumerProvider?
    ) : VertexConsumerProvider.Immediate(null, null) {
        
        override fun getBuffer(layer: RenderLayer): VertexConsumer {
            return providerGetter()?.getBuffer(layer) 
                ?: throw IllegalStateException("No active outline provider")
        }
        
        override fun draw() {
            // Drawing is handled by the actual provider
        }
        
        override fun draw(layer: RenderLayer) {
            // Drawing is handled by the actual provider
        }
    }
    
    /**
     * No-op outline vertex consumer provider for unused RenderDispatcher parameter.
     */
    private object NoopOutlineVertexConsumerProvider : net.minecraft.client.render.OutlineVertexConsumerProvider() {
        override fun getBuffer(layer: RenderLayer): VertexConsumer = NoopVertexConsumer
    }
    
    /**
     * No-op immediate vertex consumer provider for unused RenderDispatcher parameter.
     */
    private object NoopImmediateVertexConsumerProvider : VertexConsumerProvider.Immediate(null, null) {
        override fun getBuffer(layer: RenderLayer): VertexConsumer = NoopVertexConsumer
        override fun draw() {}
        override fun draw(layer: RenderLayer) {}
    }
    
    /**
     * No-op vertex consumer that discards all data.
     */
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

