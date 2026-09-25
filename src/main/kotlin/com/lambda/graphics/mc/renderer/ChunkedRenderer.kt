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
import com.lambda.event.listener.UnsafeListener.Companion.listenConcurrentlyUnsafe
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.RenderDsl
import com.lambda.module.Module
import com.lambda.module.modules.client.Client
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.WorldChunk
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedRenderer(
	owner: Any,
	name: String,
	private val preChunkBuild: (ChunkPos) -> Unit = {},
	private val preChunkClear: (ChunkPos) -> Unit = {},
	depthTest: () -> Boolean,
	pauseUpdates: () -> Boolean,
	private val update: RenderBuilder.(FastVector) -> Unit
) : AbstractRenderer(name, depthTest) {
	private val chunkMap = ConcurrentHashMap<Long, ChunkData>()

	private val WorldChunk.chunkKey: Long
		get() = getChunkKey(pos.x, pos.z)

	private val WorldChunk.chunkData
		get() = chunkMap.getOrPut(chunkKey) { ChunkData(this) }

	private val rebuildQueue = ConcurrentLinkedDeque<ChunkData>()
	private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()

	init {
		owner.listenUnsafe<WorldEvent.BlockUpdate.Client>(alwaysListen = true) { event ->
			val pos = event.pos
			val world = mc.world ?: return@listenUnsafe
			world.getWorldChunk(pos)?.chunkData?.markDirty()

			val xInChunk = pos.x and 15
			val zInChunk = pos.z and 15

			if (xInChunk == 0) world.getWorldChunk(pos.west())?.chunkData?.markDirty()
			if (xInChunk == 15) world.getWorldChunk(pos.east())?.chunkData?.markDirty()
			if (zInChunk == 0) world.getWorldChunk(pos.north())?.chunkData?.markDirty()
			if (zInChunk == 15) world.getWorldChunk(pos.south())?.chunkData?.markDirty()
		}

		owner.listenUnsafe<WorldEvent.ChunkEvent.Load>(alwaysListen = true) { event -> event.chunk.chunkData.markDirty() }
		owner.listenUnsafe<WorldEvent.ChunkEvent.Unload>(alwaysListen = true) { chunkMap.remove(it.chunk.chunkKey)?.clearData() }
		owner.listenUnsafe<WorldEvent.Leave>(alwaysListen = true) { clear() }

		owner.listenConcurrentlyUnsafe<TickEvent.Pre> {
			if (pauseUpdates()) return@listenConcurrentlyUnsafe
			val queueSize = rebuildQueue.size
			val polls = minOf(Client.chunkRebuildsPerTick, queueSize)
			val depth = depthTest()
			repeat(polls) { rebuildQueue.poll()?.rebuild(depth) }
		}

		owner.listenUnsafe<TickEvent.Pre> {
			val polls = minOf(Client.chunkUploadsPerTick, uploadQueue.size)
			repeat(polls) { uploadQueue.poll()?.invoke() }
		}

		owner.listenUnsafe<RenderEvent.RenderWorld> { render() }
		owner.listenUnsafe<RenderEvent.RenderScreen> { renderScreen() }
	}

	private fun getChunkKey(chunkX: Int, chunkZ: Int) =
		(chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)

	context(safeContext: SafeContext)
	fun rebuildChunk(x: Int, z: Int) {
		safeContext.world.getChunk(x, z)?.chunkData?.markDirty()
	}

	fun rebuild() {
		rebuildQueue.clear()
		mc.world?.chunkManager?.chunks?.let { chunks ->
			val chunkCount = chunks.chunks.length()
			(0 until chunkCount).forEach { index ->
				val chunk = chunks.chunks.get(index) ?: return@forEach
				chunkMap.putIfAbsent(chunk.chunkKey, ChunkData(chunk))
			}
		}
		rebuildQueue.addAll(chunkMap.values)
	}

	fun clear() {
		chunkMap.values.forEach { it.clearData() }
		chunkMap.clear()
		rebuildQueue.clear()
		uploadQueue.clear()
	}

	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return emptyList()

		val activeChunks = chunkMap.values.filter { it.renderer.hasData() }
		if (activeChunks.isEmpty()) return emptyList()
		
		val glintMatrix = RendererUtils.createGlintTransform(0.25f)

		return activeChunks.map { chunkData ->
			val offsetX = (chunkData.originX - cameraPos.x).toFloat()
			val offsetY = (chunkData.originY - cameraPos.y).toFloat()
			val offsetZ = (chunkData.originZ - cameraPos.z).toFloat()

			val dynamicTransform = RenderSystem.getDynamicUniforms()
				.write(
					RenderMain.cameraRotationMatrix,
					Vector4f(1f, 1f, 1f, 1f),
					Vector3f(offsetX, offsetY, offsetZ),
					glintMatrix
				)

			chunkData.renderer to dynamicTransform
		}
	}

	override fun getScreenRenderers() =
		chunkMap.values
			.filter { it.renderer.hasScreenData() }
			.map { it.renderer }

	private inner class ChunkData(val chunk: WorldChunk) {
		val originX = (chunk.pos.x shl 4).toDouble()
		val originY = chunk.bottomY.toDouble()
		val originZ = (chunk.pos.z shl 4).toDouble()

		val renderer = RegionRenderer()

		fun markDirty() {
			if (!rebuildQueue.contains(this)) rebuildQueue.add(this)
		}

		fun rebuild(depthTest: Boolean) {
			val chunkOriginVec = Vec3d(originX, originY, originZ)
			val scope = RenderBuilder(chunkOriginVec, depthTest = depthTest)

			preChunkBuild(chunk.pos)

			(chunk.pos.startX..chunk.pos.endX).forEach { x ->
				(chunk.pos.startZ..chunk.pos.endZ).forEach { z ->
					(chunk.bottomY..chunk.height).forEach { y ->
						update(scope, fastVectorOf(x, y, z))
					}
				}
			}

			uploadQueue.add { renderer.upload(scope.collector) }
		}

		fun clearData() {
			preChunkClear(chunk.pos)
			renderer.clearData()
		}
	}

	companion object {
		@RenderDsl
		fun Any.chunkedRenderer(
			name: String,
			preChunkBuild: (ChunkPos) -> Unit = {},
			preChunkClear: (ChunkPos) -> Unit = {},
			depthTest: () -> Boolean = { false },
			pauseUpdates: () -> Boolean = { false },
			update: RenderBuilder.(FastVector) -> Unit
		) = ChunkedRenderer(this, name, preChunkBuild, preChunkClear, depthTest, pauseUpdates, update).also { renderer ->
			(this as? Module)?.let { module ->
				module.onEnable { renderer.rebuild() }
				module.onDisable { renderer.clear() }
			}
		}
	}
}
