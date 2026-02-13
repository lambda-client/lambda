/*
 * Copyright 2025 Lambda
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
import com.lambda.graphics.outline.CapturedGeometry
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.renderer.upload
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import org.lwjgl.system.MemoryUtil
import java.util.*

/**
 * Renderer for ESP rendering using MC 1.21.11's new render pipeline.
 *
 * This renderer manages the lifecycle of dedicated GPU buffers and provides
 * methods to render them within a RenderPass.
 */
class RegionRenderer {
	// Dedicated GPU buffers for world-space faces, edges, and text
	private var faceVertexBuffer: GpuBuffer? = null
	private var edgeVertexBuffer: GpuBuffer? = null
	private var textVertexBuffer: GpuBuffer? = null

	// Dedicated GPU buffers for screen-space faces, edges, and text
	private var screenFaceVertexBuffer: GpuBuffer? = null
	private var screenEdgeVertexBuffer: GpuBuffer? = null
	private var screenTextVertexBuffer: GpuBuffer? = null

	// Image batches (texture -> buffer) for screen and world space
	private var screenImageBatches: List<TextureBatchResult> = emptyList()
	private var worldImageBatches: List<TextureBatchResult> = emptyList()
	private var modelBatches: List<TextureBatchResult> = emptyList()
	private var screenModelBatches: List<TextureBatchResult> = emptyList()

	// Index counts for world-space draw calls
	private var faceIndexCount = 0
	private var edgeIndexCount = 0
	private var textIndexCount = 0

	// Index counts for screen-space draw calls
	private var screenFaceIndexCount = 0
	private var screenEdgeIndexCount = 0
	private var screenTextIndexCount = 0
	
	// Outlined batches (id -> buffers)
	private var outlinedBatches: Map<Int, OutlinedBatchResult> = emptyMap()

	// State tracking
	private var hasData = false
	private var hasScreenData = false

