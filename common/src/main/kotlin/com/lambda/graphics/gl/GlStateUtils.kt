package com.lambda.graphics.gl

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.RenderSystem.depthMask
import org.lwjgl.opengl.GL30C

@Suppress("NOTHING_TO_INLINE")
object GlStateUtils {
    private var depthTestState = true
    private var depthMaskState = true
    private var blendState = false
    private var cullState = true

    fun setupGL(block: () -> Unit) {
        val savedDepthTest = depthTestState
        val savedDepthMask = depthMaskState
        val savedBlend = blendState
        val savedCull = cullState

        depthTest(false)
        depthMask(false)
        blend(true)
        cull(false)
        lineSmooth(true)

        block()

        depthTest(savedDepthTest)
        depthMask(savedDepthMask)
        blend(savedBlend)
        cull(savedCull)
        lineSmooth(false)
    }

    @JvmStatic
    fun capSet(id: Int, flag: Boolean) {
        val field = when (id) {
            GL30C.GL_DEPTH_TEST -> ::depthTestState
            GL30C.GL_DEPTH -> ::depthMaskState
            GL30C.GL_BLEND -> ::blendState
            GL30C.GL_CULL_FACE -> ::cullState
            else -> return
        }

        field.set(flag)
    }

    private inline fun blend(flag: Boolean) {
        if (flag) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
        } else RenderSystem.disableBlend()
    }

    private inline fun cull(flag: Boolean) {
        if (flag) RenderSystem.enableCull()
        else RenderSystem.disableCull()
    }

    private inline fun depthTest(flag: Boolean) {
        if (flag) RenderSystem.enableDepthTest()
        else RenderSystem.disableDepthTest()
    }

    private inline fun lineSmooth(flag: Boolean) {
        if (flag) GL30C.glEnable(GL30C.GL_LINE_SMOOTH)
        else GL30C.glDisable(GL30C.GL_LINE_SMOOTH)
    }
}