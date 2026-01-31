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

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.BufferAllocator
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Thread-safe vertex collector for region-based rendering.
 *
 * Collects vertex data on background threads using thread-safe collections, then writes to MC's
 * BufferBuilder and uploads on the main thread.
 */
class RegionVertexCollector {
	val faceVertices = ConcurrentLinkedDeque<FaceVertex>()
	val edgeVertices = ConcurrentLinkedDeque<EdgeVertex>()
	val textVertices = ConcurrentLinkedDeque<TextVertex>()

	// Screen-space vertex collections
	val screenFaceVertices = ConcurrentLinkedDeque<ScreenFaceVertex>()
	val screenEdgeVertices = ConcurrentLinkedDeque<ScreenEdgeVertex>()
	val screenTextVertices = ConcurrentLinkedDeque<ScreenTextVertex>()

	/** Face vertex data (position + color). */
	data class FaceVertex(
		val x: Float, val y: Float, val z: Float,
		val r: Int, val g: Int, val b: Int, val a: Int
	)

	/**
	 * Text vertex data for SDF billboard text rendering.
	 * Uses POSITION_TEXTURE_COLOR_ANCHOR_SDF format for GPU-based billboarding with embedded style.
	 * 
	 * @param localX Local glyph offset X (before billboard transform)
	 * @param localY Local glyph offset Y (before billboard transform)
	 * @param u Texture U coordinate
	 * @param v Texture V coordinate
	 * @param r Red color component
	 * @param g Green color component
	 * @param b Blue color component
	 * @param a Alpha component (encodes layer type)
	 * @param anchorX Camera-relative anchor position X
	 * @param anchorY Camera-relative anchor position Y
	 * @param anchorZ Camera-relative anchor position Z
	 * @param scale Text scale
	 * @param billboardFlag 0 = billboard towards camera, non-zero = fixed rotation already applied
	 * @param outlineWidth SDF outline width (0 = no outline)
	 * @param glowRadius SDF glow radius (0 = no glow)
	 * @param shadowSoftness SDF shadow softness (0 = no shadow)
	 * @param threshold SDF edge threshold (default 0.5)
	 */
	data class TextVertex(
		val localX: Float, val localY: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val anchorX: Float, val anchorY: Float, val anchorZ: Float,
		val scale: Float,
		val billboardFlag: Float,
		// SDF style params (replaces SDFParams uniform)
		val outlineWidth: Float = 0f,
		val glowRadius: Float = 0f,
		val shadowSoftness: Float = 0f,
		val threshold: Float = 0.5f
	)

	/** Edge vertex data (position + color + normal + line width + dash style). */
	data class EdgeVertex(
		val x: Float,
		val y: Float,
		val z: Float,
		val r: Int,
		val g: Int,
		val b: Int,
		val a: Int,
		val nx: Float,
		val ny: Float,
		val nz: Float,
		val lineWidth: Float,
		// Dash style parameters (0 = solid line)
		val dashLength: Float = 0f,
		val gapLength: Float = 0f,
		val dashOffset: Float = 0f,
		val animationSpeed: Float = 0f  // 0 = no animation
	)

	// ============================================================================
	// Screen-Space Vertex Types
	// ============================================================================

	/** Screen-space face vertex data (2D position + color + layer). */
	data class ScreenFaceVertex(
		val x: Float, val y: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val layer: Float  // Depth for layering (higher = on top)
	)

	/** Screen-space edge vertex data (2D position + color + direction + width + dash + layer). */
	data class ScreenEdgeVertex(
		val x: Float, val y: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val dx: Float, val dy: Float,
		val lineWidth: Float,
		// Dash style parameters (0 = solid line)
		val dashLength: Float = 0f,
		val gapLength: Float = 0f,
		val dashOffset: Float = 0f,
		val animationSpeed: Float = 0f,
		val layer: Float = 0f  // Depth for layering (higher = on top)
	)

