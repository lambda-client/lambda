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
import net.minecraft.client.render.VertexFormats
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt

object OutlineIdPassRenderer {

    private val vertexSize = 28 // POSITION_TEXTURE_COLOR with 4D pos: 4f + 2f + 1i

    private class DrawBatch(val textureView: com.mojang.blaze3d.textures.GpuTextureView?, val vertexCount: Int, val vertexOffset: Int)
    
    private var depthTestedVertexBuffer: GpuBuffer? = null
    private val depthTestedBatches = mutableListOf<DrawBatch>()
    
    private var xrayVertexBuffer: GpuBuffer? = null
    private val xrayBatches = mutableListOf<DrawBatch>()
    
    fun render(entityIds: Set<Int>, useMcDepth: Boolean) {
        if (!OutlineIdBuffer.ensureBuffer()) return
        
        val colorView = OutlineIdBuffer.getTextureView() ?: return
        
        val outlines = if (useMcDepth) OutlineManager.getDepthTestedStyles() else OutlineManager.getXrayStyles()
        val filteredOutlines = outlines.filter { (id, _) -> id in entityIds }
        
        if (filteredOutlines.isNotEmpty()) {
            buildVertexBuffer(filteredOutlines, isDepthTested = useMcDepth)
        } else {
            if (useMcDepth) depthTestedBatches.clear() else xrayBatches.clear()
        }
        
        val vbo = if (useMcDepth) depthTestedVertexBuffer else xrayVertexBuffer
        val batches = if (useMcDepth) depthTestedBatches else xrayBatches
        
        if (vbo != null && batches.isNotEmpty()) {
            OutlineIdBuffer.markHasData()
            renderPass(colorView, vbo, batches)
        }
    }

    fun renderCustom(customGeometries: Map<Int, List<CapturedGeometry>>, useMcDepth: Boolean) {
        if (customGeometries.isEmpty() || !OutlineIdBuffer.ensureBuffer()) return
        
        val colorView = OutlineIdBuffer.getTextureView() ?: return
        
        buildCustomVertexBuffer(customGeometries, isDepthTested = useMcDepth)
        val vbo = if (useMcDepth) depthTestedVertexBuffer else xrayVertexBuffer
        val batches = if (useMcDepth) depthTestedBatches else xrayBatches
        
        if (vbo != null && batches.isNotEmpty()) {
            renderPass(colorView, vbo, batches)
        }
    }
    
    fun buildVertexBuffer(entities: Map<Int, OutlineStyle>, isDepthTested: Boolean) {
        val geometries = entities.mapValues { (id, _) -> VertexCapture.getEntityGeometries(id) }
        buildVertexBufferInternal(geometries, styles = entities, isDepthTested = isDepthTested, transform = null)
    }

    /**
     * Build the vertex buffer for the ID pass using custom geometry.
     */
    private fun buildCustomVertexBuffer(customGeometries: Map<Int, List<CapturedGeometry>>, isDepthTested: Boolean) {
        val styles = customGeometries.keys.mapNotNull { id -> 
            OutlineManager.getOutlineStyle(id)?.let { id to it }
        }.toMap()

        val transform = Matrix4f(RenderMain.worldProjectionMatrix).mul(RenderMain.cameraRotationMatrix)
        buildVertexBufferInternal(customGeometries, styles, isDepthTested, transform)
    }

