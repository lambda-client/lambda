
package com.minato.graphics.outline

import com.minato.Minato.mc
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import java.util.*

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
                { "Minato Entity ID Buffer" },
                15,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
            )
            idTextureView = gpuDevice.createTextureView(idTexture)
            
            depthTexture = gpuDevice.createTexture(
                { "Minato Entity ID Depth Buffer" },
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
                { "Minato Clear Entity ID Buffer" },
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
    
    fun getSilhouetteDepthView(): GpuTextureView? = depthTextureView
    
    fun getTextureView(): GpuTextureView? = idTextureView

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
