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
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.FontHandler
import com.lambda.graphics.text.SDFFontAtlas
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.mojang.blaze3d.buffers.GpuBufferSlice
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
	owner: Any,
	name: String,
	depthTest: SafeContext.() -> Boolean,
	private val update: RenderBuilder.(World, FastVector) -> Unit
) : AbstractRenderer(name, depthTest) {
	private val chunkMap = ConcurrentHashMap<Long, ChunkData>()

	private val WorldChunk.chunkKey: Long
		get() = getChunkKey(pos.x, pos.z)

	private val WorldChunk.chunkData
		get() = chunkMap.getOrPut(chunkKey) { ChunkData(this) }

	private val rebuildQueue = ConcurrentLinkedDeque<ChunkData>()
	private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()

	// Font atlas from the default font handler
	override val currentFontAtlas: SDFFontAtlas
		get() = FontHandler.getDefaultFont()

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
		owner.listen<WorldEvent.ChunkEvent.Unload> { chunkMap.remove(it.chunk.chunkKey)?.clearData() }

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

	private fun getChunkKey(chunkX: Int, chunkZ: Int) =
		(chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)

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
		chunkMap.values.forEach { it.clearData() }
		chunkMap.clear()
		rebuildQueue.clear()
		uploadQueue.clear()
	}

	/**
	 * Get renderer/transform pairs for all active chunks.
	 * Each chunk has its own renderer and per-chunk transform (chunk-origin to camera).
	 * Includes fresh glint TextureMat for world image animation.
	 */
	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return emptyList()

		val activeChunks = chunkMap.values.filter { it.renderer.hasData() }
		if (activeChunks.isEmpty()) return emptyList()

		val modelViewMatrix = RenderMain.modelViewMatrix
		
		// Pre-compute the glint matrix once for all chunks (same animation for all)
		val glintMatrix = RendererUtils.createGlintTransform(0.25f)

		return activeChunks.map { chunkData ->
			// Compute chunk-to-camera offset in double precision
			val offsetX = (chunkData.originX - cameraPos.x).toFloat()
			val offsetY = (chunkData.originY - cameraPos.y).toFloat()
			val offsetZ = (chunkData.originZ - cameraPos.z).toFloat()

			val modelView = Matrix4f(modelViewMatrix).m30(0f).m31(0f).m32(0f).translate(offsetX, offsetY, offsetZ)
			val dynamicTransform = RenderSystem.getDynamicUniforms()
				.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), glintMatrix)

			chunkData.renderer to dynamicTransform
		}
	}

	/**
	 * Get renderers for screen-space rendering.
	 * Returns all chunk renderers that have screen data.
	 */
	override fun getScreenRenderers() =
		chunkMap.values
			.filter { it.renderer.hasScreenData() }
			.map { it.renderer }

	/** Per-chunk data with its own renderer and origin. */
	private inner class ChunkData(val chunk: WorldChunk) {
		// Chunk origin in world coordinates
		val originX: Double = (chunk.pos.x shl 4).toDouble()
		val originY: Double = chunk.bottomY.toDouble()
		val originZ: Double = (chunk.pos.z shl 4).toDouble()

		// This chunk's own renderer
		val renderer = RegionRenderer()

		fun markDirty() {
			if (!rebuildQueue.contains(this)) rebuildQueue.add(this)
		}

		/**
		 * Rebuild geometry relative to chunk origin.
		 * Coordinates are stored as (worldPos - chunkOrigin).toFloat()
		 */
		fun rebuild() {
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

			uploadQueue.add { renderer.upload(scope.collector) }
		}

		fun clearData() = renderer.clearData()
	}

	companion object {
		fun Any.chunkedRenderer(
			name: String,
			depthTest: SafeContext.() -> Boolean = { false },
			update: RenderBuilder.(World, FastVector) -> Unit
		) = ChunkedRenderer(this, name, depthTest, update)
	}
}