	/**
	 * Upload collected vertices from an external collector. This must be called on the main/render
	 * thread.
	 *
	 * @param collector The collector containing the geometry to upload
	 */
	fun upload(collector: RegionVertexCollector) {
		val result = collector.build()
		val screenResult = collector.buildScreen()


		// Helper to upload a built batch
		fun uploadBatch(batch: BuiltBatch?, label: String, oldBuffer: GpuBuffer?): BufferResult {
			oldBuffer?.close()
			if (batch == null) return BufferResult(null, 0)
			
			val buffer = RenderSystem.getDevice().createBuffer({ label }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, batch.buffer.remaining().toLong())
			buffer.upload(batch.buffer)
			MemoryUtil.memFree(batch.buffer)
			return BufferResult(buffer, batch.indexCount)
		}

		// Helper to upload texture batches
		fun uploadTextureBatches(batches: List<TextureBuildBatch>, label: String, oldBatches: List<TextureBatchResult>): List<TextureBatchResult> {
			oldBatches.forEach { it.buffer.close() }
			
			return batches.map { batch ->
				val buffer = RenderSystem.getDevice().createBuffer({ label }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, batch.buffer.remaining().toLong())
				buffer.upload(batch.buffer)
				MemoryUtil.memFree(batch.buffer)
				TextureBatchResult(batch.textureView, buffer, batch.indexCount, batch.useNearestFilter)
			}
		}

		// World-space
		val faceRes = uploadBatch(result.faces, "Lambda ESP Face Buffer", faceVertexBuffer)
		faceVertexBuffer = faceRes.buffer
		faceIndexCount = faceRes.indexCount

		val edgeRes = uploadBatch(result.edges, "Lambda ESP Edge Buffer", edgeVertexBuffer)
		edgeVertexBuffer = edgeRes.buffer
		edgeIndexCount = edgeRes.indexCount

		val textRes = uploadBatch(result.text, "Lambda ESP Text Buffer", textVertexBuffer)
		textVertexBuffer = textRes.buffer
		textIndexCount = textRes.indexCount

		// Screen-space
		val sFaceRes = uploadBatch(screenResult.faces, "Lambda Screen Face Buffer", screenFaceVertexBuffer)
		screenFaceVertexBuffer = sFaceRes.buffer
		screenFaceIndexCount = sFaceRes.indexCount

		val sEdgeRes = uploadBatch(screenResult.edges, "Lambda Screen Edge Buffer", screenEdgeVertexBuffer)
		screenEdgeVertexBuffer = sEdgeRes.buffer
		screenEdgeIndexCount = sEdgeRes.indexCount

		val sTextRes = uploadBatch(screenResult.text, "Lambda Screen Text Buffer", screenTextVertexBuffer)
		screenTextVertexBuffer = sTextRes.buffer
		screenTextIndexCount = sTextRes.indexCount

		// Batches
		screenImageBatches = uploadTextureBatches(screenResult.images, "Lambda Screen Image Buffer", screenImageBatches)
		worldImageBatches = uploadTextureBatches(result.images, "Lambda World Image Buffer", worldImageBatches)
		modelBatches = uploadTextureBatches(result.models, "Lambda Model Buffer", modelBatches)
		screenModelBatches = uploadTextureBatches(screenResult.models, "Lambda Screen Model Buffer", screenModelBatches)

		// Outlines
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
		
		// Close remaining old outlined batches that weren't updated
		oldOutlined.forEach { (id, old) ->
			if (!outlinedBatches.containsKey(id)) {
				old.faces.buffer?.close()
				old.edges.buffer?.close()
				old.text.buffer?.close()
				old.models.forEach { it.buffer.close() }
				old.images.forEach { it.buffer.close() }
			}
		}

		hasData = faceVertexBuffer != null || edgeVertexBuffer != null || textVertexBuffer != null || worldImageBatches.isNotEmpty() || modelBatches.isNotEmpty() || outlinedBatches.isNotEmpty()
		hasScreenData = screenFaceVertexBuffer != null || screenEdgeVertexBuffer != null || screenTextVertexBuffer != null || screenImageBatches.isNotEmpty() || screenModelBatches.isNotEmpty()
	}
	
	/**
	 * Get IDs of active outlines in this renderer.
	 */
	fun getOutlineIds(): Set<Int> = outlinedBatches.keys

	/**
	 * Check if there is outlined geometry for a specific ID.
	 */
	fun hasOutlinedData(id: Int): Boolean = outlinedBatches.containsKey(id)

	/**
	 * Render outlined faces for a specific ID.
	 */
	fun renderOutlinedFaces(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.faces.buffer ?: return
		if (batch.faces.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.faces.indexCount)
	}

	/**
	 * Render outlined edges for a specific ID.
	 */
	fun renderOutlinedEdges(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.edges.buffer ?: return
		if (batch.edges.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.edges.indexCount)
	}

	/**
	 * Render outlined text for a specific ID.
	 */
	fun renderOutlinedText(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		val vb = batch.text.buffer ?: return
		if (batch.text.indexCount == 0) return
		renderQuadBuffer(renderPass, vb, batch.text.indexCount)
	}

	/**
	 * Render outlined models for a specific ID.
	 */
	fun renderOutlinedModels(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		if (batch.models.isEmpty()) return
		renderModelBatches(renderPass, batch.models)
	}

	/**
	 * Render outlined images for a specific ID.
	 */
	fun renderOutlinedImages(renderPass: RenderPass, id: Int) {
		val batch = outlinedBatches[id] ?: return
		if (batch.images.isEmpty()) return
		renderImageBatches(renderPass, batch.images)
	}

