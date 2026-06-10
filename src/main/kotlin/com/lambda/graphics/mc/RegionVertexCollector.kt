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

import com.lambda.graphics.outline.OutlineStyle
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.BufferAllocator
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

class RegionVertexCollector {
	val faceVertices = ConcurrentLinkedDeque<FaceVertex>()
	val edgeVertices = ConcurrentLinkedDeque<EdgeVertex>()
	val textVertices = ConcurrentLinkedDeque<TextVertex>()

	val faceVerticesOutlined = ConcurrentHashMap<Int, ConcurrentLinkedDeque<FaceVertex>>()
	val edgeVerticesOutlined = ConcurrentHashMap<Int, ConcurrentLinkedDeque<EdgeVertex>>()
	val textVerticesOutlined = ConcurrentHashMap<Int, ConcurrentLinkedDeque<TextVertex>>()
	val modelVerticesOutlined = ConcurrentHashMap<Int, ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>>()
	val worldImageVerticesOutlined = ConcurrentHashMap<Int, ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<WorldImageVertex>>>()

	val screenFaceVertices = ConcurrentLinkedDeque<ScreenFaceVertex>()
	val screenEdgeVertices = ConcurrentLinkedDeque<ScreenEdgeVertex>()
	val screenTextVertices = ConcurrentLinkedDeque<ScreenTextVertex>()

	val depthTestedCustomOutlines = mutableMapOf<Int, OutlineStyle>()
	val xrayCustomOutlines = mutableMapOf<Int, OutlineStyle>()

	companion object {
		private val nextCustomId = AtomicInteger(1_000_000)
	}

	data class FaceVertex(
		val x: Float, val y: Float, val z: Float,
		val r: Int, val g: Int, val b: Int, val a: Int
	)

