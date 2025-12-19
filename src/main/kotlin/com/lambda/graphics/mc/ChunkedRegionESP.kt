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

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.graphics.esp.RegionESP
import com.lambda.graphics.esp.ShapeScope
import com.lambda.module.Module
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.runSafe
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Region-based chunked ESP system using MC 1.21.11's new render pipeline.
 *
 * This system:
 * - Uses region-relative coordinates for precision-safe rendering
 * - Maintains per-chunk geometry for efficient updates
 *
 * @param owner The module that owns this ESP system
 * @param name The name of the ESP system
 * @param depthTest Whether to use depth testing
 * @param update The update function called for each block position
 */
class ChunkedRegionESP(
	owner: Module,
	name: String,
	depthTest: Boolean = false,
	private val update: ShapeScope.(World, FastVector) -> Unit
) : RegionESP(name, depthTest) {
	private val chunkMap = ConcurrentHashMap<Long, RegionChunk>()

	private val WorldChunk.regionChunk
		get() = chunkMap.getOrPut(getRegionKey(pos.x shl 4, bottomY, pos.z shl 4)) {
			RegionChunk(this)
		}

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

	override fun clear() {
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
			val pos = getRegionKey(it.chunk.pos.x shl 4, it.chunk.bottomY, it.chunk.pos.z shl 4)
			chunkMap.remove(pos)?.close()
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

	/** Per-chunk rendering data. */
	private inner class RegionChunk(val chunk: WorldChunk) {
		val region = RenderRegion.forChunk(chunk.pos.x, chunk.pos.z, chunk.bottomY)
		private val key = getRegionKey(chunk.pos.x shl 4, chunk.bottomY, chunk.pos.z shl 4)

		private var isDirty = false

		fun markDirty() {
			isDirty = true
			if (!rebuildQueue.contains(this)) {
				rebuildQueue.add(this)
			}
		}

		fun rebuild() {
			if (!isDirty) return
			val scope = ShapeScope(region)

			for (x in chunk.pos.startX..chunk.pos.endX) {
				for (z in chunk.pos.startZ..chunk.pos.endZ) {
					for (y in chunk.bottomY..chunk.height) {
						update(scope, chunk.world, fastVectorOf(x, y, z))
					}
				}
			}

			uploadQueue.add {
				val renderer = renderers.getOrPut(key) { RegionRenderer(region) }
				renderer.upload(scope.builder.collector)
				isDirty = false
			}
		}

		fun close() {
			renderers.remove(key)?.close()
		}
	}
}
