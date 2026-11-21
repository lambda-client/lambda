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

package com.lambda.gui.render

import com.lambda.Lambda.mc
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import org.lwjgl.system.MemoryStack
import java.awt.Color
import java.util.*

internal object GuiCompositor {
    data class Effects(
        val frost: Float = 0f,
        val glow: Float = 0f,
        val glowIntensity: Float = 0f,
        val glowColorA: Color = Color.WHITE,
        val glowColorB: Color = Color.WHITE,
    ) {
        companion object {
            val NONE = Effects()
        }
    }

    private val backdrop by lazy { BlurPyramid("Lambda GUI backdrop") }
    private val halo by lazy { BlurPyramid("Lambda GUI glow") }
    private var haloView: GpuTextureView? = null
    private var haloRadius = 0f

    private val uniformSize =
        Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putFloat()
            .putFloat()
            .putFloat()
            .align(16)
            .get()
    private val uniforms: GpuBuffer by lazy {
        RenderSystem.getDevice().createBuffer(
            { "Lambda GUI effects" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            uniformSize.toLong()
        )
    }

    fun composite(
        gui: GpuTextureView,
        width: Int,
        height: Int,
        guiChanged: Boolean,
        effects: Effects
    ) {
        val screen = mc.framebuffer.colorAttachmentView ?: return
        val linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
        val nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)

        if (effects.frost <= 0f && effects.glow <= 0f) {
            haloView = null
            RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass({ "Lambda GUI" }, screen, OptionalInt.empty())
                .use { pass ->
                    pass.setPipeline(LambdaRenderPipelines.GUI_BLIT)
                    pass.bindTexture("InSampler", gui, nearest)
                    pass.draw(0, 3)
                }
            return
        }

        val backdropView =
            if (effects.frost > 0f) backdrop.blur(screen, width, height, effects.frost)
            else gui
        if (effects.glow <= 0f) haloView = null
        else if (guiChanged || haloView == null || haloRadius != effects.glow) {
            haloView = halo.blur(gui, width, height, effects.glow)
            haloRadius = effects.glow
        }

        writeUniforms(effects)
        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass({ "Lambda GUI" }, screen, OptionalInt.empty())
            .use { pass ->
                pass.setPipeline(LambdaRenderPipelines.GUI_COMPOSITE)
                pass.bindTexture("GuiSampler", gui, nearest)
                pass.bindTexture("BackdropSampler", backdropView, linear)
                pass.bindTexture("HaloSampler", haloView ?: gui, linear)
                pass.setUniform("GuiEffects", uniforms)
                pass.draw(0, 3)
            }
    }

    private fun writeUniforms(effects: Effects) {
        MemoryStack.stackPush().use { stack ->
            val data =
                Std140Builder.onStack(stack, uniformSize)
                    .putColor(effects.glowColorA)
                    .putColor(effects.glowColorB)
                    .putFloat(if (effects.glow > 0f) effects.glowIntensity else 0f)
                    .putFloat(if (effects.frost > 0f) 1f else 0f)
                    .putFloat((System.currentTimeMillis() % GLOW_CYCLE_MILLIS) / 1000f)
                    .get()
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                uniforms.slice(),
                data
            )
        }
    }

    private fun Std140Builder.putColor(color: Color) =
        putVec4(
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )

    private const val GLOW_CYCLE_MILLIS = 3_141_592L
}
