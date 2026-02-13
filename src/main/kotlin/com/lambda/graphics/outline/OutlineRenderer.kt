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

/**
 * Outline rendering system using FBO-based silhouette + edge detection.
 *
 * Pipeline:
 * 1. Render captured entity geometry to silhouette FBO (flat color)
 * 2. Apply Sobel edge detection shader to find silhouette edges
 * 3. Composite result onto main framebuffer
 *
 * Usage:
 * ```kotlin
 * // After MC entity render pass (vertices captured via EntityRenderManagerMixin)
 * OutlineRenderer.beginFrame()
 * OutlineRenderer.renderSilhouettes()  // Draw captured geometry to FBO
 * OutlineRenderer.applyEdgeDetection() // Find edges and draw to screen
 * ```
 */
object OutlineRenderer {

    // Silhouette FBO - stores flat-colored entity silhouettes for edge detection
    private var silhouetteTexture: GpuTexture? = null
    private var silhouetteView: GpuTextureView? = null
    private var silhouetteDepthTexture: GpuTexture? = null
    private var silhouetteDepthView: GpuTextureView? = null
    private var silhouetteWidth = 0
    private var silhouetteHeight = 0

    // Vertex buffer for silhouette rendering (rebuilt each frame)
    private var silhouetteVertexBuffer: GpuBuffer? = null
    private var silhouetteVertexCount = 0

    // Fullscreen quad buffer for post-processing
    private var fullscreenQuadBuffer: GpuBuffer? = null

    /**
     * Get the current group texture view for rendering.
     */
    fun getGroupView(): GpuTextureView? = silhouetteView

    /**
     * Get the current group depth view for rendering.
     */
    fun getGroupDepthView(): GpuTextureView? = silhouetteDepthView

