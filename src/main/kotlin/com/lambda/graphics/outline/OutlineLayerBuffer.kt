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

object OutlineLayerBuffer {
    
    private var colorTexture: GpuTexture? = null
    private var colorTextureView: GpuTextureView? = null
    
    private var bufferWidth = 0
    private var bufferHeight = 0
    
    var hasData = false
        private set
    
    fun ensureBuffer(): Boolean {
        val framebuffer = mc.framebuffer ?: return false
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight
        
        if (colorTexture == null || bufferWidth != width || bufferHeight != height) {
            cleanup()
            
            val gpuDevice = RenderSystem.getDevice()
            
            colorTexture = gpuDevice.createTexture(
                { "Lambda Outline Layer Buffer" },
                15,
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
    
    fun beginFrame(): Boolean {
        if (!ensureBuffer()) return false
        hasData = false
        
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Clear Outline Layer Buffer" },
                colorTextureView,
                OptionalInt.of(0x00000000),
                null,
                OptionalDouble.empty()
            )?.close()
            
        return true
    }
    
    fun getTextureView(): GpuTextureView? = colorTextureView
    
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
