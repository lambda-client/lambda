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

package com.lambda.graphics.mc

import com.lambda.Lambda.mc
import com.lambda.graphics.mc.renderer.RendererUtils
import com.lambda.graphics.mc.renderer.upload
import com.lambda.graphics.outline.OutlineStyle
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.item.ItemRenderer
import org.lwjgl.system.MemoryUtil
import java.util.*

class RegionRenderer {
	private var faceVertexBuffer: GpuBuffer? = null
	private var edgeVertexBuffer: GpuBuffer? = null
	private var textVertexBuffer: GpuBuffer? = null

	private var screenFaceVertexBuffer: GpuBuffer? = null
	private var screenEdgeVertexBuffer: GpuBuffer? = null
	private var screenTextVertexBuffer: GpuBuffer? = null

	private var screenImageBatches: List<TextureBatchResult> = emptyList()
	private var worldImageBatches: List<TextureBatchResult> = emptyList()
	private var modelBatches: List<TextureBatchResult> = emptyList()
	private var screenModelBatches: List<TextureBatchResult> = emptyList()

	private var faceIndexCount = 0
	private var edgeIndexCount = 0
	private var textIndexCount = 0

	private var screenFaceIndexCount = 0
	private var screenEdgeIndexCount = 0
	private var screenTextIndexCount = 0

	private var outlinedBatches: Map<Int, OutlinedBatchResult> = emptyMap()
	private var customOutlineStyles: Map<Int, OutlineStyle> = emptyMap()

	private var hasWorldData = false
	private var hasScreenData = false

	fun upload(collector: RegionVertexCollector) {
		val result = collector.buildWorld()
		val screenResult = collector.buildScreen()

		fun uploadBatch(batch: BuiltBatch?, label: String, oldBuffer: GpuBuffer?): BufferResult {
			oldBuffer?.close()
			if (batch == null) return BufferResult(null, 0)
			
			val buffer = RenderSystem.getDevice().createBuffer({ label }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, batch.buffer.remaining().toLong())
			buffer.upload(batch.buffer)
			MemoryUtil.memFree(batch.buffer)
			return BufferResult(buffer, batch.indexCount)
		}

		fun uploadTextureBatches(batches: List<BuiltTextureBatch>, label: String, oldBatches: List<TextureBatchResult>): List<TextureBatchResult> {
			oldBatches.forEach { it.buffer.close() }
			
			return batches.map { batch ->
				val buffer = RenderSystem.getDevice().createBuffer({ label }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, batch.buffer.remaining().toLong())
				buffer.upload(batch.buffer)
				MemoryUtil.memFree(batch.buffer)
				TextureBatchResult(batch.textureView, buffer, batch.indexCount, batch.useNearestFilter)
			}
		}

		val faceRes = uploadBatch(result.faces, "Lambda World Face Buffer", faceVertexBuffer)
		faceVertexBuffer = faceRes.buffer
		faceIndexCount = faceRes.indexCount

		val edgeRes = uploadBatch(result.edges, "Lambda World Edge Buffer", edgeVertexBuffer)
		edgeVertexBuffer = edgeRes.buffer
		edgeIndexCount = edgeRes.indexCount

		val textRes = uploadBatch(result.text, "Lambda World Text Buffer", textVertexBuffer)
		textVertexBuffer = textRes.buffer
		textIndexCount = textRes.indexCount

		val sFaceRes = uploadBatch(screenResult.faces, "Lambda Screen Face Buffer", screenFaceVertexBuffer)
		screenFaceVertexBuffer = sFaceRes.buffer
		screenFaceIndexCount = sFaceRes.indexCount

		val sEdgeRes = uploadBatch(screenResult.edges, "Lambda Screen Edge Buffer", screenEdgeVertexBuffer)
		screenEdgeVertexBuffer = sEdgeRes.buffer
		screenEdgeIndexCount = sEdgeRes.indexCount

		val sTextRes = uploadBatch(screenResult.text, "Lambda Screen Text Buffer", screenTextVertexBuffer)
		screenTextVertexBuffer = sTextRes.buffer
		screenTextIndexCount = sTextRes.indexCount

		worldImageBatches = uploadTextureBatches(result.images, "Lambda World Image Buffer", worldImageBatches)
		screenImageBatches = uploadTextureBatches(screenResult.images, "Lambda Screen Image Buffer", screenImageBatches)
		modelBatches = uploadTextureBatches(result.models, "Lambda World Model Buffer", modelBatches)
		screenModelBatches = uploadTextureBatches(screenResult.models, "Lambda Screen Model Buffer", screenModelBatches)

		val oldOutlined = outlinedBatches
		outlinedBatches = result.outlined.mapValues { (id, out) ->
			val old = oldOutlined[id]
			OutlinedBatchResult(
				faces = uploadBatch(out.faces, "Lambda Outlined Face Buffer ($id)", old?.faces?.buffer),
				edges = uploadBatch(out.edges, "Lambda Outlined Edge Buffer ($id)", old?.edges?.buffer),
				text = uploadBatch(out.text, "Lambda Outlined Text Buffer ($id)", old?.text?.buffer),
				models = uploadTextureBatches(out.models, "Lambda Outlined Model Buffer ($id)", old?.models ?: emptyList()),
				images = uploadTextureBatches(out.images, "Lambda Outlined Image Buffer ($id)", old?.images ?: emptyList())
			)
		}

		oldOutlined.forEach { (id, old) ->
			if (!outlinedBatches.containsKey(id)) {
				old.faces.buffer?.close()
				old.edges.buffer?.close()
				old.text.buffer?.close()
				old.models.forEach { it.buffer.close() }
				old.images.forEach { it.buffer.close() }
			}
		}

		customOutlineStyles = result.customOutlineStyles

		hasWorldData = faceVertexBuffer != null || edgeVertexBuffer != null || textVertexBuffer != null || worldImageBatches.isNotEmpty() || modelBatches.isNotEmpty() || outlinedBatches.isNotEmpty()
		hasScreenData = screenFaceVertexBuffer != null || screenEdgeVertexBuffer != null || screenTextVertexBuffer != null || screenImageBatches.isNotEmpty() || screenModelBatches.isNotEmpty()
	}

