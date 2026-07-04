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
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.pow

class OutlineGlow(private val name: String) {
    private companion object {
        private const val MAX_PASSES = 4
    }

    private val textures = arrayOfNulls<GpuTexture>(MAX_PASSES)
    private val views = arrayOfNulls<GpuTextureView>(MAX_PASSES)
    private val passWidths = IntArray(MAX_PASSES)
    private val passHeights = IntArray(MAX_PASSES)

    private var lastGlowResolution = -1.0f
    private var lastGlowDownsample = -1.0f
    private var lastFramebufferWidth = -1
    private var lastFramebufferHeight = -1
    private var lastPassCount = 0

    fun glow(
        source: GpuTextureView,
        style: OutlineStyle,
        quadBuffer: GpuBuffer,
        silhouetteDepth: GpuTextureView? = null,
        worldDepth: GpuTextureView? = null
    ): GpuTextureView {
        val framebuffer = mc.framebuffer ?: return source
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight
        val passCount = style.glowPasses.coerceIn(1, MAX_PASSES)
        val offset = style.glowOffset

        if (style.glowResolution != lastGlowResolution || style.glowDownsample != lastGlowDownsample || width != lastFramebufferWidth || height != lastFramebufferHeight || passCount != lastPassCount) {
            recreateTextures(width, height, style.glowResolution, style.glowDownsample, passCount)
        }

        renderToTexture(
            views[0] ?: return source,
            source,
            width,
            height,
            offset,
            quadBuffer,
            silhouetteDepth,
            worldDepth
        )

        for (i in 1 until passCount) {
            val target = views[i] ?: continue
            val input = views[i - 1] ?: continue
            renderToTexture(target, input, passWidths[i - 1], passHeights[i - 1], offset, quadBuffer)
        }

        for (i in passCount - 1 downTo 1) {
            val target = views[i - 1] ?: continue
            val input = views[i] ?: continue
            renderToTexture(target, input, passWidths[i], passHeights[i], offset, quadBuffer)
        }

        return views[0] ?: source
    }

    fun cleanup() {
        for (i in textures.indices) {
            views[i]?.close()
            textures[i]?.close()
            views[i] = null
            textures[i] = null
            passWidths[i] = 0
            passHeights[i] = 0
        }
        lastGlowResolution = -1.0f
        lastGlowDownsample = -1.0f
        lastFramebufferWidth = -1
        lastFramebufferHeight = -1
        lastPassCount = 0
    }

    private fun renderToTexture(
        target: GpuTextureView,
        source: GpuTextureView,
        sourceWidth: Int,
        sourceHeight: Int,
        offset: Float,
        quadBuffer: GpuBuffer,
        silhouetteDepth: GpuTextureView? = null,
        worldDepth: GpuTextureView? = null
    ) {
        val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
        val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
        val depthTest = silhouetteDepth != null && worldDepth != null
        val glowUniform = OutlineGlowUniforms.write(sourceWidth, sourceHeight, offset, depthTest)

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Glow - $name" },
                target,
                OptionalInt.of(0),
                null,
                OptionalDouble.empty()
            )?.use { pass ->
                pass.setPipeline(LambdaRenderPipelines.OUTLINE_GLOW)
                pass.bindTexture("Sampler0", source, linearSampler)
                pass.bindTexture("Sampler1", silhouetteDepth ?: source, nearestSampler)
                pass.bindTexture("Sampler2", worldDepth ?: source, nearestSampler)
                pass.setUniform("GlowData", glowUniform)
                pass.setVertexBuffer(0, quadBuffer)

                val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
                val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
                pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
                pass.drawIndexed(0, 0, 6, 1)
            }
    }

    private fun recreateTextures(framebufferWidth: Int, framebufferHeight: Int, resolution: Float, downsample: Float, passCount: Int) {
        lastFramebufferWidth = framebufferWidth
        lastFramebufferHeight = framebufferHeight
        lastGlowResolution = resolution
        lastGlowDownsample = downsample
        lastPassCount = passCount

        val device = RenderSystem.getDevice()
        for (i in 0 until passCount) {
            passWidths[i] = getPassDimension(framebufferWidth, resolution, downsample, i)
            passHeights[i] = getPassDimension(framebufferHeight, resolution, downsample, i)

            views[i]?.close()
            textures[i]?.close()

            val texture = device.createTexture(
                { "Lambda Outline Glow - $name - $i" },
                15,
                TextureFormat.RGBA8,
                passWidths[i],
                passHeights[i],
                1,
                1
            )
            textures[i] = texture
            views[i] = device.createTextureView(texture)
        }
        for (i in passCount until MAX_PASSES) {
            views[i]?.close()
            textures[i]?.close()
            views[i] = null
            textures[i] = null
            passWidths[i] = 0
            passHeights[i] = 0
        }
    }

    private fun getPassDimension(baseDimension: Int, resolution: Float, downsample: Float, level: Int): Int {
        val scale = resolution * downsample.pow(level)
        return kotlin.math.max(1, kotlin.math.round(baseDimension * scale).toInt())
    }
}
