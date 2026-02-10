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
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.render.VertexFormats
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt

/**
 * Renders entity IDs to the ID buffer for outline edge detection.
 * 
 * Uses POSITION_TEXTURE_COLOR format (24 bytes):
 * - Position (vec3): Camera-relative world position
 * - UV (vec2): Texture coordinates for alpha testing
 * - Color (vec4): 
 *    - RGB: ESP color
 *    - A: 254 = perform alpha test (atlas item), 255 = solid silhouette
 */
object OutlineIdPassRenderer {
    
    // Vertex buffers for ID pass rendering - separate for depth-tested vs xray
    private val vertexSize = 28 // POSITION_TEXTURE_COLOR with 4D pos: 4f + 2f + 1i
    
    // Batch data for rendering with different textures
    private class DrawBatch(val textureView: com.mojang.blaze3d.textures.GpuTextureView?, val vertexCount: Int, val vertexOffset: Int)
    
    private var depthTestedVertexBuffer: GpuBuffer? = null
    private val depthTestedBatches = mutableListOf<DrawBatch>()
    
    private var xrayVertexBuffer: GpuBuffer? = null
    private val xrayBatches = mutableListOf<DrawBatch>()
    
    /**
     * Render entity IDs to the ID buffer.
     * Call after MC's entity render pass when VertexCapture has geometry.
     */
    fun render(entityIds: Set<Int>, useMcDepth: Boolean) {
        if (!OutlineIdBuffer.ensureBuffer()) return
        
        val colorView = OutlineIdBuffer.getTextureView() ?: return
        
        val allOutlines = OutlineManager.getEntityOutlines()
        val outlines = allOutlines.filter { (id, _) -> id in entityIds }
        if (outlines.isEmpty()) return
        
        // Render entities using the specified depth mode
        buildVertexBuffer(outlines, isDepthTested = useMcDepth)
        val vbo = if (useMcDepth) depthTestedVertexBuffer else xrayVertexBuffer
        val batches = if (useMcDepth) depthTestedBatches else xrayBatches
        
        if (vbo != null && batches.isNotEmpty()) {
            renderPass(colorView, vbo, batches, useMcDepth = useMcDepth)
        }
    }
    