	fun getOutlineIds(): Set<Int> = outlinedBatches.keys

	fun getOutlineStyle(id: Int): OutlineStyle? = customOutlineStyles[id]

	fun hasOutlinedData(id: Int): Boolean = outlinedBatches.containsKey(id)

	fun renderOutlinedFaces(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.faces.buffer ?: return
		if (batch.faces.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.faces.indexCount)
	}

	fun renderOutlinedEdges(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.edges.buffer ?: return
		if (batch.edges.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.edges.indexCount)
	}

	fun renderOutlinedText(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.text.buffer ?: return
		if (batch.text.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.text.indexCount)
	}

	fun renderOutlinedModels(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		if (batch.models.isEmpty()) return
		renderModelBatches(renderPass, batch.models)
	}

	fun renderOutlinedImages(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		if (batch.images.isEmpty()) return
		renderImageBatches(renderPass, batch.images)
	}

	private fun renderImageBatches(renderPass: RenderPass, batches: List<TextureBatchResult>) {
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
		for (batch in batches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	private fun renderModelBatches(renderPass: RenderPass, batches: List<TextureBatchResult>) {
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
		val glintTexture = mc.textureManager.getTexture(ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
		for (batch in batches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			if (glintTexture != null) renderPass.bindTexture("Sampler3", glintTexture, linearSampler)
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	fun renderFaces(renderPass: RenderPass) {
		val vb = faceVertexBuffer ?: return
		if (faceIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(faceIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, faceIndexCount, 1)
	}

	fun renderEdges(renderPass: RenderPass) {
		val vb = edgeVertexBuffer ?: return
		if (edgeIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(edgeIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, edgeIndexCount, 1)
	}

	fun renderText(renderPass: RenderPass) {
		val vb = textVertexBuffer ?: return
		if (textIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(textIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, textIndexCount, 1)
	}

	fun hasTextData(): Boolean = textVertexBuffer != null && textIndexCount > 0

	fun renderScreenFaces(renderPass: RenderPass) {
		val vb = screenFaceVertexBuffer ?: return
		if (screenFaceIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenFaceIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenFaceIndexCount, 1)
	}

	fun renderScreenEdges(renderPass: RenderPass) {
		val vb = screenEdgeVertexBuffer ?: return
		if (screenEdgeIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenEdgeIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenEdgeIndexCount, 1)
	}

	fun renderScreenText(renderPass: RenderPass) {
		val vb = screenTextVertexBuffer ?: return
		if (screenTextIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenTextIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenTextIndexCount, 1)
	}

	fun hasScreenTextData(): Boolean = screenTextVertexBuffer != null && screenTextIndexCount > 0

	fun renderScreenImages(renderPass: RenderPass) {
		if (screenImageBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
		
		for (batch in screenImageBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	fun renderScreenModels(renderPass: RenderPass) {
		if (screenModelBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)

		val glintTexture = mc.textureManager.getTexture(ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
		
		for (batch in screenModelBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)

			if (glintTexture != null) {
				renderPass.bindTexture("Sampler3", glintTexture, linearSampler)
			}
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	fun hasScreenImageData(): Boolean = screenImageBatches.isNotEmpty()

	fun hasScreenModelData(): Boolean = screenModelBatches.isNotEmpty()

	fun renderWorldImages(renderPass: RenderPass) {
		if (worldImageBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
		
		for (batch in worldImageBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	fun hasWorldImageData(): Boolean = worldImageBatches.isNotEmpty()

	fun renderModels(renderPass: RenderPass) {
		if (modelBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)

		val glintTexture = mc.textureManager.getTexture(ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
		
		for (batch in modelBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)

			if (glintTexture != null) {
				renderPass.bindTexture("Sampler3", glintTexture, linearSampler)
			}
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	fun hasModelData(): Boolean = modelBatches.isNotEmpty()

	fun hasScreenData(): Boolean = hasScreenData

	fun clearData() {
		faceVertexBuffer?.close(); faceVertexBuffer = null
		edgeVertexBuffer?.close(); edgeVertexBuffer = null
		textVertexBuffer?.close(); textVertexBuffer = null
		faceIndexCount = 0
		edgeIndexCount = 0
		textIndexCount = 0
		hasWorldData = false

		screenFaceVertexBuffer?.close(); screenFaceVertexBuffer = null
		screenEdgeVertexBuffer?.close(); screenEdgeVertexBuffer = null
		screenTextVertexBuffer?.close(); screenTextVertexBuffer = null
		screenFaceIndexCount = 0
		screenEdgeIndexCount = 0
		screenTextIndexCount = 0

		outlinedBatches = emptyMap()
		customOutlineStyles = emptyMap()
		screenImageBatches = emptyList()
		worldImageBatches = emptyList()
		modelBatches = emptyList()
		screenModelBatches = emptyList()

		hasScreenData = false
	}

	fun hasData(): Boolean = hasWorldData

	companion object {
		fun createRenderPass(label: String, useMcDepth: Boolean): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null

			val depthView = if (useMcDepth) {
				framebuffer.depthAttachmentView
			} else {
				RendererUtils.getXrayDepthView()
			}

			return RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					{ label },
					framebuffer.colorAttachmentView,
					OptionalInt.empty(),
					depthView,
					OptionalDouble.empty()
				)
		}

		fun createScreenRenderPassWithDepth(label: String, clearDepth: Boolean = false): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null
			val depthView = RendererUtils.getScreenDepthView()

			return RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					{ label },
					framebuffer.colorAttachmentView,
					OptionalInt.empty(),
					depthView,
					if (clearDepth) OptionalDouble.of(1.0) else OptionalDouble.empty()
				)
		}

		fun renderQuadBuffer(renderPass: RenderPass, buffer: GpuBuffer, indexCount: Int) {
			if (indexCount == 0) return

			renderPass.setVertexBuffer(0, buffer)
			val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(indexCount)

			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, indexCount, 1)
		}
	}
}
