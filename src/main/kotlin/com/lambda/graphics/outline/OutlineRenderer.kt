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
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.renderer.upload
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.VertexFormats
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
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
    private var silhouetteVertexCount = 0

    private var fullscreenQuadBuffer: GpuBuffer? = null

    fun getGroupView(): GpuTextureView? = silhouetteView

    fun getGroupDepthView(): GpuTextureView? = silhouetteDepthView

    fun beginGroupPass(name: String, depthView: GpuTextureView) {
        if (!ensureSilhouetteFBO()) return
        val colorView = silhouetteView ?: return
        
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

    fun endGroupPass(style: OutlineStyle) {
        val groupView = silhouetteView ?: return
        val framebuffer = mc.framebuffer ?: return

        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val outlineColor = Vector4f(style.color.red / 255f, style.color.green / 255f, style.color.blue / 255f, 1.0f)
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            outlineColor,
            Vector3f(1f, 0f, 0f),
            Matrix4f()
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

    fun clearSilhouette() {
        if (!ensureSilhouetteFBO()) return
        val colorView = silhouetteView ?: return
        val depthView = silhouetteDepthView ?: return

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Clear Outline Silhouette" },
                colorView,
                OptionalInt.of(0x00000000),
                depthView,
                OptionalDouble.of(1.0)
            )?.close()
    }

    fun beginFrame() {
        clearSilhouette()
    }

    fun renderSilhouettes() {
        if (!ensureSilhouetteFBO()) return
        
        val colorView = silhouetteView ?: return
        val depthView = silhouetteDepthView ?: return

        val outlines = OutlineManager.getEntityOutlines()
        if (outlines.isEmpty()) return

        val vertexSize = VertexFormats.POSITION_COLOR.vertexSize
        var totalVertices = 0

        for ((entityId, _) in outlines) {
            val geometries = VertexCapture.getEntityGeometries(entityId)
            for (geometry in geometries) {
                val capturedVerts = geometry.getVertices()
                totalVertices += (capturedVerts.size / 4) * 6
            }
        }
        
        if (totalVertices == 0) return

        val buffer = MemoryUtil.memAlloc(totalVertices * vertexSize).order(ByteOrder.nativeOrder())
        try {
            for ((entityId, pair) in outlines) {
                val style = pair.first
                val geometries = VertexCapture.getEntityGeometries(entityId)
                for (geometry in geometries) {
                    val capturedVerts = geometry.getVertices()
                    if (capturedVerts.isEmpty()) continue

                    val color = style.color
                    val fillAlpha = if (style.fill) {
                        (style.fillOpacity * 255f).toInt().coerceIn(2, 255)
                    } else {
                        1
                    }
                    val packedColor = (fillAlpha shl 24) or
                                     ((color.blue and 0xFF) shl 16) or
                                     ((color.green and 0xFF) shl 8) or
                                     (color.red and 0xFF)

                    val quadCount = capturedVerts.size / 4
                    for (q in 0 until quadCount) {
                        val baseIdx = q * 4
                        val v0 = capturedVerts[baseIdx]
                        val v1 = capturedVerts[baseIdx + 1]
                        val v2 = capturedVerts[baseIdx + 2]
                        val v3 = capturedVerts[baseIdx + 3]

                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putInt(packedColor)
                        buffer.putFloat(v1.x).putFloat(v1.y).putFloat(v1.z).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putInt(packedColor)
                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putInt(packedColor)
                        buffer.putFloat(v3.x).putFloat(v3.y).putFloat(v3.z).putInt(packedColor)
                    }
                }
            }
            buffer.flip()
            

            silhouetteVertexBuffer?.close()
            val vbo = RenderSystem.getDevice().createBuffer({ "Lambda Outline Silhouette Vertices" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
            vbo.upload(buffer)
            silhouetteVertexBuffer = vbo
            silhouetteVertexCount = totalVertices
        } finally {
            MemoryUtil.memFree(buffer)
        }

        val vertexBuffer = silhouetteVertexBuffer ?: return
        
        val origProj = RenderMain.worldProjectionMatrix
        
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            RenderMain.modelViewMatrix,
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            origProj
        )

        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Silhouette Pass" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ) ?: return

	    renderPass.use { renderPass ->
		    renderPass.setPipeline(LambdaRenderPipelines.OUTLINE_SILHOUETTE)
		    renderPass.setUniform("DynamicTransforms", dynamicTransform)
		    renderPass.setVertexBuffer(0, vertexBuffer)
		    renderPass.draw(0, silhouetteVertexCount)
	    }
    }

    fun renderAllIDPasses() {
        val depthTested = OutlineManager.getDepthTestedEntityIds()
        val xray = OutlineManager.getXrayEntityIds()

        if (depthTested.isNotEmpty()) {
            OutlineIdPassRenderer.render(depthTested, useMcDepth = true)
        }
        if (xray.isNotEmpty()) {
            OutlineIdPassRenderer.render(xray, useMcDepth = false)
        }
    }

    fun renderIDPass(entityIds: Set<Int>, depthTest: Boolean) {
        if (entityIds.isEmpty()) return
        OutlineIdPassRenderer.render(entityIds, useMcDepth = depthTest)
    }

    fun renderEdges() {
        if (OutlineIdBuffer.hasData || OutlineLayerBuffer.hasData) {
            applyEdgeDetection()
        }
    }

    private fun applyEdgeDetection() {
        val idBufferView = OutlineIdBuffer.getTextureView() ?: return
        applySobel(idBufferView, "Lambda Global Outline Sobel Pass")
    }

    private fun applySobel(textureView: GpuTextureView, label: String) {
        val framebuffer = mc.framebuffer ?: return
        
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(), Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f()
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
    
    private fun applyEdgeDetectionNative(outlineFb: net.minecraft.client.gl.SimpleFramebuffer) {
        val framebuffer = mc.framebuffer ?: return
        
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            Matrix4f()
        )

        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Sobel Pass" },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
            ) ?: return

        try {
            renderPass.setPipeline(LambdaRenderPipelines.OUTLINE_SOBEL)

            val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
            renderPass.bindTexture("Sampler0", outlineFb.colorAttachmentView, nearestSampler)

            renderPass.setUniform("DynamicTransforms", dynamicTransform)

            renderPass.setVertexBuffer(0, quadBuffer)

            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
            val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
            renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
            renderPass.drawIndexed(0, 0, 6, 1)
        } finally {
            renderPass.close()
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

        OutlineLayerBuffer.cleanup()

        OutlineIdBuffer.cleanup()
        OutlineIdPassRenderer.cleanup()
    }
}