    /**
     * Build the vertex buffer for the ID pass.
     * Converts captured quads into triangles for the render pass.
     */
    fun buildVertexBuffer(entities: Map<Int, OutlineStyle>, isDepthTested: Boolean) {
        val targetBatches = if (isDepthTested) depthTestedBatches else xrayBatches
        targetBatches.clear()

        // 1. Group all geometries from all entities by texture
        val textureGroups = mutableMapOf<com.mojang.blaze3d.textures.GpuTextureView?, MutableList<Pair<CapturedGeometry, OutlineStyle>>>()
        var totalTriVertices = 0
        
        for ((entityId, style) in entities) {
            val geometries = VertexCapture.getEntityGeometries(entityId)
            for (geometry in geometries) {
                if (!geometry.isEmpty()) {
                    textureGroups.getOrPut(geometry.textureView) { mutableListOf() }.add(geometry to style)
                    totalTriVertices += (geometry.getVertices().size / 4) * 6
                }
            }
        }

        if (totalTriVertices == 0) return

        // 2. Build one large buffer and record batches
        val buffer = MemoryUtil.memAlloc(totalTriVertices * vertexSize).order(ByteOrder.nativeOrder())
        try {
            var currentVertexOffset = 0
            
            for ((textureView, group) in textureGroups) {
                val batchStartVertex = currentVertexOffset
                var batchVertexCount = 0
                
                for ((geometry, style) in group) {
                    val capturedVerts = geometry.getVertices()
                    val color = style.color
                    // Pack as (A << 24 | B << 16 | G << 8 | R)
                    val packedColor = (255 shl 24) or (color.blue and 0xFF shl 16) or (color.green and 0xFF shl 8) or (color.red and 0xFF)

                    val quadCount = capturedVerts.size / 4
                    for (q in 0 until quadCount) {
                        val baseIdx = q * 4
                        val v0 = capturedVerts[baseIdx]
                        val v1 = capturedVerts[baseIdx + 1]
                        val v2 = capturedVerts[baseIdx + 2]
                        val v3 = capturedVerts[baseIdx + 3]
                        
                        // Tri 1
                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putFloat(v0.w).putFloat(v0.u).putFloat(v0.v).putInt(packedColor)
                        buffer.putFloat(v1.x).putFloat(v1.y).putFloat(v1.z).putFloat(v1.w).putFloat(v1.u).putFloat(v1.v).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putFloat(v2.w).putFloat(v2.u).putFloat(v2.v).putInt(packedColor)
                        
                        // Tri 2
                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putFloat(v0.w).putFloat(v0.u).putFloat(v0.v).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putFloat(v2.w).putFloat(v2.u).putFloat(v2.v).putInt(packedColor)
                        buffer.putFloat(v3.x).putFloat(v3.y).putFloat(v3.z).putFloat(v3.w).putFloat(v3.u).putFloat(v3.v).putInt(packedColor)
                        
                        currentVertexOffset += 6
                        batchVertexCount += 6
                    }
                }
                
                if (batchVertexCount > 0) {
                    targetBatches.add(DrawBatch(textureView, batchVertexCount, batchStartVertex))
                }
            }

            buffer.flip()
            if (isDepthTested) {
                depthTestedVertexBuffer?.close()
                depthTestedVertexBuffer = RenderSystem.getDevice().createBuffer({ "Lambda Outline ID (Depth)" }, GpuBuffer.USAGE_VERTEX, buffer)
            } else {
                xrayVertexBuffer?.close()
                xrayVertexBuffer = RenderSystem.getDevice().createBuffer({ "Lambda Outline ID (Xray)" }, GpuBuffer.USAGE_VERTEX, buffer)
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /**
     * Execute a render pass with the specified depth buffer.
     */
    private fun renderPass(
        colorView: com.mojang.blaze3d.textures.GpuTextureView,
        vertexBuffer: GpuBuffer,
        batches: List<DrawBatch>,
        useMcDepth: Boolean
    ) {
        val depthView = if (useMcDepth) OutlineIdBuffer.getMcDepthView() else OutlineIdBuffer.getDepthView()
        if (depthView == null || batches.isEmpty()) return
        
        // Camera alignment:
        // Vertices are already in Clip Space (including projection, bobbing, and rotation).
        // AlignMatrix and ProjectionMatrix are both Identity.
        val alignMatrix = org.joml.Matrix4f()
        val identityProj = org.joml.Matrix4f()
            
        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            alignMatrix, 
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            identityProj
        )
        
        // Fallback texture for entities without one (Block Atlas)
        val blockAtlas = mc.textureManager.getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
        val whiteTexture = blockAtlas.glTextureView
        val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
        val pipeline = if (useMcDepth) LambdaRenderPipelines.OUTLINE_ID else LambdaRenderPipelines.OUTLINE_ID_THROUGH
        val label = if (useMcDepth) "Lambda Outline ID Pass (Depth Tested)" else "Lambda Outline ID Pass (Xray)"
        
        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { label },
                colorView,
                OptionalInt.empty(), // Always preserve previous IDs in the color buffer
                depthView,
                OptionalDouble.empty() // DO NOT clear depth here - cleared once at start of frame
            ) ?: return
        
        try {
            renderPass.setPipeline(pipeline)
            renderPass.setUniform("DynamicTransforms", dynamicTransform)
            renderPass.setVertexBuffer(0, vertexBuffer)
            
            for (batch in batches) {
                val textureView = batch.textureView ?: whiteTexture
                renderPass.bindTexture("Sampler0", textureView, nearestSampler)
                renderPass.draw(batch.vertexOffset, batch.vertexCount)
            }
        } finally {
            renderPass.close()
        }
    }
    
    /**
     * Release all GPU resources.
     */
    fun cleanup() {
        depthTestedVertexBuffer?.close()
        depthTestedVertexBuffer = null
        depthTestedBatches.clear()
        
        xrayVertexBuffer?.close()
        xrayVertexBuffer = null
        xrayBatches.clear()
    }
}
