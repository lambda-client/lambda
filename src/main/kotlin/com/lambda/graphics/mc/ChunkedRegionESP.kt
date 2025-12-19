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
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.module.Module
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Region-based chunked ESP system using MC 1.21.11's new render pipeline.
 *
 * This system:
 * - Uses region-relative coordinates for precision-safe rendering
 * - Uses MC's RenderPass and BufferBuilder system
 * - Maintains per-chunk geometry for efficient updates
 * - Supports both depth-tested and through-wall rendering
 *
 * @param owner The module that owns this ESP system
 * @param throughWalls Whether to render through walls
 * @param update The update function called for each block position
 */
class ChunkedRegionESP private constructor(
	owner: Module,
	private val throughWalls: () -> Boolean,
	private val update: RegionShapeBuilder.(World, FastVector) -> Unit
) {
	private val chunkMap = ConcurrentHashMap<Long, RegionChunk>()

	private val WorldChunk.regionChunk
		get() = chunkMap.getOrPut(pos.toLong()) { RegionChunk(this, this@ChunkedRegionESP) }

	private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()
	private val rebuildQueue = ConcurrentLinkedDeque<RegionChunk>()

	/** Mark all tracked chunks for rebuild. */
	fun rebuild() {
		rebuildQueue.clear()
		rebuildQueue.addAll(chunkMap.values)
	}

	/**
	 * Load all currently loaded world chunks and mark them for rebuild. Call this when the module
	 * is enabled to populate initial chunks.
	 */
	fun rebuildAll() {
		runSafe {
			val chunksArray = world.chunkManager.chunks.chunks
			(0 until chunksArray.length()).forEach { i ->
				chunksArray.get(i)?.regionChunk?.markDirty()
			}
		}
	}

	/** Clear all chunk data. */
	fun clear() {
		chunkMap.values.forEach { it.close() }
		chunkMap.clear()
		rebuildQueue.clear()
		uploadQueue.clear()
	}

	init {
		owner.listen<WorldEvent.BlockUpdate.Client> { event ->
			val pos = event.pos
			world.getWorldChunk(pos)?.regionChunk?.markDirty()

			val xInChunk = pos.x and 15
			val zInChunk = pos.z and 15

			if (xInChunk == 0) world.getWorldChunk(pos.west())?.regionChunk?.markDirty()
			if (xInChunk == 15) world.getWorldChunk(pos.east())?.regionChunk?.markDirty()
			if (zInChunk == 0) world.getWorldChunk(pos.north())?.regionChunk?.markDirty()
			if (zInChunk == 15) world.getWorldChunk(pos.south())?.regionChunk?.markDirty()
		}

		owner.listen<WorldEvent.ChunkEvent.Load> { event -> event.chunk.regionChunk.markDirty() }

		owner.listen<WorldEvent.ChunkEvent.Unload> {
			val pos = it.chunk.pos.toLong()
			chunkMap.remove(pos)?.close()
		}

		owner.listenConcurrently<TickEvent.Pre> {
			val queueSize = rebuildQueue.size
			val polls = minOf(StyleEditor.rebuildsPerTick, queueSize)
			repeat(polls) {
				rebuildQueue.poll()?.rebuild()
			}
		}

		owner.listen<TickEvent.Pre> {
			val polls = minOf(StyleEditor.uploadsPerTick, uploadQueue.size)
			repeat(polls) {
				uploadQueue.poll()?.invoke()
			}
		}

		owner.listen<RenderEvent.Render> { render() }
	}

	/** Render all chunks with geometry. */
	private fun render() {
		val camera = mc.gameRenderer?.camera ?: return
		val cameraPos = camera.pos
		val framebuffer = mc.framebuffer ?: return

		val chunksWithData = chunkMap.values.filter { it.hasData }
		if (chunksWithData.isEmpty()) return

		val chunkTransforms =
			chunksWithData.map { chunk ->
				val offset = chunk.region.computeCameraRelativeOffset(cameraPos)
				val rotation = camera.rotation.conjugate(Quaternionf())
				val modelView = Matrix4f().rotation(rotation).translate(offset)
				val transforms = RenderSystem.getDynamicUniforms().write(
					modelView,
					Vector4f(1.0f, 1.0f, 1.0f, 1.0f), // color modulator
					Vector3f(
						0f,
						0f,
						0f
					), // model offset - ignored by vanilla shaders!
					Matrix4f() // texture matrix
				)
				chunk to transforms
			}

		RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				{ "Lambda ESP Faces" },
				framebuffer.colorAttachmentView,
				OptionalInt.empty(),
				framebuffer.depthAttachmentView,
				OptionalDouble.empty()
			)
			.use { renderPass ->
				val pipeline =
					if (throughWalls()) LambdaRenderPipelines.ESP_QUADS_THROUGH
					else LambdaRenderPipelines.ESP_QUADS
				renderPass.setPipeline(pipeline)
				RenderSystem.bindDefaultUniforms(renderPass)

				chunkTransforms.forEach { (chunk, transforms) ->
					renderPass.setUniform("DynamicTransforms", transforms)
					chunk.renderFaces(renderPass)
				}
			}

		RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				{ "Lambda ESP Edges" },
				framebuffer.colorAttachmentView,
				OptionalInt.empty(),
				framebuffer.depthAttachmentView,
				OptionalDouble.empty()
			)
			.use { renderPass ->
				val pipeline =
					if (throughWalls()) LambdaRenderPipelines.ESP_LINES_THROUGH
					else LambdaRenderPipelines.ESP_LINES
				renderPass.setPipeline(pipeline)
				RenderSystem.bindDefaultUniforms(renderPass)

				chunkTransforms.forEach { (chunk, transforms) ->
					renderPass.setUniform("DynamicTransforms", transforms)
					chunk.renderEdges(renderPass)
				}
			}
	}

	companion object {
		/**
		 * Create a new chunked region ESP for a module.
		 *
		 * @param throughWalls Whether to render through walls
		 * @param update The update function called for each block position
		 */
		fun Module.newChunkedRegionESP(
			throughWalls: () -> Boolean = { false },
			update: RegionShapeBuilder.(World, FastVector) -> Unit
		) = ChunkedRegionESP(this@newChunkedRegionESP, throughWalls, update)
	}

	/** Per-chunk rendering data. */
	private inner class RegionChunk(val chunk: WorldChunk, val owner: ChunkedRegionESP) {
		val region = RenderRegion.forChunk(chunk.pos.x, chunk.pos.z, chunk.bottomY)

		private var faceBuffer: GpuBuffer? = null
		private var edgeBuffer: GpuBuffer? = null
		private var faceIndexCount = 0
		private var edgeIndexCount = 0

		var hasData = false
			private set

		private var isDirty = false

		fun markDirty() {
			isDirty = true
			if (!owner.rebuildQueue.contains(this)) {
				owner.rebuildQueue.add(this)
			}
		}

		/**
		 * Rebuild this chunk's geometry. Runs on a background thread - collects vertices into
		 * thread-safe collections.
		 */
		fun rebuild() {
			if (!isDirty) return
			val builder = RegionShapeBuilder(region)

			var blockCount = 0
			for (x in chunk.pos.startX..chunk.pos.endX) {
				for (z in chunk.pos.startZ..chunk.pos.endZ) {
					for (y in chunk.bottomY..chunk.height) {
						owner.update(builder, chunk.world, fastVectorOf(x, y, z))
						blockCount++
					}
				}
			}

			owner.uploadQueue.add { upload(builder.collector) }
		}

		/** Upload collected vertices to GPU. Must run on the main/render thread. */
		private fun upload(collector: RegionVertexCollector) {
			faceBuffer?.close()
			edgeBuffer?.close()
			val result = collector.upload()

			faceBuffer = result.faces?.buffer
			faceIndexCount = result.faces?.indexCount ?: 0

			edgeBuffer = result.edges?.buffer
			edgeIndexCount = result.edges?.indexCount ?: 0

			hasData = faceBuffer != null || edgeBuffer != null
			isDirty = false
		}

		fun renderFaces(renderPass: RenderPass) {
			val buffer = faceBuffer ?: return
			if (faceIndexCount == 0) return

			renderPass.setVertexBuffer(0, buffer)
			val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(faceIndexCount)

			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, faceIndexCount, 1)
		}

		fun renderEdges(renderPass: RenderPass) {
			val buffer = edgeBuffer ?: return
			if (edgeIndexCount == 0) return

			renderPass.setVertexBuffer(0, buffer)
			val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.LINES)
			val indexBuffer = shapeIndexBuffer.getIndexBuffer(edgeIndexCount)

			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
			renderPass.drawIndexed(0, 0, edgeIndexCount, 1)
		}

		fun close() {
			faceBuffer?.close()
			edgeBuffer?.close()
			faceBuffer = null
			edgeBuffer = null
			hasData = false
		}
	}
}