	/**
	 * Screen-space text vertex data with SDF style params and layer.
	 * Uses SCREEN_TEXT_SDF_FORMAT (position + UV + color + style + layer).
	 * 
	 * @param x Screen-space X position
	 * @param y Screen-space Y position
	 * @param u Texture U coordinate
	 * @param v Texture V coordinate
	 * @param r Red color component
	 * @param g Green color component
	 * @param b Blue color component
	 * @param a Alpha component (encodes layer type)
	 * @param outlineWidth SDF outline width (0 = no outline)
	 * @param glowRadius SDF glow radius (0 = no glow)
	 * @param shadowSoftness SDF shadow softness (0 = no shadow)
	 * @param threshold SDF edge threshold (default 0.5)
	 * @param layer Depth for layering (higher = on top)
	 */
	data class ScreenTextVertex(
		val x: Float, val y: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		// SDF style params (replaces SDFParams uniform)
		val outlineWidth: Float = 0f,
		val glowRadius: Float = 0f,
		val shadowSoftness: Float = 0f,
		val threshold: Float = 0.5f,
		val layer: Float = 0f  // Depth for layering (higher = on top)
	)

	// ============================================================================
	// Image Vertex Types
	// ============================================================================

	/**
	 * Screen-space image vertex data with overlay support.
	 * Uses SCREEN_IMAGE_FORMAT (position + UV + color + overlayUV + layer).
	 *
	 * @param x Screen-space X position
	 * @param y Screen-space Y position
	 * @param u Main texture U coordinate
	 * @param v Main texture V coordinate
	 * @param r Red tint component
	 * @param g Green tint component
	 * @param b Blue tint component
	 * @param a Alpha component
	 * @param overlayU Overlay texture U coordinate
	 * @param overlayV Overlay texture V coordinate
	 * @param hasOverlay 1.0 if overlay should be rendered, 0.0 otherwise
	 * @param layer Depth for layering (higher = on top)
	 */
	data class ScreenImageVertex(
		val x: Float, val y: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val overlayU: Float, val overlayV: Float,
		val hasOverlay: Float,
		val layer: Float
	)

	/**
	 * World-space image vertex data with billboard support and overlay.
	 * Uses WORLD_IMAGE_FORMAT (position + UV + color + anchor + billboard + overlayUV).
	 *
	 * @param localX Local offset X (before billboard transform)
	 * @param localY Local offset Y (before billboard transform)
	 * @param u Main texture U coordinate
	 * @param v Main texture V coordinate
	 * @param r Red tint component
	 * @param g Green tint component
	 * @param b Blue tint component
	 * @param a Alpha component
	 * @param anchorX Camera-relative anchor X
	 * @param anchorY Camera-relative anchor Y
	 * @param anchorZ Camera-relative anchor Z
	 * @param scale Image scale
	 * @param billboardFlag 0 = billboard towards camera, non-zero = fixed rotation
	 * @param overlayU Overlay texture U coordinate
	 * @param overlayV Overlay texture V coordinate
	 * @param hasOverlay 1.0 if overlay should be rendered, 0.0 otherwise
	 */
	data class WorldImageVertex(
		val localX: Float, val localY: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val anchorX: Float, val anchorY: Float, val anchorZ: Float,
		val scale: Float,
		val billboardFlag: Float,
		val overlayU: Float, val overlayV: Float,
		val hasOverlay: Float
	)

	/**
	 * Key for image batches - combines texture and filter mode.
	 * Batches with the same texture but different filter modes are separate.
	 */
	data class ImageBatchKey(
		val textureView: com.mojang.blaze3d.textures.GpuTextureView,
		val useNearestFilter: Boolean
	)

	// Image vertex collections - keyed by texture + filter mode for batching
	// Each unique key gets its own list of vertices, rendered as separate draw calls
	private val screenImageBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ScreenImageVertex>>()
	private val worldImageBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<WorldImageVertex>>()