    /**
     * Prepare the group FBO for a new isolated render pass.
     * Clears the color to transparent and depth to 1.0.
     */
    fun beginGroupPass(name: String, depthView: GpuTextureView) {
        if (!ensureSilhouetteFBO()) return
        val colorView = silhouetteView ?: return
        
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Begin Group Pass: $name" },
                colorView,
                java.util.OptionalInt.of(0), // Clear to transparent
                depthView,
                java.util.OptionalDouble.of(1.0) // Clear depth
            )?.close()
    }

    /**
     * Finish the group pass by applying the outline and merging back to the main framebuffer.
     */
    fun endGroupPass(style: OutlineStyle) {
        val groupView = silhouetteView ?: return
        val framebuffer = mc.framebuffer ?: return

        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val outlineColor = Vector4f(style.color.red / 255f, style.color.green / 255f, style.color.blue / 255f, 1.0f)
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            outlineColor,
            Vector3f(1f, 0f, 0f), // x=1.0 for IsOverride=true
            Matrix4f()
        )

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda End Group Pass" },
                framebuffer.colorAttachmentView,
                java.util.OptionalInt.empty(),
                null,
                java.util.OptionalDouble.empty()
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

        // Recreate if size changed or doesn't exist
        if (silhouetteTexture == null || silhouetteWidth != width || silhouetteHeight != height) {
            // Clean up old resources
            silhouetteView?.close()
            silhouetteTexture?.close()
            silhouetteDepthView?.close()
            silhouetteDepthTexture?.close()

            val gpuDevice = RenderSystem.getDevice()

            // Create RGBA color texture for silhouettes
            silhouetteTexture = gpuDevice.createTexture(
                { "Lambda Outline Silhouette" },
                15, // Usage flags: TRANSFER_SRC | TRANSFER_DST | TEXTURE | RENDER_ATTACHMENT
                TextureFormat.RGBA8,
                width,
                height,
                1, // Layers
                1  // Mip levels
            )
            silhouetteView = gpuDevice.createTextureView(silhouetteTexture)

            // Create depth texture for silhouette pass
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

        // Create fullscreen quad: 4 vertices (Position + UV)
        // Each vertex: x, y, z (float), u, v (float) = 5 floats = 20 bytes
        val vertexSize = 20
        val buffer = MemoryUtil.memAlloc(4 * vertexSize)
        try {
            // Vertex format: POSITION_TEXTURE (vec3 pos, vec2 uv)
            // Order: TL -> BL -> BR -> TR to get CCW winding for MC's quad index buffer
            // MC creates triangles (0,1,2) and (0,2,3), so:
            //   Triangle 1: TL -> BL -> BR = CCW (front face)
            //   Triangle 2: TL -> BR -> TR = CCW (front face)
            // Top-left
            buffer.putFloat(-1f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
            // Bottom-left
            buffer.putFloat(-1f).putFloat(-1f).putFloat(0f).putFloat(0f).putFloat(0f)
            // Bottom-right
            buffer.putFloat(1f).putFloat(-1f).putFloat(0f).putFloat(1f).putFloat(0f)
            // Top-right
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

    /**
     * Clear the silhouette FBO.
     */
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

    /**
     * Begin a new outline rendering frame.
     */
    fun beginFrame() {
        clearSilhouette()
    }


    /**
     * Render captured entity geometry to the silhouette FBO.
     * Draws each entity's geometry as flat colored triangles.
     */
    fun renderSilhouettes() {
        if (!ensureSilhouetteFBO()) return
        
        val colorView = silhouetteView ?: return
        val depthView = silhouetteDepthView ?: return
        
        // Get all captured entity geometry
        val outlines = OutlineManager.getEntityOutlines()
        if (outlines.isEmpty()) return

        // Build vertex data from captured geometry
        // Format: POSITION_COLOR (x, y, z, r, g, b, a) - 7 floats per vertex = 28 bytes
        val vertexSize = VertexFormats.POSITION_COLOR.vertexSize
        var totalVertices = 0
        
        // Count total vertices needed
        for ((entityId, _) in outlines) {
            val geometries = VertexCapture.getEntityGeometries(entityId)
            for (geometry in geometries) {
                val capturedVerts = geometry.getVertices()
                // Each quad (4) becomes 2 triangles (6)
                totalVertices += (capturedVerts.size / 4) * 6
            }
        }
        
        if (totalVertices == 0) return

        // Allocate buffer
        val buffer = MemoryUtil.memAlloc(totalVertices * vertexSize).order(ByteOrder.nativeOrder())
        try {
            // Fill buffer with triangle data
            for ((entityId, pair) in outlines) {
                val style = pair.first
                val geometries = VertexCapture.getEntityGeometries(entityId)
                for (geometry in geometries) {
                    val capturedVerts = geometry.getVertices()
                    if (capturedVerts.isEmpty()) continue

                    // Convert to ABGR packed int (MC format)
                    val color = style.color
                    // Alpha packing:
                    // 0 = No entity
                    // 1 = Entity present, NO FILL (outline only)
                    // 2-255 = Entity present, FILL with this alpha
                    val fillAlpha = if (style.fill) {
                        (style.fillOpacity * 255f).toInt().coerceIn(2, 255)
                    } else {
                        1
                    }
                    val packedColor = (fillAlpha shl 24) or
                                     ((color.blue and 0xFF) shl 16) or
                                     ((color.green and 0xFF) shl 8) or
                                     (color.red and 0xFF)

                    // Process quads (4 vertices each) as 2 triangles (6 vertices)
                    val quadCount = capturedVerts.size / 4
                    for (q in 0 until quadCount) {
                        val baseIdx = q * 4
                        val v0 = capturedVerts[baseIdx]
                        val v1 = capturedVerts[baseIdx + 1]
                        val v2 = capturedVerts[baseIdx + 2]
                        val v3 = capturedVerts[baseIdx + 3]

                        // Triangle 1: v0, v1, v2
                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putInt(packedColor)
                        buffer.putFloat(v1.x).putFloat(v1.y).putFloat(v1.z).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putInt(packedColor)
        // Triangle 2: v0, v2, v3
                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putInt(packedColor)
                        buffer.putFloat(v3.x).putFloat(v3.y).putFloat(v3.z).putInt(packedColor)
                    }
                }
            }
            buffer.flip()
            
            // Create GPU buffer
            silhouetteVertexBuffer?.close()
            val vbo = RenderSystem.getDevice().createBuffer({ "Lambda Outline Silhouette Vertices" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
            vbo.upload(buffer)
            silhouetteVertexBuffer = vbo
            silhouetteVertexCount = totalVertices
        } finally {
            MemoryUtil.memFree(buffer)
        }

        // Render to silhouette FBO
        val vertexBuffer = silhouetteVertexBuffer ?: return
        
        // Create a custom projection with very close near-plane to prevent clipping
        // Extract FOV from existing projection matrix and rebuild with near=0.001
        val origProj = RenderMain.worldProjectionMatrix
        // For a perspective projection: m11 = 1/(aspect*tan(fov/2)), m00 = 1/tan(fov/2)
        // We can build a new projection keeping the same FOV/aspect but with closer near
        val projMat = Matrix4f()
        projMat.set(origProj)
        // Modify near plane by adjusting m22 and m32 (z transformation coefficients)
        // For standard perspective: m22 = -(far+near)/(far-near), m32 = -2*far*near/(far-near)
        // For very near objects we want near ~= 0.001, keeping far same
        // Simplest approach: just set m22 and m32 to allow closer z values
        // Actually, let's try a simpler hack: scale the Z after model-view so nothing clips
        // For now, use identity near-plane fix in shader instead
        
        // Create dynamic transform BEFORE starting render pass
        // Vertices are in camera-relative world space, need camera rotation applied
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            RenderMain.modelViewMatrix, // Apply camera rotation
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            origProj // Use original projection for now
        )

        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Silhouette Pass" },
                colorView,
                OptionalInt.empty(), // Don't clear (already cleared in beginFrame)
                depthView,
                OptionalDouble.empty()
            ) ?: return

        try {
            // Set up pipeline and uniforms
            renderPass.setPipeline(LambdaRenderPipelines.OUTLINE_SILHOUETTE)
            renderPass.setUniform("DynamicTransforms", dynamicTransform)
            
            // Note: Don't call bindDefaultUniforms - it may override our DynamicTransforms
            
            // Bind vertex buffer and draw triangles
            renderPass.setVertexBuffer(0, vertexBuffer)
            renderPass.draw(0, silhouetteVertexCount)
        } finally {
            renderPass.close()
        }
    }


    /**
     * Render entity IDs to the ID buffer for all registered outlines.
     * Consolidity into a single pass per frame to avoid redundant re-renders.
     */
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

    /**
     * Render entity IDs to the ID buffer for a specific set of entities.
     * @deprecated Use [renderAllIDPasses] for better performance.
     */
    fun renderIDPass(entityIds: Set<Int>, depthTest: Boolean) {
        if (entityIds.isEmpty()) return
        OutlineIdPassRenderer.render(entityIds, useMcDepth = depthTest)
    }

    /**
     * Final step of outline rendering for entities.
     * Apply edge detection to the shared ID buffer and composite outlines.
     */
    fun renderEdges() {
        if (OutlineIdBuffer.hasData || OutlineLayerBuffer.hasData) {
            applyEdgeDetection()
        }
    }


    /**
     * Apply Sobel edge detection and composite results.
     */
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
                
                // Bind silhouettes depth for occlusion logic
                val silDepth = OutlineIdBuffer.getSilhouetteDepthView()
                if (silDepth != null) pass.bindTexture("Sampler1", silDepth, nearestSampler)
                
                // Bind world depth for occlusion logic
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
    
    /**
     * Apply edge detection using the native outline framebuffer.
     */
    private fun applyEdgeDetectionNative(outlineFb: net.minecraft.client.gl.SimpleFramebuffer) {
        val framebuffer = mc.framebuffer ?: return
        
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        // Create dynamic transform (identity for fullscreen quad) BEFORE render pass
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f), // x=0.0 for IsOverride=false
            Matrix4f()
        )

        // Create render pass targeting main framebuffer
        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Sobel Pass" },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null, // No depth
                OptionalDouble.empty()
            ) ?: return

        try {
            renderPass.setPipeline(LambdaRenderPipelines.OUTLINE_SOBEL)
            
            // Bind native outline framebuffer texture
            val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
            renderPass.bindTexture("Sampler0", outlineFb.colorAttachmentView, nearestSampler)
            
            // Set uniforms
            renderPass.setUniform("DynamicTransforms", dynamicTransform)
            
            // Draw fullscreen quad
            renderPass.setVertexBuffer(0, quadBuffer)
            
            // Use sequential index buffer for quads
            val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
            val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
            renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
            renderPass.drawIndexed(0, 0, 6, 1)
        } finally {
            renderPass.close()
        }
    }


    /**
     * Release all GPU resources.
     */
    fun cleanup() {
        // Clean up silhouette resources
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
        
        // Clean up outline layer buffer
        OutlineLayerBuffer.cleanup()
        
        // Clean up new ID buffer resources
        OutlineIdBuffer.cleanup()
        OutlineIdPassRenderer.cleanup()
    }
}