	data class TextVertex(
		val localX: Float, val localY: Float, val layerType: Int,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val anchorX: Float, val anchorY: Float, val anchorZ: Float,
		val scale: Float,
		val billboardFlag: Float,
		val outlineWidth: Float = 0f,
		val glowRadius: Float = 0f,
		val shadowSoftness: Float = 0f,
		val threshold: Float = 0.5f
	)

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
		val dashLength: Float = 0f,
		val gapLength: Float = 0f,
		val dashOffset: Float = 0f,
		val animationSpeed: Float = 0f
	)

	data class ScreenFaceVertex(
		val x: Float, val y: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val layer: Float
	)

	data class ScreenEdgeVertex(
		val x: Float, val y: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val dx: Float, val dy: Float,
		val lineWidth: Float,
		val dashLength: Float = 0f,
		val gapLength: Float = 0f,
		val dashOffset: Float = 0f,
		val animationSpeed: Float = 0f,
		val layer: Float = 0f
	)

	data class ScreenTextVertex(
		val x: Float, val y: Float, val layerType: Int,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val outlineWidth: Float = 0f,
		val glowRadius: Float = 0f,
		val shadowSoftness: Float = 0f,
		val threshold: Float = 0.5f,
		val layer: Float = 0f
	)

	data class ScreenImageVertex(
		val x: Float, val y: Float,
		val u: Float, val v: Float,
		val r: Int, val g: Int, val b: Int, val a: Int,
		val overlayU: Float, val overlayV: Float,
		val hasOverlay: Float,
		val diffuseAmount: Float,
		val layer: Float
	)

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

	data class ImageBatchKey(
		val textureView: GpuTextureView,
		val useNearestFilter: Boolean
	)

	private val worldImageBatches = ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<WorldImageVertex>>()
	private val screenImageBatches = ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ScreenImageVertex>>()
	private val worldModelBatches = ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>()
	private val screenModelBatches = ConcurrentHashMap<ImageBatchKey, ConcurrentLinkedDeque<ModelVertex>>()

	fun addScreenImageVertices(texture: GpuTextureView, vertices: List<ScreenImageVertex>, useNearestFilter: Boolean = false) {
		val key = ImageBatchKey(texture, useNearestFilter)
		screenImageBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
	}

	fun addWorldImageVertices(
		texture: GpuTextureView, 
		vertices: List<WorldImageVertex>, 
		useNearestFilter: Boolean = false,
		outlineId: Int? = null
	) {
		val key = ImageBatchKey(texture, useNearestFilter)
		if (outlineId == null) worldImageBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		else {
			val idMap = worldImageVerticesOutlined.getOrPut(outlineId) { ConcurrentHashMap() }
			idMap.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		}
	}

	fun addModelVertices(
		texture: GpuTextureView, 
		vertices: List<ModelVertex>, 
		useNearestFilter: Boolean = false,
		outlineId: Int? = null
	) {
		val key = ImageBatchKey(texture, useNearestFilter)
		if (outlineId == null) worldModelBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		else {
			val idMap = modelVerticesOutlined.getOrPut(outlineId) { ConcurrentHashMap() }
			idMap.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
		}
	}

	fun addScreenModelVertices(texture: GpuTextureView, vertices: List<ModelVertex>, useNearestFilter: Boolean = false) {
		val key = ImageBatchKey(texture, useNearestFilter)
		screenModelBatches.getOrPut(key) { ConcurrentLinkedDeque() }.addAll(vertices)
	}

	fun addFaceVertex(x: Float, y: Float, z: Float, color: Color, outlineId: Int? = null) {
		val v = FaceVertex(x, y, z, color.red, color.green, color.blue, color.alpha)
		if (outlineId == null) faceVertices.add(v)
		else faceVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
	}

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
		if (outlineId == null) edgeVertices.add(v)
		else edgeVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
	}

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
		if (dashStyle == null) addEdgeVertex(x, y, z, color, nx, ny, nz, lineWidth, outlineId)
		else {
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
			if (outlineId == null) edgeVertices.add(v)
			else edgeVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(v)
		}
	}

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
		layerType: Int = 3
	) {
		val vertex = TextVertex(
			localX, localY, layerType, u, v, r, g, b, a,
			anchorX, anchorY, anchorZ, scale, if (billboard) 0f else 1f,
			outlineWidth, glowRadius, shadowSoftness, threshold
		)
		if (outlineId == null) textVertices.add(vertex)
		else textVerticesOutlined.getOrPut(outlineId) { ConcurrentLinkedDeque() }.add(vertex)
	}

	fun addScreenFaceVertex(x: Float, y: Float, color: Color, layer: Float) {
		screenFaceVertices.add(ScreenFaceVertex(x, y, color.red, color.green, color.blue, color.alpha, layer))
	}

	fun addScreenEdgeVertex(x: Float, y: Float, color: Color, dx: Float, dy: Float, lineWidth: Float, layer: Float) {
		screenEdgeVertices.add(ScreenEdgeVertex(x, y, color.red, color.green, color.blue, color.alpha, dx, dy, lineWidth, layer = layer))
	}

	fun addScreenEdgeVertex(
		x: Float, y: Float,
		color: Color,
		dx: Float, dy: Float,
		lineWidth: Float,
		dashStyle: LineDashStyle?,
		layer: Float
	) {
		if (dashStyle == null) addScreenEdgeVertex(x, y, color, dx, dy, lineWidth, layer)
		else {
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

	fun buildWorld(): BuiltWorldResult {
		val faces = buildFaces()
		val edges = buildEdges()
		val text = buildText()
		val models = buildModelBatches()
		val images = buildWorldImageBatches()
		
		val outlineIds = faceVerticesOutlined.keys + edgeVerticesOutlined.keys + textVerticesOutlined.keys + modelVerticesOutlined.keys + worldImageVerticesOutlined.keys
		val outlinedResults = outlineIds.associateWith { id ->
			BuiltOutlineResult(
				faces = buildOutlinedFaces(id),
				edges = buildOutlinedEdges(id),
				text = buildOutlinedText(id),
				models = buildOutlinedModelBatches(id),
				images = buildOutlinedWorldImageBatches(id)
			)
		}

		val allCustomStyles = depthTestedCustomOutlines + xrayCustomOutlines
		return BuiltWorldResult(faces, edges, text, models, images, outlinedResults, allCustomStyles)
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.PositionColorNormalLineWidthDash)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.NormalFloat).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz)
					}
				}
				builder.beginElement(LambdaVertexFormats.LineWidthFloat).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DashElement).let { p ->
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.PositionTextureColorAnchorSdf)
			vertices.forEach { v ->
				builder.vertex(v.localX, v.localY, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.AnchorElement).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ)
					}
				}
				builder.beginElement(LambdaVertexFormats.BillboardDataElement).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag)
					}
				}
				builder.beginElement(LambdaVertexFormats.SdfStyleElement).let { p ->
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

	private fun buildScreenFaces(): BuiltBatch? {
		if (screenFaceVertices.isEmpty()) return null
		val vertices = screenFaceVertices.toList()
		screenFaceVertices.clear()

		var result: BuiltBatch? = null
		BufferAllocator(vertices.size * 24).use { allocator ->
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.ScreenFaceFormat)
			vertices.forEach { v -> 
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.LayerElement).let { p ->
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.ScreenLineFormat)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, 0f).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.Direction2dElement).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.dx); MemoryUtil.memPutFloat(p + 4, v.dy) }
				}
				builder.beginElement(LambdaVertexFormats.LineWidthFloat).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DashElement).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.dashLength); MemoryUtil.memPutFloat(p + 4, v.gapLength); MemoryUtil.memPutFloat(p + 8, v.dashOffset); MemoryUtil.memPutFloat(p + 12, v.animationSpeed)
					}
				}
				builder.beginElement(LambdaVertexFormats.LayerElement).let { p ->
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.ScreenTextSdfFormat)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.SdfStyleElement).let { p ->
					if (p != -1L) {
						MemoryUtil.memPutFloat(p, v.outlineWidth); MemoryUtil.memPutFloat(p + 4, v.glowRadius); MemoryUtil.memPutFloat(p + 8, v.shadowSoftness); MemoryUtil.memPutFloat(p + 12, v.threshold)
					}
				}
				builder.beginElement(LambdaVertexFormats.LayerElement).let { p ->
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

	fun buildScreen(): BuiltScreenResult {
		val faces = buildScreenFaces()
		val edges = buildScreenEdges()
		val text = buildScreenText()
		val images = buildScreenImageBatches()
		val models = buildScreenModelBatches()
		return BuiltScreenResult(faces, edges, text, images, models)
	}

	private fun buildScreenImageBatches(): List<BuiltTextureBatch> {
		if (screenImageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<BuiltTextureBatch>()
		
		screenImageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach

			BufferAllocator(vertices.size * 44).use { allocator ->
				val builder = BufferBuilder(
					allocator,
					VertexFormat.DrawMode.QUADS,
					LambdaVertexFormats.ScreenImageFormat
				)

				vertices.forEach { v ->
					builder.vertex(v.x, v.y, 0f)
						.texture(v.u, v.v)
						.color(v.r, v.g, v.b, v.a)

					val overlayPointer = builder.beginElement(LambdaVertexFormats.OverlayUvElement)
					if (overlayPointer != -1L) {
						MemoryUtil.memPutFloat(overlayPointer, v.overlayU)
						MemoryUtil.memPutFloat(overlayPointer + 4L, v.overlayV)
						MemoryUtil.memPutFloat(overlayPointer + 8L, v.hasOverlay)
						MemoryUtil.memPutFloat(overlayPointer + 12L, v.diffuseAmount)
					}

					val layerPointer = builder.beginElement(LambdaVertexFormats.LayerElement)
					if (layerPointer != -1L) {
						MemoryUtil.memPutFloat(layerPointer, v.layer)
					}
				}

				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		screenImageBatches.clear()
		return results
	}

	fun buildWorldImageBatches(): List<BuiltTextureBatch> {
		if (worldImageBatches.isEmpty()) return emptyList()
		val results = mutableListOf<BuiltTextureBatch>()
		worldImageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			
			BufferAllocator(vertices.size * 64).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WorldImageFormat)
				vertices.forEach { v ->
					builder.vertex(v.localX, v.localY, 0f).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
					builder.beginElement(LambdaVertexFormats.AnchorElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
					}
					builder.beginElement(LambdaVertexFormats.BillboardDataElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
					}
					builder.beginElement(LambdaVertexFormats.OverlayUvElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldImageBatches.clear()
		return results
	}

	fun buildModelBatches(): List<BuiltTextureBatch> {
		if (worldModelBatches.isEmpty()) return emptyList()
		val results = mutableListOf<BuiltTextureBatch>()
		worldModelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WorldModelFormat)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OverlayUvElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { MemoryUtil.memPutShort(p, (v.light and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, ((v.light shr 16) and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LightDirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.Light1DirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NormalFloat).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EdgeDataElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldModelBatches.clear()
		return results
	}

	private fun buildScreenModelBatches(): List<BuiltTextureBatch> {
		if (screenModelBatches.isEmpty()) return emptyList()
		val results = mutableListOf<BuiltTextureBatch>()
		screenModelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach
			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WorldModelFormat)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OverlayUvElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { MemoryUtil.memPutShort(p, (v.light and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, ((v.light shr 16) and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LightDirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.Light1DirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NormalFloat).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EdgeDataElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.PositionColorNormalLineWidthDash)
			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.NormalFloat).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
				}
				builder.beginElement(LambdaVertexFormats.LineWidthFloat).let { p ->
					if (p != -1L) MemoryUtil.memPutFloat(p, v.lineWidth)
				}
				builder.beginElement(LambdaVertexFormats.DashElement).let { p ->
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
			val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.PositionTextureColorAnchorSdf)
			vertices.forEach { v ->
				builder.vertex(v.localX, v.localY, v.layerType.toFloat()).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
				builder.beginElement(LambdaVertexFormats.AnchorElement).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
				}
				builder.beginElement(LambdaVertexFormats.BillboardDataElement).let { p ->
					if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
				}
				builder.beginElement(LambdaVertexFormats.SdfStyleElement).let { p ->
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

	private fun buildOutlinedModelBatches(id: Int): List<BuiltTextureBatch> {
		val modelBatches = modelVerticesOutlined[id] ?: return emptyList()
		if (modelBatches.isEmpty()) return emptyList()

		val results = mutableListOf<BuiltTextureBatch>()
		modelBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach

			BufferAllocator(vertices.size * 88).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WorldModelFormat)
				vertices.forEach { v ->
					builder.vertex(v.x, v.y, v.z).color(v.r, v.g, v.b, v.a).texture(v.u, v.v)
					builder.beginElement(LambdaVertexFormats.OverlayUvElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
					builder.beginElement(VertexFormatElement.UV2).let { p ->
						if (p != -1L) { val l = v.light; MemoryUtil.memPutShort(p, (l and 0xFFFF).toShort()); MemoryUtil.memPutShort(p + 2, (l shr 16 and 0xFFFF).toShort()) }
					}
					builder.beginElement(LambdaVertexFormats.LightDirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.lx); MemoryUtil.memPutFloat(p + 4, v.ly); MemoryUtil.memPutFloat(p + 8, v.lz) }
					}
					builder.beginElement(LambdaVertexFormats.Light1DirElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.l1x); MemoryUtil.memPutFloat(p + 4, v.l1y); MemoryUtil.memPutFloat(p + 8, v.l1z) }
					}
					builder.beginElement(LambdaVertexFormats.NormalFloat).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.nx); MemoryUtil.memPutFloat(p + 4, v.ny); MemoryUtil.memPutFloat(p + 8, v.nz) }
					}
					builder.beginElement(LambdaVertexFormats.EdgeDataElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.edgeX); MemoryUtil.memPutFloat(p + 4, v.edgeY) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		modelVerticesOutlined.remove(id)
		return results
	}

	private fun buildOutlinedWorldImageBatches(id: Int): List<BuiltTextureBatch> {
		val imageBatches = worldImageVerticesOutlined[id] ?: return emptyList()
		if (imageBatches.isEmpty()) return emptyList()

		val results = mutableListOf<BuiltTextureBatch>()
		imageBatches.forEach { (batchKey, vertexDeque) ->
			val vertices = vertexDeque.toList()
			vertexDeque.clear()
			if (vertices.isEmpty()) return@forEach

			BufferAllocator(vertices.size * 60).use { allocator ->
				val builder = BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, LambdaVertexFormats.WorldImageFormat)
				vertices.forEach { v ->
					builder.vertex(v.localX, v.localY, 0f).texture(v.u, v.v).color(v.r, v.g, v.b, v.a)
					builder.beginElement(LambdaVertexFormats.AnchorElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.anchorX); MemoryUtil.memPutFloat(p + 4, v.anchorY); MemoryUtil.memPutFloat(p + 8, v.anchorZ) }
					}
					builder.beginElement(LambdaVertexFormats.BillboardDataElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.scale); MemoryUtil.memPutFloat(p + 4, v.billboardFlag) }
					}
					builder.beginElement(LambdaVertexFormats.OverlayUvElement).let { p ->
						if (p != -1L) { MemoryUtil.memPutFloat(p, v.overlayU); MemoryUtil.memPutFloat(p + 4, v.overlayV); MemoryUtil.memPutFloat(p + 8, v.hasOverlay); MemoryUtil.memPutFloat(p + 12, v.diffuseAmount) }
					}
				}
				builder.endNullable()?.let { built ->
					val copy = MemoryUtil.memAlloc(built.buffer.remaining())
					copy.put(built.buffer)
					copy.flip()
					results.add(BuiltTextureBatch(batchKey.textureView, copy, built.drawParameters.indexCount(), batchKey.useNearestFilter))
					built.close()
				}
			}
		}
		worldImageVerticesOutlined.remove(id)
		return results
	}

	fun registerCustomOutline(style: OutlineStyle, depthTest: Boolean = true): Int {
		val id = nextCustomId.getAndIncrement()
		if (depthTest) {
			depthTestedCustomOutlines[id] = style
		} else {
			xrayCustomOutlines[id] = style
		}
		return id
	}
}

data class BuiltBatch(val buffer: ByteBuffer, val indexCount: Int)
data class BuiltTextureBatch(val textureView: GpuTextureView, val buffer: ByteBuffer, val indexCount: Int, val useNearestFilter: Boolean = false)
data class BuiltWorldResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val models: List<BuiltTextureBatch>, val images: List<BuiltTextureBatch>, val outlined: Map<Int, BuiltOutlineResult>, val customOutlineStyles: Map<Int, OutlineStyle> = emptyMap())
data class BuiltScreenResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val images: List<BuiltTextureBatch>, val models: List<BuiltTextureBatch>)
data class BuiltOutlineResult(val faces: BuiltBatch?, val edges: BuiltBatch?, val text: BuiltBatch?, val models: List<BuiltTextureBatch>, val images: List<BuiltTextureBatch>)

data class TextureBatchResult(
	val textureView: GpuTextureView,
	val buffer: GpuBuffer,
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

data class BufferResult(val buffer: GpuBuffer?, val indexCount: Int)

