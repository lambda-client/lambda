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

import com.lambda.graphics.outline.CapturedGeometry
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
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
	
	// Outlined versions of the world-space collections
	// We use a Map to group vertices by outline ID for efficient layered rendering
	val faceVerticesOutlined = java.util.concurrent.ConcurrentHashMap<Int, ConcurrentLinkedDeque<FaceVertex>>()
	val edgeVerticesOutlined = java.util.concurrent.ConcurrentHashMap<Int, ConcurrentLinkedDeque<EdgeVertex>>()
	val textVerticesOutlined = java.util.concurrent.ConcurrentHashMap<Int, ConcurrentLinkedDeque<TextVertex>>()
	val modelVerticesOutlined = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>>()
	val worldImageVerticesOutlined = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<WorldImageVertex>>>()

	// Screen-space vertex collections (No outline support yet as screen-space is already layered)
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
		val localX: Float, val localY: Float, val layerType: Int,
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
		val x: Float, val y: Float, val layerType: Int,
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
		val diffuseAmount: Float,
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
		val hasOverlay: Float,
		val diffuseAmount: Float
	)

	/**
	 * Model vertex data for generic 3D models.
	 * Uses WORLD_MODEL_FORMAT (position + color + UV0 + overlay + light + lightDir + light1Dir + normal + edgeData).
	 *
	 * @param x World X
	 * @param y World Y
	 * @param z World Z
	 * @param u Texture U
	 * @param v Texture V
	 * @param r Red
	 * @param g Green
	 * @param b Blue
	 * @param a Alpha
	 * @param overlayU Overlay U
	 * @param overlayV Overlay V
	 * @param hasOverlay 1.0 if overlay active
	 * @param diffuseAmount Amount of diffuse shading (0 = lightmap only, 1 = full diffuse)
	 * @param light Packed lightmap coordinates
	 * @param lx Primary light direction X (pre-transformed for ITEMS_FLAT)
	 * @param ly Primary light direction Y
	 * @param lz Primary light direction Z
	 * @param l1x Secondary/fill light direction X
	 * @param l1y Secondary/fill light direction Y
	 * @param l1z Secondary/fill light direction Z
	 * @param nx Normal X
	 * @param ny Normal Y
	 * @param nz Normal Z
	 * @param edgeX Edge coordinate for AA
	 * @param edgeY Edge coordinate for AA
	 */
	data class ModelVertex(
		val x: Float, val y: Float, val z: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val overlayU: Float, val overlayV: Float, val hasOverlay: Float,
		val diffuseAmount: Float,
		val light: Int,
		val lx: Float, val ly: Float, val lz: Float,
		val l1x: Float, val l1y: Float, val l1z: Float,
		val nx: Float, val ny: Float, val nz: Float,
		val edgeX: Float, val edgeY: Float
	)

	/**
	 * Key for image batches - combines texture and filter mode.
	 * Batches with the same texture but different filter modes are separate.
	 */
	data class ImageBatchKey(
		val textureView: GpuTextureView,
		val useNearestFilter: Boolean
	)

	// Image vertex collections - keyed by texture + filter mode for batching
	// Each unique key gets its own list of vertices, rendered as separate draw calls
	private val screenImageBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ScreenImageVertex>>()
	private val worldImageBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<WorldImageVertex>>()
	private val modelBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>()
	private val screenModelBatches = java.util.concurrent.ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>()

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
	 * @param outlineId Optional ID if these vertices should be rendered in the outline layer
	 */
	fun addWorldImageVertices(
		texture: GpuTextureView, 
		vertices: List<WorldImageVertex>, 
		useNearestFilter: Boolean = false,
		outlineId: Int? = null
	) {
		val key = ImageBatchKey(texture, useNearestFilter)
		if (outlineId == null) {
			worldImageBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		} else {
			val idMap = worldImageVerticesOutlined.getOrPut(outlineId) { java.util.concurrent.ConcurrentHashMap() }
			idMap.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		}
	}

	/**
	 * Add model vertices for a specific texture.
	 * @param texture The GPU texture view
	 * @param vertices The vertices to add
	 * @param useNearestFilter If true, use NEAREST filtering
	 * @param outlineId Optional ID if these vertices should be rendered in the outline layer
	 */
	fun addModelVertices(
		texture: GpuTextureView, 
		vertices: List<ModelVertex>, 
		useNearestFilter: Boolean = false,
		outlineId: Int? = null
	) {
		val key = ImageBatchKey(texture, useNearestFilter)
		if (outlineId == null) {
			modelBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		} else {
			val idMap = modelVerticesOutlined.getOrPut(outlineId) { java.util.concurrent.ConcurrentHashMap() }
			idMap.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		}
	}

	/**
	 * Add screen model vertices for a specific texture.
	 */
	fun addScreenModelVertices(texture: GpuTextureView, vertices: List<ModelVertex>, useNearestFilter: Boolean = false) {
		val key = ImageBatchKey(texture, useNearestFilter)
		screenModelBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
	}

	/** Add a face vertex. */
	fun addFaceVertex(x: Float, y: Float, z: Float, color: Color, outlineId: Int? = null) {
		val v = FaceVertex(x, y, z, color.red, color.green, color.blue, color.alpha)
		if (outlineId == null) {
			faceVertices.add(v)
		} else {
			faceVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
		}
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
		lineWidth: Float,
		outlineId: Int? = null
	) {
		val v = EdgeVertex(x, y, z, color.red, color.green, color.blue, color.alpha, nx, ny, nz, lineWidth)
		if (outlineId == null) {
			edgeVertices.add(v)
		} else {
			edgeVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
		}
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
		dashStyle: LineDashStyle?,
		outlineId: Int? = null
	) {
		if (dashStyle == null) {
			addEdgeVertex(x, y, z, color, nx, ny, nz, lineWidth, outlineId)
		} else {
			val v = EdgeVertex(
				x, y, z,
				color.red, color.green, color.blue, color.alpha,
				nx, ny, nz,
				lineWidth,
				dashStyle.dashLength,
				dashStyle.gapLength,
				dashStyle.offset,
				if (dashStyle.animated) dashStyle.animationSpeed else 0f
			)
			if (outlineId == null) {
				edgeVertices.add(v)
			} else {
				edgeVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
			}
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
	/**
	 * Add a billboard text vertex.
	 */
	fun addTextVertex(
		localX: Float, localY: Float, u: Float, v: Float,
		r: Int, g: Int, b: Int, a: Int,
		anchorX: Float, anchorY: Float, anchorZ: Float,
		scale: Float, billboard: Boolean,
		outlineWidth: Float = 0f,
		glowRadius: Float = 0f,
		shadowSoftness: Float = 0f,
		threshold: Float = 0.5f,
		outlineId: Int? = null,
		layerType: Int = 3 // Default to main text
	) {
		val vertex = TextVertex(
			localX, localY, layerType, u, v, r, g, b, a,
			anchorX, anchorY, anchorZ, scale, if (billboard) 0f else 1f,
			outlineWidth, glowRadius, shadowSoftness, threshold
		)
		if (outlineId == null) {
			textVertices.add(vertex)
		} else {
			textVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(vertex)
		}
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
	fun addScreenTextVertex(
		x: Float, y: Float, u: Float, v: Float,
		r: Int, g: Int, b: Int, a: Int,
		layer: Float,
		layerType: Int = 3,
		outlineWidth: Float = 0f,
		glowRadius: Float = 0f,
		shadowSoftness: Float = 0f,
		threshold: Float = 0.5f
	) {
		screenTextVertices.add(ScreenTextVertex(x, y, layerType, u, v, r, g, b, a, outlineWidth, glowRadius, shadowSoftness, threshold, layer))
	}


	/**
	 * Build collected data into ByteBuffers. Must be called on the main/render thread.
	 * 
	 * @return BuildResult containing raw ByteBuffers with index counts
	 */
	fun build(): BuildResult {
		val faces = buildFaces()
		val edges = buildEdges()
		val text = buildText()
		val models = buildModelBatches()
		val images = buildWorldImageBatches()
		
		val outlineIds = faceVerticesOutlined.keys + edgeVerticesOutlined.keys + textVerticesOutlined.keys + modelVerticesOutlined.keys + worldImageVerticesOutlined.keys
		val outlinedResults = outlineIds.associateWith { id ->
			OutlinedBuildResult(
				faces = buildOutlinedFaces(id),
				edges = buildOutlinedEdges(id),
				text = buildOutlinedText(id),
				models = buildOutlinedModelBatches(id),
				images = buildOutlinedWorldImageBatches(id)
			)
		}

		return BuildResult(faces, edges, text, models, images, outlinedResults)
	}



	private fun buildFaces(): BuiltBatch? {
		if (faceVertices.isEmpty()) return null
		val vertices = faceVertices.toList()
		faceVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 16).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
			vertices.forEach { v -> builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a) }
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildEdges(): BuiltBatch? {
		if (edgeVertices.isEmpty()) return null
		val vertices = edgeVertices.toList()
		edgeVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 48).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz)
					}
				}
				builder.beginElement(LambdaVertexFormats.LINE_WIDTH_FLOAT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DASH_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.dashLength); MemoryUtil.memPutFloat(p + 4, v.gapLength); MemoryUtil.memPutFloat(p + 8, v.dashOffset); MemoryUtil.memPutFloat(p + 12, v.animationSpeed)
					}
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildText(): BuiltBatch? {
		if (textVertices.isEmpty()) return null
		val vertices = textVertices.toList()
		textVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 64).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF)
			vertices.forEach { v ->
				builder.vertex(v.localX, v.localY, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ)
					}
				}
				builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag)
					}
				}
				builder.beginElement(LambdaVertexFormats.SDF_STYLE_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.outlineWidth); MemoryUtil.memPutFloat(p + 4, v.glowRadius); MemoryUtil.memPutFloat(p + 8, v.shadowSoftness); MemoryUtil.memPutFloat(p + 12, v.threshold)
					}
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	// ============================================================================
	// Screen-Space Upload Methods
	// ============================================================================

	private fun buildScreenFaces(): BuiltBatch? {
		if (screenFaceVertices.isEmpty()) return null
		val vertices = screenFaceVertices.toList()
		screenFaceVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 24).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.SCREEN_FACE_FORMAT)
			vertices.forEach { v -> 
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.layer)
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildScreenEdges(): BuiltBatch? {
		if (screenEdgeVertices.isEmpty()) return null
		val vertices = screenEdgeVertices.toList()
		screenEdgeVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 52).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.SCREEN_LINE_FORMAT)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.DIRECTION_2D_ELEMENT).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.dx); MemoryUtil.memPutFloat(p + 4, v.dy) }
				}
				builder.beginElement(LambdaVertexFormats.LINE_WIDTH_FLOAT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DASH_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.dashLength); MemoryUtil.memPutFloat(p + 4, v.gapLength); MemoryUtil.memPutFloat(p + 8, v.dashOffset); MemoryUtil.memPutFloat(p + 12, v.animationSpeed)
					}
				}
				builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.layer)
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildScreenText(): BuiltBatch? {
		if (screenTextVertices.isEmpty()) return null
		val vertices = screenTextVertices.toList()
		screenTextVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 48).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.SCREEN_TEXT_SDF_FORMAT)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.SDF_STYLE_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.outlineWidth); MemoryUtil.memPutFloat(p + 4, v.glowRadius); MemoryUtil.memPutFloat(p + 8, v.shadowSoftness); MemoryUtil.memPutFloat(p + 12, v.threshold)
					}
				}
				builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.layer)
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	/**
	 * Build screen-space data into ByteBuffers.
	 *
	 * @return ScreenBuildResult containing ByteBuffers
	 */
	fun buildScreen(): ScreenBuildResult {
		val faces = buildScreenFaces()
		val edges = buildScreenEdges()
		val text = buildScreenText()
		val images = buildScreenImageBatches()
		val models = buildScreenModelBatches()
		return ScreenBuildResult(faces, edges, text, images, models)
	}

	private fun buildScreenImageBatches(): List<TextureBuildBatch> {
		if (screenImageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<TextureBuildBatch>()
		
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

					// Write overlay UV data (overlayU, overlayV, hasOverlay, diffuseAmount)
					val overlayPointer = builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT)
					if (overlayPointer != -1L) {
						MemoryUtil.memPutFloat(overlayPointer, v.overlayU)
						MemoryUtil.memPutFloat(overlayPointer + 4L, v.overlayV)
						MemoryUtil.memPutFloat(overlayPointer + 8L, v.hasOverlay)
						MemoryUtil.memPutFloat(overlayPointer + 12L, v.diffuseAmount)
					}

					// Write layer for draw order
					val layerPointer = builder.beginElement(LambdaVertexFormats.LAYER_ELEMENT)
					if (layerPointer != -1L) {
						MemoryUtil.memPutFloat(layerPointer, v.layer)
					}
				}

				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		screenImageBatches.clear()
		return results
	}

	fun buildWorldImageBatches(): List<TextureBuildBatch> {
		if (worldImageBatches.isEmpty()) return emptyList()
		val results = mutableListOf<TextureBuildBatch>()
		worldImageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			
			BufferAllocator(vertices.size * 64).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WORLD_IMAGE_FORMAT)
				vertices.forEach { v ->
					builder.vertex(v.localX, v.localY, 0f).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
					builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
					}
					builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
					}
					builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldImageBatches.clear()
		return results
	}

	fun buildModelBatches(): List<TextureBuildBatch> {
		if (modelBatches.isEmpty()) return emptyList()
		val results = mutableListOf<TextureBuildBatch>()
		modelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WORLD_MODEL_FORMAT)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { MemoryUtil.memPutShort(p, (v.light and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, ((v.light shr 16) and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT1_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EDGE_DATA_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		modelBatches.clear()
		return results
	}

	private fun buildScreenModelBatches(): List<TextureBuildBatch> {
		if (screenModelBatches.isEmpty()) return emptyList()
		val results = mutableListOf<TextureBuildBatch>()
		screenModelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WORLD_MODEL_FORMAT)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { MemoryUtil.memPutShort(p, (v.light and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, ((v.light shr 16) and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT1_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EDGE_DATA_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		screenModelBatches.clear()
		return results
	}

	private fun buildOutlinedFaces(id: Int): BuiltBatch? {
		val vertices = faceVerticesOutlined[id] ?: return null
		if (vertices.isEmpty()) return null
		faceVerticesOutlined.remove(id)

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 16).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
			vertices.forEach { v -> builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a) }
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildOutlinedEdges(id: Int): BuiltBatch? {
		val vertices = edgeVerticesOutlined[id] ?: return null
		if (vertices.isEmpty()) return null
		edgeVerticesOutlined.remove(id)

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 48).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
				}
				builder.beginElement(LambdaVertexFormats.LINE_WIDTH_FLOAT).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DASH_ELEMENT).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.dashLength); MemoryUtil.memPutFloat(p + 4, v.gapLength); MemoryUtil.memPutFloat(p + 8, v.dashOffset); MemoryUtil.memPutFloat(p + 12, v.animationSpeed)
					}
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildOutlinedText(id: Int): BuiltBatch? {
		val vertices = textVerticesOutlined[id] ?: return null
		if (vertices.isEmpty()) return null
		textVerticesOutlined.remove(id)

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 64).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF)
			vertices.forEach { v ->
				builder.vertex(v.localX, v.localY, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
				}
				builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
				}
				builder.beginElement(LambdaVertexFormats.SDF_STYLE_ELEMENT).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.outlineWidth); MemoryUtil.memPutFloat(p + 4, v.glowRadius); MemoryUtil.memPutFloat(p + 8, v.shadowSoftness); MemoryUtil.memPutFloat(p + 12, v.threshold) }
				}
			}
			builder.endNullable()?.let { built ->
				val copy = MemoryUtil.memAlloc(built.buffer.remaining())
				copy.put(built.buffer)
				copy.flip()
				result = BuiltBatch(copy, built.drawParameters.indexCount())
				built.close()
			}
		}
		return result
	}

	private fun buildOutlinedModelBatches(id: Int): List<TextureBuildBatch> {
		val modelBatches = modelVerticesOutlined[id] ?: return emptyList()
		if (modelBatches.isEmpty()) return emptyList()

		val results = mutableListOf<TextureBuildBatch>()
		modelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach

			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WORLD_MODEL_FORMAT)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { val l = v.light; MemoryUtil.memPutShort(p, (l and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, (l shr 16 and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.LIGHT1_DIR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NORMAL_FLOAT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EDGE_DATA_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		modelVerticesOutlined.remove(id)
		return results
	}

	private fun buildOutlinedWorldImageBatches(id: Int): List<TextureBuildBatch> {
		val imageBatches = worldImageVerticesOutlined[id] ?: return emptyList()
		if (imageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<TextureBuildBatch>()
		imageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach

			BufferAllocator(vertices.size * 60).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WORLD_IMAGE_FORMAT)
				vertices.forEach { v ->
					builder.vertex(v.localX, v.localY, 0f).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
					builder.beginElement(LambdaVertexFormats.ANCHOR_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
					}
					builder.beginElement(LambdaVertexFormats.BILLBOARD_DATA_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
					}
					builder.beginElement(LambdaVertexFormats.OVERLAY_UV_ELEMENT).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(TextureBuildBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldImageVerticesOutlined.remove(id)
		return results
	}

}

data class BuiltBatch(val buffer: java.nio.ByteBuffer, val indexCount: Int)
data class TextureBuildBatch(val textureView: com.mojang.blaze3d.textures.GpuTextureView, val buffer: java.nio.ByteBuffer, val indexCount: Int, val useNearestFilter: Boolean = false)
data class OutlinedBuildResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val models: List<TextureBuildBatch>, val images: List<TextureBuildBatch>)
data class BuildResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val models: List<TextureBuildBatch>, val images: List<TextureBuildBatch>, val outlined: Map<Int, OutlinedBuildResult>)
data class ScreenBuildResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val images: List<TextureBuildBatch>, val models: List<TextureBuildBatch>)

data class TextureBatchResult(
	val textureView: com.mojang.blaze3d.textures.GpuTextureView,
	val buffer: com.mojang.blaze3d.buffers.GpuBuffer,
	val indexCount: Int,
	val useNearestFilter: Boolean = false
)

data class OutlinedBatchResult(
	val faces: BufferResult,
	val edges: BufferResult,
	val text: BufferResult,
	val models: List<TextureBatchResult>,
	val images: List<TextureBatchResult>
)

data class BufferResult(val buffer: com.mojang.blaze3d.buffers.GpuBuffer?, val indexCount: Int)