	/**
	 * Add screen image vertices for a specific texture.
	 * @param texture The GPU texture view
	 * @param vertices The vertices to add
	 * @param useNearestFilter If true, use NEAREST filtering for pixel-perfect rendering
	 */
	fun addScreenImageVertices(texture: GpuTextureView, vertices: List<ScreenImageVertex>, useNearestFilter: Boolean = false) {
		val key = ImageBatchKey(texture, useNearestFilter)
		screenImageBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
	}

	/**
	 * Add world image vertices for a specific texture.
	 * @param texture The GPU texture view
	 * @param vertices The vertices to add
	 * @param useNearestFilter If true, use NEAREST filtering for pixel-perfect rendering
	 */
	fun addWorldImageVertices(texture: GpuTextureView, vertices: List<WorldImageVertex>, useNearestFilter: Boolean = false) {
		val key = ImageBatchKey(texture, useNearestFilter)
		worldImageBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
	}

	/** Add a face vertex. */
	fun addFaceVertex(x: Float, y: Float, z: Float, color: Color) {
		faceVertices.add(FaceVertex(x, y, z, color.red, color.green, color.blue, color.alpha))
	}

	/** Add an edge vertex (solid line). */
	fun addEdgeVertex(
		x: Float,
		y: Float,
		z: Float,
		color: Color,
		nx: Float,
		ny: Float,
		nz: Float,
		lineWidth: Float
	) {
		edgeVertices.add(
			EdgeVertex(x, y, z, color.red, color.green, color.blue, color.alpha, nx, ny, nz, lineWidth)
		)
	}

	/** Add an edge vertex with dash style. */
	fun addEdgeVertex(
		x: Float,
		y: Float,
		z: Float,
		color: Color,
		nx: Float,
		ny: Float,
		nz: Float,
		lineWidth: Float,
		dashStyle: LineDashStyle?
	) {
		if (dashStyle == null) {
			addEdgeVertex(x, y, z, color, nx, ny, nz, lineWidth)
		} else {
			edgeVertices.add(
				EdgeVertex(
					x, y, z,
					color.red, color.green, color.blue, color.alpha,
					nx, ny, nz,
					lineWidth,
					dashStyle.dashLength,
					dashStyle.gapLength,
					dashStyle.offset,
					if (dashStyle.animated) dashStyle.animationSpeed else 0f
				)
			)
		}
	}

	/**
	 * Add a billboard text vertex.
	 * 
	 * @param localX Local glyph offset X (before billboard transform)
	 * @param localY Local glyph offset Y (before billboard transform)
	 * @param u Texture U coordinate
	 * @param v Texture V coordinate
	 * @param r Red color component
	 * @param g Green color component
	 * @param b Blue color component
	 * @param a Alpha component (encodes layer type)
	 * @param anchorX Camera-relative anchor X
	 * @param anchorY Camera-relative anchor Y
	 * @param anchorZ Camera-relative anchor Z
	 * @param scale Text scale
	 * @param billboard True = auto-billboard towards camera, False = fixed rotation (offset already transformed)
	 */
	fun addTextVertex(
		localX: Float, localY: Float,
		u: Float, v: Float,
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float, anchorZ: Float,
		scale: Float,
		billboard: Boolean
	) {
		textVertices.add(TextVertex(
			localX, localY, u, v, r, g, b, a,
			anchorX, anchorY, anchorZ, scale,
			if (billboard) 0f else 1f
		))
	}

	// ============================================================================
	// Screen-Space Vertex Add Methods
	// ============================================================================

	/** Add a screen-space face vertex with layer for draw order. */
	fun addScreenFaceVertex(x: Float, y: Float, color: Color, layer: Float) {
		screenFaceVertices.add(ScreenFaceVertex(x, y, color.red, color.green, color.blue, color.alpha, layer))
	}

	/** Add a screen-space edge vertex (solid line) with layer for draw order. */
	fun addScreenEdgeVertex(x: Float, y: Float, color: Color, dx: Float, dy: Float, lineWidth: Float, layer: Float) {
		screenEdgeVertices.add(ScreenEdgeVertex(x, y, color.red, color.green, color.blue, color.alpha, dx, dy, lineWidth, layer = layer))
	}

