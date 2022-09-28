package com.lambda.client.util.graphics

import com.lambda.client.LambdaMod
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.events.ResolutionUpdateEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.util.Wrapper
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.shader.Framebuffer
import net.minecraft.client.shader.ShaderGroup
import net.minecraft.client.shader.ShaderLinkHelper
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.GL_VENDOR

class ShaderHelper(shaderIn: ResourceLocation, vararg frameBufferNames: String) {
    private val mc = Wrapper.minecraft

    val shader: ShaderGroup?
    private val frameBufferMap = HashMap<String, Framebuffer>()
    private var frameBuffersInitialized = false

    init {
        shader = when {
            !OpenGlHelper.shadersSupported -> {
                LambdaMod.LOG.warn("Shaders are unsupported by OpenGL!")
                null
            }

            isIntegratedGraphics -> {
                LambdaMod.LOG.warn("Running on Intel Integrated Graphics!")
                null
            }

            else -> {
                try {
                    ShaderLinkHelper.setNewStaticShaderLinkHelper()

                    ShaderGroup(mc.textureManager, mc.resourceManager, mc.framebuffer, shaderIn).also {
                        it.createBindFramebuffers(mc.displayWidth, mc.displayHeight)
                    }
                } catch (e: Exception) {
                    LambdaMod.LOG.warn("Failed to load shaders")
                    e.printStackTrace()

                    null
                }?.also {
                    for (name in frameBufferNames) {
                        frameBufferMap[name] = it.getFramebufferRaw(name)
                    }
                }
            }

        }

        listener<TickEvent.ClientTickEvent> {
            if (!frameBuffersInitialized) {
                shader?.createBindFramebuffers(mc.displayWidth, mc.displayHeight)

                frameBuffersInitialized = true
            }
        }

        listener<ResolutionUpdateEvent> {
            shader?.createBindFramebuffers(it.width, it.height) // this will not run if on Intel GPU or unsupported Shaders
        }

        LambdaEventBus.subscribe(this)
    }

    fun getFrameBuffer(name: String) = frameBufferMap[name]

    companion object {
        val isIntegratedGraphics by lazy {
            GlStateManager.glGetString(GL_VENDOR).contains("Intel")
        }
    }
}