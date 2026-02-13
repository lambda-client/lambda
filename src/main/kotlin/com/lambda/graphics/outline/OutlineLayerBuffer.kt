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
 * Manages a separate buffer for layered outline rendering.
 * 
 * Outlined objects are rendered once to this buffer instead of the main framebuffer.
 * This allows applying post-processing (outlines, glow) to the isolated geometry
 * before compositing it back into the scene.
 */
object OutlineLayerBuffer {
    
    private var colorTexture: GpuTexture? = null
    private var colorTextureView: GpuTextureView? = null
    
    private var bufferWidth = 0
    private var bufferHeight = 0
    
    /** Whether any geometry has been rendered into the layer buffer this frame. */
    var hasData = false
        private set
    
    /**
     * Ensure buffer exists and matches framebuffer size.
     */
    fun ensureBuffer(): Boolean {
        val framebuffer = mc.framebuffer ?: return false
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight
        
        if (colorTexture == null || bufferWidth != width || bufferHeight != height) {
            cleanup()
            
            val gpuDevice = RenderSystem.getDevice()
            
            // Create RGBA8 texture for layered geometry
            colorTexture = gpuDevice.createTexture(
                { "Lambda Outline Layer Buffer" },
                15, // Usage: TRANSFER_SRC | TRANSFER_DST | TEXTURE | RENDER_ATTACHMENT
                TextureFormat.RGBA8,
                width,
                height,
                1, 1
            )
            colorTextureView = gpuDevice.createTextureView(colorTexture)
            
            bufferWidth = width
            bufferHeight = height
        }
        
        return true
    }
    
    /**
     * Clear the color buffer and return true if ready.
     * Shares the main depth buffer for correct occlusion.
     */
    fun beginFrame(): Boolean {
        if (!ensureBuffer()) return false
        hasData = false
        
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Clear Outline Layer Buffer" },
                colorTextureView,
                OptionalInt.of(0x00000000), // Clear to transparent
                null, // No depth clear here - we share with main
                OptionalDouble.empty()
            )?.close()
            
        return true
    }
    
    fun getTextureView(): GpuTextureView? = colorTextureView
    
    /**
     * Mark that geometry has been rendered into the layer buffer.
     */
    fun markHasData() {
        hasData = true
    }
    
    fun cleanup() {
        colorTextureView?.close()
        colorTexture?.close()
        colorTextureView = null
        colorTexture = null
    }
}
