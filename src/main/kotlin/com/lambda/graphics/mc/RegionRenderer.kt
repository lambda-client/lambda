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
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
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

	// Index counts for world-space draw calls
	private var faceIndexCount = 0
	private var edgeIndexCount = 0
	private var textIndexCount = 0

	// Index counts for screen-space draw calls
	private var screenFaceIndexCount = 0
	private var screenEdgeIndexCount = 0
	private var screenTextIndexCount = 0

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
		val result = collector.upload()
		val screenResult = collector.uploadScreen()

		// Cleanup old world-space buffers
		faceVertexBuffer?.close()
		edgeVertexBuffer?.close()
		textVertexBuffer?.close()

		// Cleanup old screen-space buffers
		screenFaceVertexBuffer?.close()
		screenEdgeVertexBuffer?.close()
		screenTextVertexBuffer?.close()

		// Assign new world-space buffers and counts
		faceVertexBuffer = result.faces?.buffer
		faceIndexCount = result.faces?.indexCount ?: 0

		edgeVertexBuffer = result.edges?.buffer
		edgeIndexCount = result.edges?.indexCount ?: 0

		textVertexBuffer = result.text?.buffer
		textIndexCount = result.text?.indexCount ?: 0

		// Assign new screen-space buffers and counts
		screenFaceVertexBuffer = screenResult.faces?.buffer
		screenFaceIndexCount = screenResult.faces?.indexCount ?: 0

		screenEdgeVertexBuffer = screenResult.edges?.buffer
		screenEdgeIndexCount = screenResult.edges?.indexCount ?: 0

		screenTextVertexBuffer = screenResult.text?.buffer
		screenTextIndexCount = screenResult.text?.indexCount ?: 0

		hasData = faceVertexBuffer != null || edgeVertexBuffer != null || textVertexBuffer != null
		hasScreenData = screenFaceVertexBuffer != null || screenEdgeVertexBuffer != null || screenTextVertexBuffer != null
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

	/** Check if this renderer has any screen-space data to render. */
	fun hasScreenData(): Boolean = hasScreenData

	/** Clear all geometry data and release GPU resources. */
	fun clearData() {
		// Clear world-space buffers
		faceVertexBuffer?.close()
		edgeVertexBuffer?.close()
		textVertexBuffer?.close()
		faceVertexBuffer = null
		edgeVertexBuffer = null
		textVertexBuffer = null
		faceIndexCount = 0
		edgeIndexCount = 0
		textIndexCount = 0
		hasData = false

		// Clear screen-space buffers
		screenFaceVertexBuffer?.close()
		screenEdgeVertexBuffer?.close()
		screenTextVertexBuffer?.close()
		screenFaceVertexBuffer = null
		screenEdgeVertexBuffer = null
		screenTextVertexBuffer = null
		screenFaceIndexCount = 0
		screenEdgeIndexCount = 0
		screenTextIndexCount = 0
		hasScreenData = false
	}

	/** Check if this renderer has any data to render. */
	fun hasData(): Boolean = hasData

	/** Clean up all resources. */
	fun close() {
		clearData()
	}

	companion object {
		/** Helper to create a render pass targeting the main framebuffer. */
		fun createRenderPass(label: String): RenderPass? {
			return createRenderPass(label, useDepth = true)
		}

		/**
		 * Helper to create a render pass targeting the main framebuffer.
		 * @param label Debug label for the render pass
		 * @param useDepth Whether to attach the depth buffer for depth testing
		 */
		fun createRenderPass(label: String, useDepth: Boolean): RenderPass? {
			val framebuffer = mc.framebuffer ?: return null
			val depthView = if (useDepth) framebuffer.depthAttachmentView else null
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
	}
}
