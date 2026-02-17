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

object OutlineIdBuffer {
    private var idTexture: GpuTexture? = null
    private var idTextureView: GpuTextureView? = null

    private var depthTexture: GpuTexture? = null
    private var depthTextureView: GpuTextureView? = null
    
    private var bufferWidth = 0
    private var bufferHeight = 0
    
    var hasData = false
        private set
    
    fun ensureBuffer(): Boolean {
        val framebuffer = mc.framebuffer ?: return false
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight
        
        if (idTexture == null || bufferWidth != width || bufferHeight != height) {
            cleanup()
            
            val gpuDevice = RenderSystem.getDevice()
            
            idTexture = gpuDevice.createTexture(
                { "Lambda Entity ID Buffer" },
                15,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
            )
            idTextureView = gpuDevice.createTextureView(idTexture)
            
            depthTexture = gpuDevice.createTexture(
                { "Lambda Entity ID Depth Buffer" },
                15,
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
    
    fun beginFrame() {
        if (!ensureBuffer()) return
        hasData = false
        
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Clear Entity ID Buffer" },
                idTextureView,
                OptionalInt.of(0x00000000),
                depthTextureView,
                OptionalDouble.of(1.0)
            )?.close()
    }
    
    fun markHasData() {
        hasData = true
    }
    
    fun getMcDepthView(): GpuTextureView? = mc.framebuffer?.depthAttachmentView
    
    fun getDepthView(): GpuTextureView? = depthTextureView
    
    fun getSilhouetteDepthView(): GpuTextureView? = depthTextureView
    
    fun getTextureView(): GpuTextureView? = idTextureView
    
    fun getWidth(): Int = bufferWidth
    fun getHeight(): Int = bufferHeight
    
    fun isReady(): Boolean = idTexture != null && idTextureView != null
    
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
