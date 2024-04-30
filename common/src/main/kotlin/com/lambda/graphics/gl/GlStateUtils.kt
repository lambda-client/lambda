package com.lambda.graphics.gl

import org.lwjgl.opengl.GL30C.*

object GlStateUtils {
    private var depthTestState = true
    private var blendState = false
    private var cullState = true

    fun setupGL(block: () -> Unit) {
        val savedDepthTest = depthTestState
        val savedBlend = blendState
        val savedCull = cullState

        glDepthMask(false)
        lineSmooth(true)

        depthTest(false)
        blend(true)
        cull(false)


        block()

        glDepthMask(true)
        lineSmooth(false)

        depthTest(savedDepthTest)
        blend(savedBlend)
        cull(savedCull)
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

    private fun depthTest(flag: Boolean) {
        if (flag) glEnable(GL_DEPTH_TEST)
        else glDisable(GL_DEPTH_TEST)
    }

    private fun lineSmooth(flag: Boolean) {
        if (flag) glEnable(GL_LINE_SMOOTH)
        else glDisable(GL_LINE_SMOOTH)
    }
}
