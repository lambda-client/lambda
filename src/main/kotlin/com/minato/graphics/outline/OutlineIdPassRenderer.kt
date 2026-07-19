
package com.minato.graphics.outline

import com.minato.Minato.mc
import com.minato.graphics.RenderMain
import com.minato.graphics.mc.MinatoRenderPipelines
import com.minato.graphics.mc.renderer.upload
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.BlockModelPart
import net.minecraft.client.util.math.Vector2f
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.util.*

object OutlineIdPassRenderer {
    private val vertexSize = 28

    private class DrawBatch(val textureView: GpuTextureView?, val vertexCount: Int, val vertexOffset: Int)
    
    private var depthTestedVertexBuffer: GpuBuffer? = null
    private val depthTestedBatches = mutableListOf<DrawBatch>()
    
    private var xrayVertexBuffer: GpuBuffer? = null
    private val xrayBatches = mutableListOf<DrawBatch>()
    
    private var blockDepthTestedVertexBuffer: GpuBuffer? = null
    private val blockDepthTestedBatches = mutableListOf<DrawBatch>()
    
    private var blockXrayVertexBuffer: GpuBuffer? = null
    private val blockXrayBatches = mutableListOf<DrawBatch>()
    
    fun render(entityIds: Set<Int>, useMcDepth: Boolean) {
        if (!OutlineIdBuffer.ensureBuffer()) return
        
        val colorView = OutlineIdBuffer.getTextureView() ?: return
        
        val outlines = if (useMcDepth) OutlineHandler.getDepthTestedEntityStyles() else OutlineHandler.getXrayEntityStyles()
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
    
    fun buildVertexBuffer(entities: Map<Int, OutlineStyle>, isDepthTested: Boolean) {
        val geometries = entities.mapValues { (id, _) -> VertexCapture.getEntityGeometries(id) }
        buildVertexBufferInternal(geometries, styles = entities, isDepthTested = isDepthTested)
    }

    private fun buildVertexBufferInternal(
        allGeometries: Map<*, List<CapturedGeometry>>,
        styles: Map<*, OutlineStyle>,
        isDepthTested: Boolean
    ) {
        val targetBatches = if (isDepthTested) depthTestedBatches else xrayBatches
        targetBatches.clear()

        val textureGroups = mutableMapOf<GpuTextureView?, MutableList<Pair<CapturedGeometry, OutlineStyle>>>()
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
        
        try {
            var currentVertexOffset = 0
            
            for ((textureView, group) in textureGroups) {
                val batchStartVertex = currentVertexOffset
                var batchVertexCount = 0
                
                for ((geometry, style) in group) {
                    val capturedVerts = geometry.getVertices()
                    val color = style.color
                    var alpha =
                        if (style.fill) (style.fillOpacity * 125f).toInt().coerceIn(2, 125)
                        else 1
                    
                    if (isDepthTested) alpha = alpha or 128
                    
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

                        val v0 = quadVerts[0]
                        val v1 = quadVerts[1]
                        val v2 = quadVerts[2]
                        val v3 = quadVerts[3]

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
                val vbo = RenderSystem.getDevice().createBuffer({ "Minato Outline ID (Depth)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                depthTestedVertexBuffer = vbo
            } else {
                xrayVertexBuffer?.close()
                val vbo = RenderSystem.getDevice().createBuffer({ "Minato Outline ID (Xray)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                xrayVertexBuffer = vbo
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun renderPass(
        colorView: GpuTextureView,
        vertexBuffer: GpuBuffer,
        batches: List<DrawBatch>
    ) {
        val depthView = OutlineIdBuffer.getSilhouetteDepthView() ?: return
        if (batches.isEmpty()) return

        val blockAtlas = mc.textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png"))
        val fallbackTexture = blockAtlas.glTextureView
        val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)

        val idUniform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            Matrix4f()
        )

        val renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Minato Outline ID Pass (Internal Silhouette)" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ) ?: return

	    renderPass.use { renderPass ->
		    renderPass.setPipeline(MinatoRenderPipelines.OUTLINE_ID)

		    renderPass.setUniform("DynamicTransforms", idUniform)

		    renderPass.setVertexBuffer(0, vertexBuffer)

		    for (batch in batches) {
			    val textureView = batch.textureView ?: fallbackTexture
			    renderPass.bindTexture("Sampler0", textureView, nearestSampler)
			    renderPass.draw(batch.vertexOffset, batch.vertexCount)
		    }
	    }
    }

    fun renderBlocks(blocks: Map<BlockPos, OutlineStyle>, useMcDepth: Boolean) {
        if (blocks.isEmpty()) return
        if (!OutlineIdBuffer.ensureBuffer()) return
        
        val colorView = OutlineIdBuffer.getTextureView() ?: return
        val cameraPos = mc.gameRenderer?.camera?.pos ?: return
        
        val viewProj = RenderMain.projModel

        val targetBatches = if (useMcDepth) blockDepthTestedBatches else blockXrayBatches
        targetBatches.clear()

        val world = mc.world ?: return
        val blockRenderManager = mc.blockRenderManager
        val random = Random.create()
        val allDirections = Direction.entries + listOf(null)

        val blockData = mutableListOf<Pair<OutlineStyle, List<Pair<Vector3f, BakedQuad>>>>()
        val blockEntityData = mutableMapOf<OutlineStyle, MutableList<Pair<GpuTextureView?, CapturedGeometry>>>()
        var totalTriVertices = 0
        
        for ((pos, style) in blocks) {
            val geometries = VertexCapture.getEntityGeometries(pos)
            for (g in geometries) {
                if (!g.isEmpty()) {
                    blockEntityData.getOrPut(style) { mutableListOf() }.add(g.textureView to g)
                    totalTriVertices += (g.getVertices().size / 4) * 6
                }
            }

            val state = world.getBlockState(pos)
            if (state.isAir || state.renderType == net.minecraft.block.BlockRenderType.INVISIBLE) continue
            
            val blockStateModel = blockRenderManager.getModel(state)
            val parts = mutableListOf<BlockModelPart>()
            random.setSeed(state.getRenderingSeed(pos))
            blockStateModel.addParts(random, parts)
            
            val offset = Vector3f(
                (pos.x.toDouble() - cameraPos.x).toFloat(),
                (pos.y.toDouble() - cameraPos.y).toFloat(),
                (pos.z.toDouble() - cameraPos.z).toFloat()
            )
            
            val quads = mutableListOf<Pair<Vector3f, BakedQuad>>()
            for (part in parts) {
                for (dir in allDirections) {
                    val partQuads = part.getQuads(dir)
                    if (partQuads.isNotEmpty()) {
                        for (q in partQuads) {
                            quads.add(offset to q)
                        }
                    }
                }
            }
            
            if (quads.isNotEmpty()) {
                blockData.add(style to quads)
                totalTriVertices += quads.size * 6
            }
        }

        if (totalTriVertices == 0) return
        
        val buffer = MemoryUtil.memAlloc(totalTriVertices * vertexSize).order(ByteOrder.nativeOrder())
        
        try {
            var batchVertexCount = 0
            
            for ((style, quads) in blockData) {
                val color = style.color
                var alpha =
                    if (style.fill) (style.fillOpacity * 125f).toInt().coerceIn(2, 125)
                    else 1
                
                if (useMcDepth) alpha = alpha or 128
                
                val packedColor = (alpha shl 24) or
                        ((color.blue and 0xFF) shl 16) or
                        ((color.green and 0xFF) shl 8) or
                        (color.red and 0xFF)

                for ((offset, quad) in quads) {
                    val p0 = quad.getPosition(0)
                    val c0 = viewProj.transform(p0.x() + offset.x, p0.y() + offset.y, p0.z() + offset.z, 1.0f, Vector4f())

                    val p1 = quad.getPosition(1)
                    val c1 = viewProj.transform(p1.x() + offset.x, p1.y() + offset.y, p1.z() + offset.z, 1.0f, Vector4f())

                    val p2 = quad.getPosition(2)
                    val c2 = viewProj.transform(p2.x() + offset.x, p2.y() + offset.y, p2.z() + offset.z, 1.0f, Vector4f())

                    val p3 = quad.getPosition(3)
                    val c3 = viewProj.transform(p3.x() + offset.x, p3.y() + offset.y, p3.z() + offset.z, 1.0f, Vector4f())

                    val anyBehind = c0.w <= 0.05f || c1.w <= 0.05f || c2.w <= 0.05f || c3.w <= 0.05f
                    
                    if (anyBehind) {
                        repeat(6) {
                            buffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
                            buffer.putFloat(0f).putFloat(0f)
                            buffer.putInt(0)
                        }
                    } else {
                        val uvA = quad.getTexcoords(0)
                        val uA = Vector2f.getX(uvA)
                        val vA = Vector2f.getY(uvA)
                        
                        val uvB = quad.getTexcoords(1)
                        val uB = Vector2f.getX(uvB)
                        val vB = Vector2f.getY(uvB)
                        
                        val uvC = quad.getTexcoords(2)
                        val uC = Vector2f.getX(uvC)
                        val vC = Vector2f.getY(uvC)
                        
                        val uvD = quad.getTexcoords(3)
                        val uD = Vector2f.getX(uvD)
                        val vD = Vector2f.getY(uvD)

                        buffer.putFloat(c0.x).putFloat(c0.y).putFloat(c0.z).putFloat(c0.w)
                        buffer.putFloat(uA).putFloat(vA)
                        buffer.putInt(packedColor)
                        
                        buffer.putFloat(c1.x).putFloat(c1.y).putFloat(c1.z).putFloat(c1.w)
                        buffer.putFloat(uB).putFloat(vB)
                        buffer.putInt(packedColor)
                        
                        buffer.putFloat(c2.x).putFloat(c2.y).putFloat(c2.z).putFloat(c2.w)
                        buffer.putFloat(uC).putFloat(vC)
                        buffer.putInt(packedColor)

                        buffer.putFloat(c0.x).putFloat(c0.y).putFloat(c0.z).putFloat(c0.w)
                        buffer.putFloat(uA).putFloat(vA)
                        buffer.putInt(packedColor)
                        
                        buffer.putFloat(c2.x).putFloat(c2.y).putFloat(c2.z).putFloat(c2.w)
                        buffer.putFloat(uC).putFloat(vC)
                        buffer.putInt(packedColor)
                        
                        buffer.putFloat(c3.x).putFloat(c3.y).putFloat(c3.z).putFloat(c3.w)
                        buffer.putFloat(uD).putFloat(vD)
                        buffer.putInt(packedColor)
                    }
                    
                    batchVertexCount += 6
                }
            }
            
            if (batchVertexCount > 0) {
                targetBatches.add(DrawBatch(null, batchVertexCount, 0))
            }
            
            var currentVertexOffset = batchVertexCount

            val textureGroups = mutableMapOf<GpuTextureView?, MutableList<Pair<CapturedGeometry, OutlineStyle>>>()
            for ((style, list) in blockEntityData) {
                for ((textureView, geometry) in list) {
                    textureGroups.getOrPut(textureView) { mutableListOf() }.add(geometry to style)
                }
            }

            for ((textureView, group) in textureGroups) {
                val batchStartVertex = currentVertexOffset
                var entBatchVertexCount = 0
                
                for ((geometry, style) in group) {
                    val capturedVerts = geometry.getVertices()
                    val color = style.color
                    var alpha =
                        if (style.fill) (style.fillOpacity * 125f).toInt().coerceIn(2, 125)
                        else 1
                    
                    if (useMcDepth) alpha = alpha or 128
                    
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
                        
                        val anyBehind = quadVerts.any { it.w <= 0.0f }
                        if (anyBehind) {
                            repeat(6) {
                                buffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
                                buffer.putFloat(0f).putFloat(0f)
                                buffer.putInt(0)
                            }
                        } else {
                            buffer.putFloat(quadVerts[0].x).putFloat(quadVerts[0].y).putFloat(quadVerts[0].z).putFloat(quadVerts[0].w)
                                .putFloat(quadVerts[0].u).putFloat(quadVerts[0].v).putInt(packedColor)
                            buffer.putFloat(quadVerts[1].x).putFloat(quadVerts[1].y).putFloat(quadVerts[1].z).putFloat(quadVerts[1].w)
                                .putFloat(quadVerts[1].u).putFloat(quadVerts[1].v).putInt(packedColor)
                            buffer.putFloat(quadVerts[2].x).putFloat(quadVerts[2].y).putFloat(quadVerts[2].z).putFloat(quadVerts[2].w)
                                .putFloat(quadVerts[2].u).putFloat(quadVerts[2].v).putInt(packedColor)

                            buffer.putFloat(quadVerts[0].x).putFloat(quadVerts[0].y).putFloat(quadVerts[0].z).putFloat(quadVerts[0].w)
                                .putFloat(quadVerts[0].u).putFloat(quadVerts[0].v).putInt(packedColor)
                            buffer.putFloat(quadVerts[2].x).putFloat(quadVerts[2].y).putFloat(quadVerts[2].z).putFloat(quadVerts[2].w)
                                .putFloat(quadVerts[2].u).putFloat(quadVerts[2].v).putInt(packedColor)
                            buffer.putFloat(quadVerts[3].x).putFloat(quadVerts[3].y).putFloat(quadVerts[3].z).putFloat(quadVerts[3].w)
                                .putFloat(quadVerts[3].u).putFloat(quadVerts[3].v).putInt(packedColor)
                        }
                        entBatchVertexCount += 6
                        currentVertexOffset += 6
                    }
                }
                
                if (entBatchVertexCount > 0) {
                    targetBatches.add(DrawBatch(textureView, entBatchVertexCount, batchStartVertex))
                }
            }
            
            buffer.flip()
            if (useMcDepth) {
                blockDepthTestedVertexBuffer?.close()
                val vbo = RenderSystem.getDevice().createBuffer({ "Minato Block Outline ID (Depth)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                blockDepthTestedVertexBuffer = vbo
            } else {
                blockXrayVertexBuffer?.close()
                val vbo = RenderSystem.getDevice().createBuffer({ "Minato Block Outline ID (Xray)" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, buffer.remaining().toLong())
                vbo.upload(buffer)
                blockXrayVertexBuffer = vbo
            }
        } finally {
            MemoryUtil.memFree(buffer)
        }
        
        val vbo = if (useMcDepth) blockDepthTestedVertexBuffer else blockXrayVertexBuffer
        if (vbo != null && targetBatches.isNotEmpty()) {
            OutlineIdBuffer.markHasData()
            renderPass(colorView, vbo, targetBatches)
        }
    }
    
    fun cleanup() {
        depthTestedVertexBuffer?.close()
        depthTestedVertexBuffer = null
        depthTestedBatches.clear()
        
        xrayVertexBuffer?.close()
        xrayVertexBuffer = null
        xrayBatches.clear()
        
        blockDepthTestedVertexBuffer?.close()
        blockDepthTestedVertexBuffer = null
        blockDepthTestedBatches.clear()
        
        blockXrayVertexBuffer?.close()
        blockXrayVertexBuffer = null
        blockXrayBatches.clear()
    }
}
