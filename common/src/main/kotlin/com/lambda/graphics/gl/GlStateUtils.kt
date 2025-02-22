/*
 * Copyright 2024 Lambda
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

package com.lambda.graphics.gl

import org.lwjgl.opengl.GL30C.*

object GlStateUtils {
    private var depthTestState = true
    private var blendState = false
    private var cullState = true

    /**
     * Temporarily configures OpenGL states for rendering, executes the provided block, and then restores the previous state.
     *
     * This function saves the current depth testing, blending, and face culling states. It then sets up OpenGL by disabling depth testing,
     * enabling blending, disabling face culling, setting the depth mask to false, and enabling line smoothing before executing the provided block.
     * After the block completes, it restores the original depth testing, blending, face culling, depth mask, and line smoothing settings.
     *
     * @param block OpenGL operations to perform under the temporary state configuration.
     */
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

    /**
     * Enables depth testing and optionally configures the depth mask during the execution of a code block.
     *
     * When invoked, depth testing is enabled. If [maskWrite] is true, the depth buffer is made writable before the
     * block is executed and set back to non-writable afterward. Finally, depth testing is disabled once the block finishes.
     *
     * @param maskWrite if true, enables writing to the depth buffer for the duration of the block.
     * @param block the code to execute with the modified depth state.
     */
    fun withDepth(maskWrite: Boolean = false, block: () -> Unit) {
        depthTest(true)
        if (maskWrite) glDepthMask(true)
        block()
        if (maskWrite) glDepthMask(false)
        depthTest(false)
    }

    /**
     * Executes the provided block with face culling enabled.
     *
     * This function turns on face culling, executes the given block of code, and then disables face culling,
     * ensuring that the OpenGL state is restored after the block execution.
     *
     * @param block the code to run with face culling enabled.
     */
    fun withFaceCulling(block: () -> Unit) {
        cull(true)
        block()
        cull(false)
    }

    fun withLineWidth(width: Double, block: () -> Unit) {
        glLineWidth(width.toFloat())
        block()
        glLineWidth(1f)
    }

    fun withBlendFunc(
        sfactorRGB: Int, dfactorRGB: Int,
        sfactorAlpha: Int = sfactorRGB, dfactorAlpha: Int = dfactorRGB,
        block: () -> Unit
    ) {
        glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha)
        block()
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
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
