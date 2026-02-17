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
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.util.OptionalDouble
import java.util.OptionalInt

object OutlineRenderer {
    private var silhouetteTexture: GpuTexture? = null
    private var silhouetteView: GpuTextureView? = null
    private var silhouetteDepthTexture: GpuTexture? = null
    private var silhouetteDepthView: GpuTextureView? = null
    private var silhouetteWidth = 0
    private var silhouetteHeight = 0

    private var silhouetteVertexBuffer: GpuBuffer? = null

    private var fullscreenQuadBuffer: GpuBuffer? = null

    fun getGroupView(): GpuTextureView? = silhouetteView

    fun getGroupDepthView(): GpuTextureView? = silhouetteDepthView

    fun beginGroupPass(name: String) {
        if (!ensureSilhouetteFBO()) return
        val colorView = silhouetteView ?: return
        val depthView = silhouetteDepthView ?: return
        
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Begin Group Pass: $name" },
                colorView,
                OptionalInt.of(0),
                depthView,
                OptionalDouble.of(1.0)
            )?.close()
    }

    fun endGroupPass(style: OutlineStyle, depthTest: Boolean = false) {
        val groupView = silhouetteView ?: return
        val framebuffer = mc.framebuffer ?: return

        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val outlineColor = Vector4f(style.color.red / 255f, style.color.green / 255f, style.color.blue / 255f, 1.0f)
        val styleMat = buildStyleMatrix(style)
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            outlineColor,
            Vector3f(1f, if (depthTest) 1f else 0f, 0f),
            styleMat
        )

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda End Group Pass" },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
            )?.use { pass ->
                pass.setPipeline(LambdaRenderPipelines.OUTLINE_SOBEL)
                val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
                pass.bindTexture("Sampler0", groupView, nearestSampler)

                val silDepth = silhouetteDepthView
                if (silDepth != null) pass.bindTexture("Sampler1", silDepth, nearestSampler)

                val worldDepth = framebuffer.depthAttachmentView
                if (worldDepth != null) pass.bindTexture("Sampler2", worldDepth, nearestSampler)

                pass.setUniform("DynamicTransforms", dynamicTransform)
                pass.setVertexBuffer(0, quadBuffer)
                
                val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
                val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
                pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
                pass.drawIndexed(0, 0, 6, 1)
            }
    }
    private fun ensureSilhouetteFBO(): Boolean {
        val framebuffer = mc.framebuffer ?: return false
        val width = framebuffer.textureWidth
        val height = framebuffer.textureHeight

        if (silhouetteTexture == null || silhouetteWidth != width || silhouetteHeight != height) {
            silhouetteView?.close()
            silhouetteTexture?.close()
            silhouetteDepthView?.close()
            silhouetteDepthTexture?.close()

            val gpuDevice = RenderSystem.getDevice()

            silhouetteTexture = gpuDevice.createTexture(
                { "Lambda Outline Silhouette" },
                15,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
            )
            silhouetteView = gpuDevice.createTextureView(silhouetteTexture)

            silhouetteDepthTexture = gpuDevice.createTexture(
                { "Lambda Outline Silhouette Depth" },
                15,
                TextureFormat.DEPTH32,
                width,
                height,
                1,
                1
            )
            silhouetteDepthView = gpuDevice.createTextureView(silhouetteDepthTexture)

            silhouetteWidth = width
            silhouetteHeight = height
        }

        return true
    }

    private fun ensureFullscreenQuad() {
        if (fullscreenQuadBuffer != null) return

        val vertexSize = 20
        val buffer = MemoryUtil.memAlloc(4 * vertexSize)
        try {
            buffer.putFloat(-1f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
            buffer.putFloat(-1f).putFloat(-1f).putFloat(0f).putFloat(0f).putFloat(0f)
            buffer.putFloat(1f).putFloat(-1f).putFloat(0f).putFloat(1f).putFloat(0f)
            buffer.putFloat(1f).putFloat(1f).putFloat(0f).putFloat(1f).putFloat(1f)
            buffer.flip()

            fullscreenQuadBuffer = RenderSystem.getDevice().createBuffer(
                { "Lambda Outline Fullscreen Quad" },
                GpuBuffer.USAGE_VERTEX,
                buffer
            )
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private data class StyleKey(
        val thickness: Float,
        val glowIntensity: Float,
        val glowRadius: Float,
        val fill: Boolean,
        val fillOpacity: Float
    )

    private fun OutlineStyle.toKey() = StyleKey(thickness, glowIntensity, glowRadius, fill, fillOpacity)

    fun renderAllIDPasses() {
        val depthTestedStyles = OutlineManager.getDepthTestedStyles()
        val xrayStyles = OutlineManager.getXrayStyles()

        val depthTestedGroups = depthTestedStyles.entries.groupBy({ it.value.toKey() }, { it.key })
        val xrayGroups = xrayStyles.entries.groupBy({ it.value.toKey() }, { it.key })

        val allStyleKeys = (depthTestedGroups.keys + xrayGroups.keys).distinct()

        for (styleKey in allStyleKeys) {
            OutlineIdBuffer.beginFrame()

            val depthTestedIds = depthTestedGroups[styleKey]
            val xrayIds = xrayGroups[styleKey]

            if (!depthTestedIds.isNullOrEmpty()) {
                OutlineIdPassRenderer.render(depthTestedIds.toSet(), useMcDepth = true)
            }
            if (!xrayIds.isNullOrEmpty()) {
                OutlineIdPassRenderer.render(xrayIds.toSet(), useMcDepth = false)
            }

            if (OutlineIdBuffer.hasData) {
                val representativeId = depthTestedIds?.firstOrNull() ?: xrayIds?.firstOrNull() ?: continue
                val representativeStyle = OutlineManager.getOutlineStyle(representativeId) ?: continue
                applyEdgeDetection(representativeStyle)
            }
        }
    }

    private fun applyEdgeDetection(style: OutlineStyle = OutlineStyle.DEFAULT) {
        val idBufferView = OutlineIdBuffer.getTextureView() ?: return
        applySobel(idBufferView, "Lambda Global Outline Sobel Pass", style)
    }

    private fun buildStyleMatrix(style: OutlineStyle): Matrix4f {
        val mat = Matrix4f()
        mat.m00(style.thickness)
        mat.m01(style.glowIntensity)
        mat.m02(style.glowRadius)
        mat.m03(if (style.fill) style.fillOpacity else 0f)
        return mat
    }

    private fun applySobel(textureView: GpuTextureView, label: String, style: OutlineStyle = OutlineStyle.DEFAULT) {
        val framebuffer = mc.framebuffer ?: return
        
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val styleMat = buildStyleMatrix(style)
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(), Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), styleMat
        )

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { label },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
            )?.use { pass ->
                pass.setPipeline(LambdaRenderPipelines.OUTLINE_SOBEL)
                val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
                pass.bindTexture("Sampler0", textureView, nearestSampler)

                val silDepth = OutlineIdBuffer.getSilhouetteDepthView()
                if (silDepth != null) pass.bindTexture("Sampler1", silDepth, nearestSampler)

                val worldDepth = OutlineIdBuffer.getMcDepthView()
                if (worldDepth != null) pass.bindTexture("Sampler2", worldDepth, nearestSampler)
                pass.setUniform("DynamicTransforms", dynamicTransform)
                pass.setVertexBuffer(0, quadBuffer)
                val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
                val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
                pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
                pass.drawIndexed(0, 0, 6, 1)
            }
    }

    fun cleanup() {
        silhouetteView?.close()
        silhouetteTexture?.close()
        silhouetteDepthView?.close()
        silhouetteDepthTexture?.close()
        silhouetteVertexBuffer?.close()
        fullscreenQuadBuffer?.close()

        silhouetteView = null
        silhouetteTexture = null
        silhouetteDepthView = null
        silhouetteDepthTexture = null
        silhouetteVertexBuffer = null
        fullscreenQuadBuffer = null
        silhouetteWidth = 0
        silhouetteHeight = 0

        OutlineIdBuffer.cleanup()
        OutlineIdPassRenderer.cleanup()
    }
}
