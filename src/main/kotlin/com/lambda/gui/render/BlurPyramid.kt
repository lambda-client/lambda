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

import com.lambda.graphics.mc.LambdaRenderPipelines
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import org.lwjgl.system.MemoryStack
import java.util.*
import kotlin.math.ceil
import kotlin.math.log2

/**
 * A dual Kawase blur.
 *
 * Instead of sampling a wide kernel at full resolution, the image is halved a few times and then
 * doubled back up, each step a handful of bilinear taps. Every level costs a quarter of the one
 * before it, so the whole blur is cheaper than a single full resolution pass and its cost barely
 * grows with the radius: a larger radius just adds a smaller level.
 *
 * The result is left at half the source resolution. A blur has no detail a full resolution copy
 * would show, and skipping the last upsample avoids the one pass that touches every pixel.
 */
internal class BlurPyramid(private val name: String) {
    private var levels = emptyList<Level>()
    private var width = 0
    private var height = 0

    private val spreadUniform: GpuBuffer = RenderSystem.getDevice().createBuffer(
        { "$name spread" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, SPREAD_UNIFORM_SIZE
    )
    private var spread = Float.NaN

    /** Blurs [source], which is [width] by [height], by about [radius] pixels */
    fun blur(source: GpuTextureView, width: Int, height: Int, radius: Float): GpuTextureView {
        resize(width, height)
        // Each level doubles the reach of the one above; the spread covers what is left over
        val depth = (ceil(log2(radius.coerceAtLeast(1f))).toInt() - 1).coerceIn(1, levels.size)
        writeSpread((radius / (1 shl depth)).coerceIn(MIN_SPREAD, MAX_SPREAD))

        var input = source
        for (index in 0 until depth) {
            pass(LambdaRenderPipelines.GUI_BLUR_DOWN, input, levels[index].view)
            input = levels[index].view
        }
        for (index in depth - 2 downTo 0) {
            pass(LambdaRenderPipelines.GUI_BLUR_UP, input, levels[index].view)
            input = levels[index].view
        }
        return input
    }

    private fun pass(pipeline: RenderPipeline, input: GpuTextureView, output: GpuTextureView) {
        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass({ name }, output, OptionalInt.empty())
            .use { pass ->
                pass.setPipeline(pipeline)
                pass.bindTexture("InSampler", input, RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
                pass.setUniform("BlurSpread", spreadUniform)
                pass.draw(0, 3)
            }
    }

    private fun writeSpread(value: Float) {
        if (value == spread) return
        spread = value
        MemoryStack.stackPush().use { stack ->
            val data = Std140Builder.onStack(stack, SPREAD_UNIFORM_SIZE.toInt()).putFloat(value).get()
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(spreadUniform.slice(), data)
        }
    }

    private fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return
        levels.forEach { it.close() }
        levels = (1..MAX_DEPTH).map { Level("$name level $it", (width shr it).coerceAtLeast(1), (height shr it).coerceAtLeast(1)) }
        this.width = width
        this.height = height
    }

    private class Level(label: String, width: Int, height: Int) : AutoCloseable {
        private val texture: GpuTexture = RenderSystem.getDevice().createTexture(
            { label },
            GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
            TextureFormat.RGBA8,
            width, height, 1, 1
        )
        val view: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)

        override fun close() {
            view.close()
            texture.close()
        }
    }

    private companion object {
        /** Deep enough for a radius of over a hundred pixels */
        const val MAX_DEPTH = 6

        /** Past two texels the taps stop overlapping and the blur breaks up into copies */
        const val MIN_SPREAD = 0.5f
        const val MAX_SPREAD = 2f

        /** A lone float, padded to the 16 bytes a std140 block rounds up to */
        const val SPREAD_UNIFORM_SIZE = 16L
    }
}