	private fun renderImageBatches(renderPass: RenderPass, batches: List<TextureBatchResult>) {
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
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
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
		val glintTexture = mc.textureManager.getTexture(net.minecraft.client.render.item.ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
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

	/**
	 * Render faces using the given render pass.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderFaces(renderPass: RenderPass) {
		val vb = faceVertexBuffer ?: return
		if (faceIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		// Use vanilla's sequential index buffer for quads
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(faceIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, faceIndexCount, 1)
	}

	/**
	 * Render edges using the given render pass.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderEdges(renderPass: RenderPass) {
		val vb = edgeVertexBuffer ?: return
		if (edgeIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		// Use vanilla's sequential index buffer for quads
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(edgeIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, edgeIndexCount, 1)
	}

	/**
	 * Render text using the given render pass.
	 * Note: Caller must bind the font texture and SDF params uniform before calling.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderText(renderPass: RenderPass) {
		val vb = textVertexBuffer ?: return
		if (textIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		// Use vanilla's sequential index buffer for quads
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(textIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, textIndexCount, 1)
	}

	/** Check if this renderer has text data. */
	fun hasTextData(): Boolean = textVertexBuffer != null && textIndexCount > 0

	// ============================================================================
	// Screen-Space Render Methods
	// ============================================================================

	/**
	 * Render screen-space faces using the given render pass.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderScreenFaces(renderPass: RenderPass) {
		val vb = screenFaceVertexBuffer ?: return
		if (screenFaceIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenFaceIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenFaceIndexCount, 1)
	}

	/**
	 * Render screen-space edges using the given render pass.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderScreenEdges(renderPass: RenderPass) {
		val vb = screenEdgeVertexBuffer ?: return
		if (screenEdgeIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenEdgeIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenEdgeIndexCount, 1)
	}

	/**
	 * Render screen-space text using the given render pass.
	 * Note: Caller must bind the font texture before calling.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderScreenText(renderPass: RenderPass) {
		val vb = screenTextVertexBuffer ?: return
		if (screenTextIndexCount == 0) return

		renderPass.setVertexBuffer(0, vb)
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val indexBuffer = shapeIndexBuffer.getIndexBuffer(screenTextIndexCount)

		renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
		renderPass.drawIndexed(0, 0, screenTextIndexCount, 1)
	}

	/** Check if this renderer has screen-space text data. */
	fun hasScreenTextData(): Boolean = screenTextVertexBuffer != null && screenTextIndexCount > 0

	/**
	 * Render screen-space images using the given render pass.
	 * Each texture batch is rendered separately with its texture bound.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderScreenImages(renderPass: RenderPass) {
		if (screenImageBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
		
		for (batch in screenImageBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	/**
	 * Render screen-space models using the given render pass.
	 */
	fun renderScreenModels(renderPass: RenderPass) {
		if (screenModelBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
		
		// Get glint texture for enchantment shimmer
		val glintTexture = mc.textureManager.getTexture(net.minecraft.client.render.item.ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
		
		for (batch in screenModelBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			
			// Bind glint texture to Sampler3
			if (glintTexture != null) {
				renderPass.bindTexture("Sampler3", glintTexture, linearSampler)
			}
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}
	
	/** Check if this renderer has screen-space image data. */
	fun hasScreenImageData(): Boolean = screenImageBatches.isNotEmpty()

	/** Check if this renderer has screen-space model data. */
	fun hasScreenModelData(): Boolean = screenModelBatches.isNotEmpty()

	/**
	 * Render world-space images using the given render pass.
	 * Each texture batch is rendered separately with its texture bound.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderWorldImages(renderPass: RenderPass) {
		if (worldImageBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
		
		for (batch in worldImageBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	/** Check if this renderer has world-space image data. */
	fun hasWorldImageData(): Boolean = worldImageBatches.isNotEmpty()

	/**
	 * Render 3D models using the given render pass.
	 * Each texture batch is rendered separately with its texture bound.
	 *
	 * @param renderPass The active RenderPass to record commands into
	 */
	fun renderModels(renderPass: RenderPass) {
		if (modelBatches.isEmpty()) return
		
		val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
		val linearSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)
		
		// Get glint texture for enchantment shimmer
		val glintTexture = mc.textureManager.getTexture(net.minecraft.client.render.item.ItemRenderer.ITEM_ENCHANTMENT_GLINT)?.glTextureView
		
		for (batch in modelBatches) {
			val sampler = if (batch.useNearestFilter) nearestSampler else linearSampler
			renderPass.bindTexture("Sampler0", batch.textureView, sampler)
			
			// Bind glint texture to Sampler3
			if (glintTexture != null) {
				renderPass.bindTexture("Sampler3", glintTexture, linearSampler)
			}
			
			renderPass.setVertexBuffer(0, batch.buffer)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(batch.indexCount)
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, batch.indexCount, 1)
		}
	}

	/** Check if this renderer has model data. */
	fun hasModelData(): Boolean = modelBatches.isNotEmpty()

	/** Check if this renderer has any screen-space data to render. */
	fun hasScreenData(): Boolean = hasScreenData

	/** Clear all geometry data. Note: buffers are managed by GpuBufferPool. */
	fun clearData() {
		// Just clear references and counts - GpuBufferPool handles the actual buffers
		faceVertexBuffer = null
		edgeVertexBuffer = null
		textVertexBuffer = null
		faceIndexCount = 0
		edgeIndexCount = 0
		textIndexCount = 0
		hasData = false

		screenFaceVertexBuffer = null
		screenEdgeVertexBuffer = null
		screenTextVertexBuffer = null
		screenFaceIndexCount = 0
		screenEdgeIndexCount = 0
		screenTextIndexCount = 0

		outlinedBatches = emptyMap()
		screenImageBatches = emptyList()
		worldImageBatches = emptyList()
		modelBatches = emptyList()
		screenModelBatches = emptyList()

		hasScreenData = false
	}

	/** Check if this renderer has any data to render. */
	fun hasData(): Boolean = hasData

	companion object {
		/** Helper to create a render pass targeting the main framebuffer with MC's depth. */
		fun createRenderPass(label: String): RenderPass? {
			return createRenderPass(label, useMcDepth = true)
		}

		/**
		 * Helper to create a render pass for world-space rendering.
		 * @param label Debug label for the render pass
		 * @param useMcDepth If true, use MC's depth buffer (normal depth testing against world).
		 *                   If false, use Lambda's custom xray depth buffer (self-ordering, ignores MC world).
		 */
		fun createRenderPass(label: String, useMcDepth: Boolean): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null
			
			// Choose depth buffer:
			// - true = MC's depth (normal depth testing against world)
			// - false = Lambda's xray depth (self-ordering, ignores MC world)
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

		/**
		 * Helper to create a render pass for screen-space rendering (no depth buffer).
		 * Uses painter's algorithm: last drawn is on top.
		 * @param label Debug label for the render pass
		 */
		fun createScreenRenderPass(label: String): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null
			
			return RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					{ label },
					framebuffer.colorAttachmentView,
					OptionalInt.empty(),
					null, // No depth buffer - painter's algorithm
					OptionalDouble.empty()
				)
		}

		/**
		 * Helper to create a render pass for screen-space rendering WITH depth testing.
		 * Enables unified layering across all screen element types (models, faces, text, etc.).
		 * Uses a SEPARATE screen depth buffer to ensure complete isolation from world-space.
		 * 
		 * @param label Debug label for the render pass
		 * @param clearDepth If true, clear the depth buffer to 1.0 (call this on the first screen pass)
		 */
		fun createScreenRenderPassWithDepth(label: String, clearDepth: Boolean = false): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null
			val depthView = RendererUtils.getScreenDepthView()  // Use separate screen depth buffer
			
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

		/**
		 * Render a custom vertex buffer using quads mode.
		 * Used for styled text rendering where each style has its own buffer.
		 *
		 * @param renderPass The active RenderPass to record commands into
		 * @param buffer The vertex buffer to render
		 * @param indexCount The number of indices (vertices) to render
		 */
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
