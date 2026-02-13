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

import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import java.util.OptionalDouble
import java.util.OptionalInt

/**
 * Manages the entity ID buffer for outline rendering.
 * 
 * Stores entity IDs per-pixel during the ID pass. Used for:
 * - Per-entity outline selection
 * - Edge detection at entity boundaries
 * - Accurate silhouettes from MC's actual rendered geometry
 * 
 * Depth testing is handled by OutlineIdPassRenderer using either:
 * - MC's depth buffer (depthTest = true, respects world geometry)
 * - Lambda's xray depth buffer (depthTest = false, through walls)
 * 
 * Usage:
 * ```kotlin
 * OutlineIdBuffer.beginFrame()           // Clear to 0
 * // ... OutlineIdPassRenderer.render() handles depth buffer selection
 * OutlineIdBuffer.getTextureView()       // Sample in post-process
 * ```
 */
object OutlineIdBuffer {
    
    // RGBA8 texture - stores entity ID encoded as color per pixel
    private var idTexture: GpuTexture? = null
    private var idTextureView: GpuTextureView? = null
    
    // Depth texture - stores un-occluded entity depth for Sobel occlusion
    private var depthTexture: GpuTexture? = null
    private var depthTextureView: GpuTextureView? = null
    
    private var bufferWidth = 0
    private var bufferHeight = 0
    
    /** Whether any geometry has been rendered into the ID buffer this frame. */
    var hasData = false
        private set
    
    /**
     * Ensure ID buffer exists and matches framebuffer size.
     * @return true if buffer is ready, false if creation failed
     */
    fun ensureBuffer(): Boolean {
        val framebuffer = mc.framebuffer ?: return false
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight
        
        if (idTexture == null || bufferWidth != width || bufferHeight != height) {
            cleanup()
            
            val gpuDevice = RenderSystem.getDevice()
            
            // Create RGBA8 texture for entity IDs (encoded as color)
            // Each pixel stores the entity ID encoded in RGBA (0 = no entity / not outlined)
            idTexture = gpuDevice.createTexture(
                { "Lambda Entity ID Buffer" },
                15, // Usage: TRANSFER_SRC | TRANSFER_DST | TEXTURE | RENDER_ATTACHMENT
                TextureFormat.RGBA8,
                width,
                height,
                1, // layers
                1  // mip levels
            )
            idTextureView = gpuDevice.createTextureView(idTexture)
            
            // Create DEPTH32 texture for entity silhouettes (un-occluded depth)
            depthTexture = gpuDevice.createTexture(
                { "Lambda Entity ID Depth Buffer" },
                15, // Usage: TRANSFER_SRC | TRANSFER_DST | TEXTURE | RENDER_ATTACHMENT
                TextureFormat.DEPTH32,
                width,
                height,
                1,
                1
            )
            depthTextureView = gpuDevice.createTextureView(depthTexture)
            
            bufferWidth = width
            bufferHeight = height
        }
        
        return true
    }
    
    /**
     * Clear the ID buffer at the start of each frame.
     * Sets all pixels to 0 (no entity).
     * 
     * Note: Depth buffer is NOT cleared here - OutlineIdPassRenderer uses
     * MC's or Lambda's shared depth buffers depending on per-entity settings.
     */
    fun beginFrame() {
        if (!ensureBuffer()) return
        hasData = false
        
        // Clear only the ID buffer to 0 (no entity)
        // We create a render pass without depth attachment to clear just the color
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Clear Entity ID Buffer" },
                idTextureView,
                OptionalInt.of(0x00000000), // Clear to transparent black (entity ID = 0)
                depthTextureView,
                OptionalDouble.of(1.0) // Clear depth to 1.0 (far plane)
            )?.close()
    }
    
    /**
     * Mark that geometry has been rendered into the ID buffer.
     */
    fun markHasData() {
        hasData = true
    }
    
    /**
     * Get the Minecraft main depth buffer view.
     */
    fun getMcDepthView(): GpuTextureView? = mc.framebuffer?.depthAttachmentView
    
    /**
     * Get the Lambda Xray depth buffer view.
     */
    fun getDepthView(): GpuTextureView? = depthTextureView
    
    /**
     * Get the silhouette depth view.
     */
    fun getSilhouetteDepthView(): GpuTextureView? = depthTextureView
    
    /**
     * Get the ID texture view for sampling in post-processing.
     */
    fun getTextureView(): GpuTextureView? = idTextureView
    
    /**
     * Get buffer dimensions.
     */
    fun getWidth(): Int = bufferWidth
    fun getHeight(): Int = bufferHeight
    
    /**
     * Check if buffer is ready.
     */
    fun isReady(): Boolean = idTexture != null && idTextureView != null
    
    /**
     * Release all GPU resources.
     */
    fun cleanup() {
        idTextureView?.close()
        idTexture?.close()
        depthTextureView?.close()
        depthTexture?.close()
        
        idTextureView = null
        idTexture = null
        depthTextureView = null
        depthTexture = null
        bufferWidth = 0
        bufferHeight = 0
    }
}
