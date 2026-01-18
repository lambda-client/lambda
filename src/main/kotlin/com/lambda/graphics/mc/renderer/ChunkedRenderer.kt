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

package com.lambda.graphics.mc.renderer

import com.lambda.Lambda.mc
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.FontHandler
import com.lambda.module.Module
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Chunked ESP system using chunk-origin relative coordinates.
 *
 * This system:
 * - Stores geometry relative to chunk origin (stable, small floats)
 * - Only rebuilds when chunks are modified
 * - At render time, translates from chunk origin to camera-relative position
 *
 * @param owner The module that owns this ESP system
 * @param name The name of the ESP system
 * @param depthTest Whether to use depth testing
 * @param update The update function called for each block position
 */
class ChunkedRenderer(
	owner: Module,
	name: String,
	var depthTest: Boolean = false,
	private val update: RenderBuilder.(World, FastVector) -> Unit
) {
	private val chunkMap = ConcurrentHashMap<Long, ChunkData>()

	private val WorldChunk.chunkKey: Long
		get() = getChunkKey(pos.x, pos.z)

	private val WorldChunk.chunkData
		get() = chunkMap.getOrPut(chunkKey) { ChunkData(this) }

	private val rebuildQueue = ConcurrentLinkedDeque<ChunkData>()
	private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()

	private fun getChunkKey(chunkX: Int, chunkZ: Int): Long {
		return (chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)
	}

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
				chunksArray.get(i)?.chunkData?.markDirty()
			}
		}
	}

	fun clear() {
		chunkMap.values.forEach { it.close() }
		chunkMap.clear()
		rebuildQueue.clear()
		uploadQueue.clear()
	}

	fun close() {
		clear()
	}

	/**
	 * Render all chunks with camera-relative translation.
	 */
	fun render() {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return

		val activeChunks = chunkMap.values.filter { it.renderer.hasData() }
		if (activeChunks.isEmpty()) return

		val modelViewMatrix = RenderMain.modelViewMatrix

		// Pre-compute all transforms BEFORE starting render passes
		val chunkTransforms = activeChunks.map { chunkData ->
			// Compute chunk-to-camera offset in double precision
			val offsetX = (chunkData.originX - cameraPos.x).toFloat()
			val offsetY = (chunkData.originY - cameraPos.y).toFloat()
			val offsetZ = (chunkData.originZ - cameraPos.z).toFloat()

			val modelView = Matrix4f(modelViewMatrix).translate(offsetX, offsetY, offsetZ)
			val dynamicTransform = RenderSystem.getDynamicUniforms()
				.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f())

			chunkData to dynamicTransform
		}

		// Render Faces
		RegionRenderer.Companion.createRenderPass("ChunkedESP Faces", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getFacesPipeline(depthTest))
			RenderSystem.bindDefaultUniforms(pass)

			chunkTransforms.forEach { (chunkData, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				chunkData.renderer.renderFaces(pass)
			}
		}

		// Render Edges
		RegionRenderer.Companion.createRenderPass("ChunkedESP Edges", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getEdgesPipeline(depthTest))
			RenderSystem.bindDefaultUniforms(pass)

			chunkTransforms.forEach { (chunkData, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				chunkData.renderer.renderEdges(pass)
			}
		}

		// Render Text (for any chunks that have text data)
		val chunksWithText = chunkTransforms.filter { (chunkData, _) -> chunkData.renderer.hasTextData() }
		if (chunksWithText.isNotEmpty()) {
			// Use default font atlas for chunked text
			val atlas = FontHandler.getDefaultFont()
			if (!atlas.isUploaded) atlas.upload()
			val textureView = atlas.textureView
			val sampler = atlas.sampler
			if (textureView != null && sampler != null) {
				val sdfParams = RendererUtils.createSDFParamsBuffer()
				if (sdfParams != null) {
					RegionRenderer.Companion.createRenderPass("ChunkedESP Text", depthTest)?.use { pass ->
						pass.setPipeline(RendererUtils.getTextPipeline(depthTest))
						RenderSystem.bindDefaultUniforms(pass)
						pass.setUniform("SDFParams", sdfParams)
						pass.bindTexture("Sampler0", textureView, sampler)

						chunksWithText.forEach { (chunkData, transform) ->
							pass.setUniform("DynamicTransforms", transform)
							chunkData.renderer.renderText(pass)
						}
					}
					sdfParams.close()
				}
			}
		}
	}

	/**
	 * Render screen-space geometry for all chunks.
	 * Uses orthographic projection for 2D rendering.
	 */
	fun renderScreen() {
		val activeChunks = chunkMap.values.filter { it.renderer.hasScreenData() }
		if (activeChunks.isEmpty()) return

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()

			// Render Screen Faces
			RegionRenderer.Companion.createRenderPass("ChunkedESP Screen Faces", false)?.use { pass ->
				pass.setPipeline(RendererUtils.screenFacesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				activeChunks.forEach { chunkData ->
					chunkData.renderer.renderScreenFaces(pass)
				}
			}

			// Render Screen Edges
			RegionRenderer.Companion.createRenderPass("ChunkedESP Screen Edges", false)?.use { pass ->
				pass.setPipeline(RendererUtils.screenEdgesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				activeChunks.forEach { chunkData ->
					chunkData.renderer.renderScreenEdges(pass)
				}
			}

			// Render Screen Text
			val chunksWithText = activeChunks.filter { it.renderer.hasScreenTextData() }
			if (chunksWithText.isNotEmpty()) {
				val atlas = FontHandler.getDefaultFont()
				if (!atlas.isUploaded) atlas.upload()
				val textureView = atlas.textureView
				val sampler = atlas.sampler
				if (textureView != null && sampler != null) {
					val sdfParams = RendererUtils.createSDFParamsBuffer()
					if (sdfParams != null) {
						RegionRenderer.Companion.createRenderPass("ChunkedESP Screen Text", false)?.use { pass ->
							pass.setPipeline(RendererUtils.screenTextPipeline)
							RenderSystem.bindDefaultUniforms(pass)
							pass.setUniform("DynamicTransforms", dynamicTransform)
							pass.setUniform("SDFParams", sdfParams)
							pass.bindTexture("Sampler0", textureView, sampler)
							chunksWithText.forEach { chunkData ->
								chunkData.renderer.renderScreenText(pass)
							}
						}
						sdfParams.close()
					}
				}
			}
		}
	}

	companion object {
		fun Module.chunkedEsp(
			name: String,
			depthTest: Boolean = false,
			update: RenderBuilder.(World, FastVector) -> Unit
		): ChunkedRenderer {
			return ChunkedRenderer(this, name, depthTest, update)
		}
	}

	init {
		owner.listen<WorldEvent.BlockUpdate.Client> { event ->
			val pos = event.pos
			world.getWorldChunk(pos)?.chunkData?.markDirty()

			val xInChunk = pos.x and 15
			val zInChunk = pos.z and 15

			if (xInChunk == 0) world.getWorldChunk(pos.west())?.chunkData?.markDirty()
			if (xInChunk == 15) world.getWorldChunk(pos.east())?.chunkData?.markDirty()
			if (zInChunk == 0) world.getWorldChunk(pos.north())?.chunkData?.markDirty()
			if (zInChunk == 15) world.getWorldChunk(pos.south())?.chunkData?.markDirty()
		}

		owner.listen<WorldEvent.ChunkEvent.Load> { event -> event.chunk.chunkData.markDirty() }

		owner.listen<WorldEvent.ChunkEvent.Unload> {
			chunkMap.remove(it.chunk.chunkKey)?.close()
		}

		owner.listenConcurrently<TickEvent.Pre> {
			val queueSize = rebuildQueue.size
			val polls = minOf(StyleEditor.rebuildsPerTick, queueSize)
			repeat(polls) { rebuildQueue.poll()?.rebuild() }
		}

		owner.listen<TickEvent.Pre> {
			val polls = minOf(StyleEditor.uploadsPerTick, uploadQueue.size)
			repeat(polls) { uploadQueue.poll()?.invoke() }
		}

		owner.listen<RenderEvent.Render> { render() }
	}

	/** Per-chunk data with its own renderer and origin. */
	private inner class ChunkData(val chunk: WorldChunk) {
		// Chunk origin in world coordinates
		val originX: Double = (chunk.pos.x shl 4).toDouble()
		val originY: Double = chunk.bottomY.toDouble()
		val originZ: Double = (chunk.pos.z shl 4).toDouble()

		// This chunk's own renderer
		val renderer = RegionRenderer()

		private var isDirty = false

		fun markDirty() {
			isDirty = true
			if (!rebuildQueue.contains(this)) {
				rebuildQueue.add(this)
			}
		}

		/**
		 * Rebuild geometry relative to chunk origin.
		 * Coordinates are stored as (worldPos - chunkOrigin).toFloat()
		 */
		fun rebuild() {
			if (!isDirty) return

			// Use chunk origin as the "camera" position for relative coords
			val chunkOriginVec = Vec3d(originX, originY, originZ)
			val scope = RenderBuilder(chunkOriginVec)

			for (x in chunk.pos.startX..chunk.pos.endX) {
				for (z in chunk.pos.startZ..chunk.pos.endZ) {
					for (y in chunk.bottomY..chunk.height) {
						update(scope, chunk.world, fastVectorOf(x, y, z))
					}
				}
			}

			uploadQueue.add {
				renderer.upload(scope.collector)
				isDirty = false
			}
		}

		fun close() {
			renderer.close()
		}
	}
}