	/** Add a screen-space edge vertex with dash style and layer for draw order. */
	fun addScreenEdgeVertex(
		x: Float, y: Float,
		color: Color,
		dx: Float, dy: Float,
		lineWidth: Float,
		dashStyle: LineDashStyle?,
		layer: Float
	) {
		if (dashStyle == null) {
			addScreenEdgeVertex(x, y, color, dx, dy, lineWidth, layer)
		} else {
			screenEdgeVertices.add(
				ScreenEdgeVertex(
					x, y,
					color.red, color.green, color.blue, color.alpha,
					dx, dy,
					lineWidth,
					dashStyle.dashLength,
					dashStyle.gapLength,
					dashStyle.offset,
					if (dashStyle.animated) dashStyle.animationSpeed else 0f,
					layer
				)
			)
		}
	}

	/** Add a screen-space text vertex with layer for draw order. */
	fun addScreenTextVertex(x: Float, y: Float, u: Float, v: Float, r: Int, g: Int, b: Int, a: Int, layer: Float) {
		screenTextVertices.add(ScreenTextVertex(x, y, u, v, r, g, b, a, layer = layer))
	}

	/**
	 * Upload collected data to GPU buffers. Must be called on the main/render thread.
	 *
	 * @return UploadResult containing face, edge, and text buffers with index counts
	 */
	fun upload(): UploadResult {
		val faces = uploadFaces()
		val edges = uploadEdges()
		val text = uploadText()
		return UploadResult(faces, edges, text)
	}

