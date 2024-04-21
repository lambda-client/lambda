package com.lambda.graphics.gl

import org.lwjgl.opengl.GL30C.*

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
        glDepthMask(false)
        blend(true)
        cull(false)
        lineSmooth(true)

        block()

        depthTest(savedDepthTest)
        glDepthMask(savedDepthMask)
        blend(savedBlend)
        cull(savedCull)
        lineSmooth(false)
    }

    fun withDepth(block: () -> Unit) {
        depthTest(true)
        block()
        depthTest(false)
    }

    @JvmStatic
    fun capSet(id: Int, flag: Boolean) {
        val field = when (id) {
            GL_DEPTH_TEST -> ::depthTestState
            GL_DEPTH -> ::depthMaskState
            GL_BLEND -> ::blendState
            GL_CULL_FACE -> ::cullState
            else -> return
        }

        field.set(flag)
    }

    private fun blend(flag: Boolean) {
        if (flag) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        } else glDisable(GL_BLEND)
    }

    private fun cull(flag: Boolean) {
        if (flag) glEnable(GL_CULL_FACE)
        else glDisable(GL_CULL_FACE)
    }

    private fun depthTest(flag: Boolean) = glDepthMask(flag)

    private fun lineSmooth(flag: Boolean) {
        if (flag) glEnable(GL_LINE_SMOOTH)
        else glDisable(GL_LINE_SMOOTH)
    }
}
