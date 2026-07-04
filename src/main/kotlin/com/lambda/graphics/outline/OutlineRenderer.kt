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
import com.lambda.graphics.shader.CustomShaders
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.util.math.BlockPos
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

    private var fullscreenQuadBuffer: GpuBuffer? = null

    private val groupGlow = OutlineGlow("Group")
    private val idGlow = OutlineGlow("Captured")

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
        val silDepth = if (depthTest) silhouetteDepthView ?: return else null
        val worldDepth = if (depthTest) mc.framebuffer?.depthAttachmentView ?: return else null
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        renderComposite(
            source = groupView,
            glowed = if (style.usesGlow()) groupGlow.glow(groupView, style, quadBuffer, silDepth, worldDepth) else groupView,
            style = style,
            label = "Lambda End Group Pass",
            quadBuffer = quadBuffer,
            silhouetteDepth = silDepth,
            worldDepth = worldDepth
        )
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

    fun renderAllIDPasses() {
        val depthTestedEntityStyles = OutlineHandler.getDepthTestedEntityStyles()
        val xrayEntityStyles = OutlineHandler.getXrayEntityStyles()

        val depthTestedBlockStyles = OutlineHandler.getDepthTestedBlockStyles()
        val xrayBlockStyles = OutlineHandler.getXrayBlockStyles()

        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val depthTestedEntityGroups = depthTestedEntityStyles.entries.groupBy({ it.value }, { it.key })
        val depthTestedBlockGroups = depthTestedBlockStyles.entries.groupBy({ it.value }, { it.key to it.value })

        val xrayEntityGroups = xrayEntityStyles.entries.groupBy({ it.value }, { it.key })
        val xrayBlockGroups = xrayBlockStyles.entries.groupBy({ it.value }, { it.key to it.value })

        val cameraPos = mc.gameRenderer?.camera?.pos
        val depthTestedStyles = (depthTestedEntityGroups.keys + depthTestedBlockGroups.keys).distinct()
            .sortedByDescending { style ->
                if (cameraPos != null) computeGroupDistance(
                    depthTestedEntityGroups[style], depthTestedBlockGroups[style], cameraPos
                ) else 0f
            }

        for (style in depthTestedStyles) {
            renderCapturedGroup(
                entityIds = depthTestedEntityGroups[style]?.toSet().orEmpty(),
                blocks = depthTestedBlockGroups[style]?.associate { it.first to it.second }.orEmpty(),
                useMcDepth = true,
                style = style,
                quadBuffer = quadBuffer
            )
        }

        val xrayStyles = (xrayEntityGroups.keys + xrayBlockGroups.keys).distinct()

        for (style in xrayStyles) {
            renderCapturedGroup(
                entityIds = xrayEntityGroups[style]?.toSet().orEmpty(),
                blocks = xrayBlockGroups[style]?.associate { it.first to it.second }.orEmpty(),
                useMcDepth = false,
                style = style,
                quadBuffer = quadBuffer
            )
        }
    }

    private fun computeGroupDistance(
        entityIds: List<Int>?,
        blocks: List<Pair<BlockPos, OutlineStyle>>?,
        cameraPos: net.minecraft.util.math.Vec3d
    ): Float {
        var minDistSq = Float.MAX_VALUE
        val world = mc.world

        entityIds?.forEach { id ->
            val entity = world?.getEntityById(id) ?: return@forEach
            val distSq = entity.squaredDistanceTo(cameraPos).toFloat()
            if (distSq < minDistSq) minDistSq = distSq
        }

        blocks?.forEach { (pos, _) ->
            val distSq = pos.getSquaredDistance(cameraPos.x, cameraPos.y, cameraPos.z).toFloat()
            if (distSq < minDistSq) minDistSq = distSq
        }

        return if (minDistSq == Float.MAX_VALUE) 0f else minDistSq
    }

    private fun renderCapturedGroup(
        entityIds: Set<Int>,
        blocks: Map<BlockPos, OutlineStyle>,
        useMcDepth: Boolean,
        style: OutlineStyle,
        quadBuffer: GpuBuffer
    ) {
        if (entityIds.isEmpty() && blocks.isEmpty()) return

        OutlineIdBuffer.beginFrame()

        if (entityIds.isNotEmpty()) {
            OutlineIdPassRenderer.render(entityIds, useMcDepth = useMcDepth)
        }

        if (blocks.isNotEmpty()) {
            OutlineIdPassRenderer.renderBlocks(blocks, useMcDepth = useMcDepth)
        }

        if (!OutlineIdBuffer.hasData) return

        val idBufferView = OutlineIdBuffer.getTextureView() ?: return
        val silDepth = if (useMcDepth) OutlineIdBuffer.getSilhouetteDepthView() ?: return else null
        val worldDepth = if (useMcDepth) OutlineIdBuffer.getMcDepthView() ?: return else null
        val glowed = if (style.usesGlow()) idGlow.glow(idBufferView, style, quadBuffer, silDepth, worldDepth) else idBufferView
        renderComposite(
            source = idBufferView,
            glowed = glowed,
            style = style,
            label = if (useMcDepth) "Lambda Captured Outline Composite (Depth)" else "Lambda Captured Outline Composite (Xray)",
            quadBuffer = quadBuffer,
            silhouetteDepth = silDepth,
            worldDepth = worldDepth
        )
    }

    private fun renderComposite(
        source: GpuTextureView,
        glowed: GpuTextureView,
        style: OutlineStyle,
        label: String,
        quadBuffer: GpuBuffer,
        silhouetteDepth: GpuTextureView? = null,
        worldDepth: GpuTextureView? = null
    ) {
        val framebuffer = mc.framebuffer ?: return
        val depthTest = silhouetteDepth != null && worldDepth != null
        val postUniform = OutlinePostUniforms.write(framebuffer.textureWidth.toFloat(), framebuffer.textureHeight.toFloat())
        val outlineUniform = OutlineCompositeUniforms.write(style, depthTest)
        val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
        val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
        val pipeline = if (CustomShaders.OUTLINE.isSelected(style.customShader)) {
            CustomShaders.OUTLINE.getOrDefault(style.customShader, LambdaRenderPipelines.OUTLINE_COMPOSITE)
        } else {
            LambdaRenderPipelines.OUTLINE_COMPOSITE
        }

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Outline Sobel Pass" },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
            )?.use { pass ->
                pass.setPipeline(pipeline)
                pass.bindTexture("Sampler0", source, nearestSampler)
                pass.bindTexture("Sampler1", glowed, linearSampler)
                pass.bindTexture("Sampler2", silhouetteDepth ?: source, nearestSampler)
                pass.bindTexture("Sampler3", worldDepth ?: source, nearestSampler)
                pass.setUniform("PostData", postUniform)
                pass.setUniform("OutlineData", outlineUniform)
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
        fullscreenQuadBuffer?.close()
        groupGlow.cleanup()
        idGlow.cleanup()
        OutlinePostUniforms.clear()
        OutlineGlowUniforms.clear()
        OutlineCompositeUniforms.clear()

        silhouetteView = null
        silhouetteTexture = null
        silhouetteDepthView = null
        silhouetteDepthTexture = null
        fullscreenQuadBuffer = null
        silhouetteWidth = 0
        silhouetteHeight = 0

        OutlineIdBuffer.cleanup()
        OutlineIdPassRenderer.cleanup()
    }
}