	private fun uploadFaces(): BufferResult {
		if (faceVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = faceVertices.toList()
		faceVertices.clear()

		var result: BufferResult? = null
		BufferAllocator(vertices.size * 16).use { allocator ->
			val builder =
				BufferBuilder(
					allocator,
					VertexFormat.DrawMode.QUADS,
					VertexFormats.POSITION_COLOR
				)

			vertices.forEach { v -> builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a) }

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer =
					gpuDevice.createBuffer(
						{ "Lambda ESP Face Buffer" },
						GpuBuffer.USAGE_VERTEX,
						built.buffer
					)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	private fun uploadEdges(): BufferResult {
		if (edgeVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = edgeVertices.toList()
		edgeVertices.clear()

		var result: BufferResult? = null
		// Increased buffer size to accommodate the new dash vec3 (3 floats = 12 bytes extra)
		BufferAllocator(vertices.size * 48).use { allocator ->
			val builder =
				BufferBuilder(
					allocator,
					VertexFormat.DrawMode.QUADS,
					LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH
				)

			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z)
					.color(v.r, v.g, v.b, v.a)
				
				// Write Normal as 3 floats (NOT using .normal() which writes bytes)
				val normalPointer = builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT)
				if (normalPointer != -1L) {
					MemoryUtil.memPutFloat(normalPointer, v.nx)
					MemoryUtil.memPutFloat(normalPointer + 4L, v.ny)
					MemoryUtil.memPutFloat(normalPointer + 8L, v.nz)
				}
				
				// Write LineWidth as float
				val widthPointer = builder.beginElement(LambdaVertexFormats.LINE_WIDTH_FLOAT)
				if (widthPointer != -1L) {
					MemoryUtil.memPutFloat(widthPointer, v.lineWidth)
				}
				
				// Write dash data using access-widened beginElement (vec4)
				val dashPointer = builder.beginElement(LambdaVertexFormats.DASH_ELEMENT)
				if (dashPointer != -1L) {
					MemoryUtil.memPutFloat(dashPointer, v.dashLength)
					MemoryUtil.memPutFloat(dashPointer + 4L, v.gapLength)
					MemoryUtil.memPutFloat(dashPointer + 8L, v.dashOffset)
					MemoryUtil.memPutFloat(dashPointer + 12L, v.animationSpeed)
				}
			}

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer =
					gpuDevice.createBuffer(
						{ "Lambda ESP Edge Buffer" },
						GpuBuffer.USAGE_VERTEX,
						built.buffer
					)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	private fun uploadText(): BufferResult {
		if (textVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = textVertices.toList()
		textVertices.clear()

		var result: BufferResult? = null
		// POSITION_TEXTURE_COLOR_ANCHOR_SDF: 12 + 8 + 4 + 12 + 8 + 16 = 60 bytes per vertex
		BufferAllocator(vertices.size * 64).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF
			)

			vertices.forEach { v ->
				// Position stores local glyph offset (z unused, set to 0)
				builder.vertex(v.localX, v.localY, 0f)
					.texture(v.u, v.v)
					.color(v.r, v.g, v.b, v.a)
				
				// Write Anchor position (camera-relative world pos)
				val anchorPointer = builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT)
				if (anchorPointer != -1L) {
					MemoryUtil.memPutFloat(anchorPointer, v.anchorX)
					MemoryUtil.memPutFloat(anchorPointer + 4L, v.anchorY)
					MemoryUtil.memPutFloat(anchorPointer + 8L, v.anchorZ)
				}
				
				// Write Billboard data (scale, billboardFlag)
				val billboardPointer = builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT)
				if (billboardPointer != -1L) {
					MemoryUtil.memPutFloat(billboardPointer, v.scale)
					MemoryUtil.memPutFloat(billboardPointer + 4L, v.billboardFlag)
				}
				
				// Write SDF style params (outlineWidth, glowRadius, shadowSoftness, threshold)
				val sdfPointer = builder.beginElement(LambdaVertexFormats.SDF_STYLE_ELEMENT)
				if (sdfPointer != -1L) {
					MemoryUtil.memPutFloat(sdfPointer, v.outlineWidth)
					MemoryUtil.memPutFloat(sdfPointer + 4L, v.glowRadius)
					MemoryUtil.memPutFloat(sdfPointer + 8L, v.shadowSoftness)
					MemoryUtil.memPutFloat(sdfPointer + 12L, v.threshold)
				}
			}

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer = gpuDevice.createBuffer(
					{ "Lambda ESP Text Buffer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	// ============================================================================
	// Screen-Space Upload Methods
	// ============================================================================

	private fun uploadScreenFaces(): BufferResult {
		if (screenFaceVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = screenFaceVertices.toList()
		screenFaceVertices.clear()

		var result: BufferResult? = null
		// SCREEN_FACE_FORMAT: 12 + 4 + 4 = 20 bytes per vertex
		BufferAllocator(vertices.size * 24).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				LambdaVertexFormats.SCREEN_FACE_FORMAT
			)

			// For screen-space: use x, y, with z = 0, plus layer
			vertices.forEach { v -> 
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)
				
				// Write layer for draw order
				val layerPointer = builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT)
				if (layerPointer != -1L) {
					MemoryUtil.memPutFloat(layerPointer, v.layer)
				}
			}

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer = gpuDevice.createBuffer(
					{ "Lambda Screen Face Buffer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	private fun uploadScreenEdges(): BufferResult {
		if (screenEdgeVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = screenEdgeVertices.toList()
		screenEdgeVertices.clear()

		var result: BufferResult? = null
		// Position (12) + Color (4) + Direction (8) + Width (4) + Dash (16) + Layer (4) = 48 bytes
		BufferAllocator(vertices.size * 52).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				LambdaVertexFormats.SCREEN_LINE_FORMAT
			)

			vertices.forEach { v ->
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)

				// Write direction (for calculating perpendicular offset in shader)
				val dirPointer = builder.beginElement(LambdaVertexFormats.DIRECTION_2D_ELEMENT)
				if (dirPointer != -1L) {
					MemoryUtil.memPutFloat(dirPointer, v.dx)
					MemoryUtil.memPutFloat(dirPointer + 4L, v.dy)
				}

				// Write line width
				val widthPointer = builder.beginElement(LambdaVertexFormats.LINE_WIDTH_FLOAT)
				if (widthPointer != -1L) {
					MemoryUtil.memPutFloat(widthPointer, v.lineWidth)
				}

				// Write dash data
				val dashPointer = builder.beginElement(LambdaVertexFormats.DASH_ELEMENT)
				if (dashPointer != -1L) {
					MemoryUtil.memPutFloat(dashPointer, v.dashLength)
					MemoryUtil.memPutFloat(dashPointer + 4L, v.gapLength)
					MemoryUtil.memPutFloat(dashPointer + 8L, v.dashOffset)
					MemoryUtil.memPutFloat(dashPointer + 12L, v.animationSpeed)
				}

				// Write layer for draw order
				val layerPointer = builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT)
				if (layerPointer != -1L) {
					MemoryUtil.memPutFloat(layerPointer, v.layer)
				}
			}

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer = gpuDevice.createBuffer(
					{ "Lambda Screen Edge Buffer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	private fun uploadScreenText(): BufferResult {
		if (screenTextVertices.isEmpty()) return BufferResult(null, 0)

		val vertices = screenTextVertices.toList()
		screenTextVertices.clear()

		var result: BufferResult? = null
		// SCREEN_TEXT_SDF_FORMAT: 12 + 8 + 4 + 16 + 4 = 44 bytes per vertex
		BufferAllocator(vertices.size * 48).use { allocator ->
			val builder = BufferBuilder(
				allocator,
				VertexFormat.DrawMode.QUADS,
				LambdaVertexFormats.SCREEN_TEXT_SDF_FORMAT
			)

			// Screen text: position is already final screen coordinates
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, 0f)
					.texture(v.u, v.v)
					.color(v.r, v.g, v.b, v.a)
				
				// Write SDF style params (outlineWidth, glowRadius, shadowSoftness, threshold)
				val sdfPointer = builder.beginElement(LambdaVertexFormats.SDF_STYLE_ELEMENT)
				if (sdfPointer != -1L) {
					MemoryUtil.memPutFloat(sdfPointer, v.outlineWidth)
					MemoryUtil.memPutFloat(sdfPointer + 4L, v.glowRadius)
					MemoryUtil.memPutFloat(sdfPointer + 8L, v.shadowSoftness)
					MemoryUtil.memPutFloat(sdfPointer + 12L, v.threshold)
				}

				// Write layer for draw order
				val layerPointer = builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT)
				if (layerPointer != -1L) {
					MemoryUtil.memPutFloat(layerPointer, v.layer)
				}
			}

			builder.endNullable()?.let { built ->
				val gpuDevice = RenderSystem.getDevice()
				val buffer = gpuDevice.createBuffer(
					{ "Lambda Screen Text Buffer" },
					GpuBuffer.USAGE_VERTEX,
					built.buffer
				)
				result = BufferResult(buffer, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result ?: BufferResult(null, 0)
	}

	/**
	 * Upload screen-space data to GPU buffers.
	 *
	 * @return ScreenUploadResult containing screen-space face, edge, text, and image buffers
	 */
	fun uploadScreen(): ScreenUploadResult {
		val faces = uploadScreenFaces()
		val edges = uploadScreenEdges()
		val text = uploadScreenText()
		val images = uploadScreenImageBatches()
		return ScreenUploadResult(faces, edges, text, images)
	}

	/**
	 * Result for a single texture batch - buffer, index count, and filter mode.
	 */
	data class TextureBatchResult(
		val textureView: com.mojang.blaze3d.textures.GpuTextureView,
		val buffer: GpuBuffer,
		val indexCount: Int,
		val useNearestFilter: Boolean = false
	)

	private fun uploadScreenImageBatches(): List<TextureBatchResult> {
		if (screenImageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<TextureBatchResult>()
		
		screenImageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			
			// SCREEN_IMAGE_FORMAT: 12 + 8 + 4 + 12 + 4 = 40 bytes per vertex
			BufferAllocator(vertices.size * 44).use { allocator ->
				val builder = BufferBuilder(
					allocator,
					VertexFormat.DrawMode.QUADS,
					LambdaVertexFormats.SCREEN_IMAGE_FORMAT
				)

				vertices.forEach { v ->
					builder.vertex(v.x, v.y, 0f)
						.texture(v.u, v.v)
						.color(v.r, v.g, v.b, v.a)

					// Write overlay UV data (overlayU, overlayV, hasOverlay)
					val overlayPointer = builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT)
					if (overlayPointer != -1L) {
						MemoryUtil.memPutFloat(overlayPointer, v.overlayU)
						MemoryUtil.memPutFloat(overlayPointer + 4L, v.overlayV)
						MemoryUtil.memPutFloat(overlayPointer + 8L, v.hasOverlay)
					}

					// Write layer for draw order
					val layerPointer = builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT)
					if (layerPointer != -1L) {
						MemoryUtil.memPutFloat(layerPointer, v.layer)
					}
				}

				builder.endNullable()?.let { built ->
					val gpuDevice = RenderSystem.getDevice()
					val buffer = gpuDevice.createBuffer(
						{ "Lambda Screen Image Buffer" },
						GpuBuffer.USAGE_VERTEX,
						built.buffer
					)
					results.add(TextureBatchResult(batchKey.textureView, buffer, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		screenImageBatches.clear()
		return results
	}

	fun uploadWorldImageBatches(): List<TextureBatchResult> {
		if (worldImageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<TextureBatchResult>()
		
		worldImageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			
			// WORLD_IMAGE_FORMAT: 12 + 8 + 4 + 12 + 8 + 12 = 56 bytes per vertex
			BufferAllocator(vertices.size * 60).use { allocator ->
				val builder = BufferBuilder(
					allocator,
					VertexFormat.DrawMode.QUADS,
					LambdaVertexFormats.WORLD_IMAGE_FORMAT
				)

				vertices.forEach { v ->
					builder.vertex(v.localX, v.localY, 0f)
						.texture(v.u, v.v)
						.color(v.r, v.g, v.b, v.a)

					// Write Anchor position (camera-relative world pos)
					val anchorPointer = builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT)
					if (anchorPointer != -1L) {
						MemoryUtil.memPutFloat(anchorPointer, v.anchorX)
						MemoryUtil.memPutFloat(anchorPointer + 4L, v.anchorY)
						MemoryUtil.memPutFloat(anchorPointer + 8L, v.anchorZ)
					}

					// Write Billboard data (scale, billboardFlag)
					val billboardPointer = builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT)
					if (billboardPointer != -1L) {
						MemoryUtil.memPutFloat(billboardPointer, v.scale)
						MemoryUtil.memPutFloat(billboardPointer + 4L, v.billboardFlag)
					}

					// Write overlay UV data (overlayU, overlayV, hasOverlay)
					val overlayPointer = builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT)
					if (overlayPointer != -1L) {
						MemoryUtil.memPutFloat(overlayPointer, v.overlayU)
						MemoryUtil.memPutFloat(overlayPointer + 4L, v.overlayV)
						MemoryUtil.memPutFloat(overlayPointer + 8L, v.hasOverlay)
					}
				}

				builder.endNullable()?.let { built ->
					val gpuDevice = RenderSystem.getDevice()
					val buffer = gpuDevice.createBuffer(
						{ "Lambda World Image Buffer" },
						GpuBuffer.USAGE_VERTEX,
						built.buffer
					)
					results.add(TextureBatchResult(batchKey.textureView, buffer, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldImageBatches.clear()
		return results
	}

	data class BufferResult(val buffer: GpuBuffer?, val indexCount: Int)
	data class UploadResult(val faces: BufferResult?, val edges: BufferResult?, val text: BufferResult? = null, val images: List<TextureBatchResult> = emptyList())
	data class ScreenUploadResult(val faces: BufferResult?, val edges: BufferResult?, val text: BufferResult? = null, val images: List<TextureBatchResult> = emptyList())
}