    private fun buildVertexBufferInternal(
        allGeometries: Map<Int, List<CapturedGeometry>>,
        styles: Map<Int, OutlineStyle>,
        isDepthTested: Boolean,
        transform: Matrix4f? = null
    ) {
        val targetBatches = if (isDepthTested) depthTestedBatches else xrayBatches
        targetBatches.clear()

        val textureGroups = mutableMapOf<com.mojang.blaze3d.textures.GpuTextureView?, MutableList<Pair<CapturedGeometry, OutlineStyle>>>()
        var totalTriVertices = 0
        
        for ((id, geometries) in allGeometries) {
            val style = styles[id] ?: continue
            for (geometry in geometries) {
                if (!geometry.isEmpty()) {
                    textureGroups.getOrPut(geometry.textureView) { mutableListOf() }.add(geometry to style)
                    totalTriVertices += (geometry.getVertices().size / 4) * 6
                }
            }
        }

        if (totalTriVertices == 0) return

        val buffer = MemoryUtil.memAlloc(totalTriVertices * vertexSize).order(ByteOrder.nativeOrder())
        val posVec = Vector4f()
        
        try {
            var currentVertexOffset = 0
            
            for ((textureView, group) in textureGroups) {
                val batchStartVertex = currentVertexOffset
                var batchVertexCount = 0
                
                for ((geometry, style) in group) {
                    val capturedVerts = geometry.getVertices()
                    val color = style.color
                    var alpha = if (style.fill) {
                        (style.fillOpacity * 125f).toInt().coerceIn(2, 125)
                    } else {
                        1
                    }
                    
                    if (isDepthTested) {
                        alpha = alpha or 128
                    }
                    
                    val packedColor = (alpha shl 24) or
                                     ((color.blue and 0xFF) shl 16) or
                                     ((color.green and 0xFF) shl 8) or
                                     (color.red and 0xFF)

                    val quadCount = capturedVerts.size / 4
                    for (q in 0 until quadCount) {
                        val baseIdx = q * 4
                        val quadVerts = arrayOf(
                            capturedVerts[baseIdx],
                            capturedVerts[baseIdx + 1],
                            capturedVerts[baseIdx + 2],
                            capturedVerts[baseIdx + 3]
                        )

                        val transformed = if (transform != null) {
                            quadVerts.map { v ->
                                transform.transform(v.x, v.y, v.z, v.w, posVec)
                                CapturedVertex(posVec.x, posVec.y, posVec.z, posVec.w, v.nx, v.ny, v.nz, v.u, v.v)
                            }
                        } else {
                            quadVerts.toList()
                        }

                        val v0 = transformed[0]
                        val v1 = transformed[1]
                        val v2 = transformed[2]
                        val v3 = transformed[3]

                        buffer.putFloat(v0.x).putFloat(v0.y).putFloat(v0.z).putFloat(v0.w).putFloat(v0.u).putFloat(v0.v).putInt(packedColor)
                        buffer.putFloat(v1.x).putFloat(v1.y).putFloat(v1.z).putFloat(v1.w).putFloat(v1.u).putFloat(v1.v).putInt(packedColor)
                        buffer.putFloat(v2.x).putFloat(v2.y).putFloat(v2.z).putFloat(v2.w).putFloat(v2.u).putFloat(v2.v).putInt(packedColor)

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
                val vbo = RenderSystem.getDevice().createBuffer({ "Lambda Outline ID (Depth)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                depthTestedVertexBuffer = vbo
            } else {
                xrayVertexBuffer?.close()
                val vbo = RenderSystem.getDevice().createBuffer({ "Lambda Outline ID (Xray)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                xrayVertexBuffer = vbo
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun renderPass(
        colorView: com.mojang.blaze3d.textures.GpuTextureView,
        vertexBuffer: GpuBuffer,
        batches: List<DrawBatch>
    ) {
        val depthView = OutlineIdBuffer.getSilhouetteDepthView() ?: return
        if (batches.isEmpty()) return

        val blockAtlas = mc.textureManager.getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
        val whiteTexture = blockAtlas.glTextureView
        val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
        
        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline ID Pass (Internal Silhouette)" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ) ?: return

	    renderPass.use { renderPass ->
		    renderPass.setPipeline(LambdaRenderPipelines.OUTLINE_ID)

		    val idUniform = RenderSystem.getDynamicUniforms().write(
			    Matrix4f(),
			    Vector4f(1f, 1f, 1f, 1f),
			    Vector3f(0f, 0f, 0f),
			    Matrix4f()
		    )
		    renderPass.setUniform("DynamicTransforms", idUniform)

		    renderPass.setVertexBuffer(0, vertexBuffer)

		    for (batch in batches) {
			    val textureView = batch.textureView ?: whiteTexture
			    renderPass.bindTexture("Sampler0", textureView, nearestSampler)
			    renderPass.draw(batch.vertexOffset, batch.vertexCount)
		    }
	    }
    }
    
    fun cleanup() {
        depthTestedVertexBuffer?.close()
        depthTestedVertexBuffer = null
        depthTestedBatches.clear()
        
        xrayVertexBuffer?.close()
        xrayVertexBuffer = null
        xrayBatches.clear()
    }
}
