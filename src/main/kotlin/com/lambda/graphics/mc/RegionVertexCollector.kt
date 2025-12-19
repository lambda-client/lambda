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
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.BufferAllocator
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

	/** Face vertex data (position + color). */
	data class FaceVertex(
		val x: Float,
		val y: Float,
		val z: Float,
		val r: Int,
		val g: Int,
		val b: Int,
		val a: Int
	)

	/** Edge vertex data (position + color + normal + line width). */
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
		val lineWidth: Float
	)

	/** Add a face vertex. */
	fun addFaceVertex(x: Float, y: Float, z: Float, color: Color) {
		faceVertices.add(FaceVertex(x, y, z, color.red, color.green, color.blue, color.alpha))
	}

	/** Add an edge vertex. */
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

	/**
	 * Upload collected data to GPU buffers. Must be called on the main/render thread.
	 *
	 * @return Pair of (faceBuffer, edgeBuffer) and their index counts, or null if no data
	 */
	fun upload(): UploadResult {
		val faces = uploadFaces()
		val edges = uploadEdges()
		return UploadResult(faces, edges)
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
		BufferAllocator(vertices.size * 32).use { allocator ->
			val builder =
				BufferBuilder(
					allocator,
					VertexFormat.DrawMode.LINES,
					VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH
				)

			vertices.forEach { v ->
				builder.vertex(v.x, v.y, v.z)
					.color(v.r, v.g, v.b, v.a)
					.normal(v.nx, v.ny, v.nz)
					.lineWidth(v.lineWidth)
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

	data class BufferResult(val buffer: GpuBuffer?, val indexCount: Int)
	data class UploadResult(val faces: BufferResult?, val edges: BufferResult?)
}
