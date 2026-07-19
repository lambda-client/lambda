
package com.minato.graphics.mc.renderer

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.event.events.RenderEvent
import com.minato.event.events.TickEvent
import com.minato.event.events.WorldEvent
import com.minato.event.listener.UnsafeListener.Companion.listenConcurrentlyUnsafe
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.graphics.RenderMain
import com.minato.graphics.mc.RegionRenderer
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.RenderDsl
import com.minato.module.Module
import com.minato.module.modules.client.Client
import com.minato.module.modules.client.FpsManager
import com.minato.module.modules.client.PerformanceOptimizer
import com.minato.util.world.FastVector
import com.minato.util.world.fastVectorOf
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
		owner.listenUnsafe<WorldEvent.BlockUpdate.Client> { event ->
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

		owner.listenUnsafe<WorldEvent.ChunkEvent.Load> { event -> event.chunk.chunkData.markDirty() }
		owner.listenUnsafe<WorldEvent.ChunkEvent.Unload> { chunkMap.remove(it.chunk.chunkKey)?.clearData() }
		owner.listenUnsafe<WorldEvent.Leave> { clear() }

		owner.listenConcurrentlyUnsafe<TickEvent.Pre> {
			if (pauseUpdates()) return@listenConcurrentlyUnsafe
			val queueSize = rebuildQueue.size
			val baseRate = Client.chunkRebuildsPerTick
			// Dynamic rate: giảm khi FPS thấp
			val effectiveRate = if (PerformanceOptimizer.isEnabled)
				FpsManager.getAdjustedChunkRate(baseRate) else baseRate
			val polls = minOf(effectiveRate, queueSize)
			val depth = depthTest()
			repeat(polls) { rebuildQueue.poll()?.rebuild(depth) }
		}

		owner.listenUnsafe<TickEvent.Pre> {
			val baseRate = Client.chunkUploadsPerTick
			val effectiveRate = if (PerformanceOptimizer.isEnabled)
				FpsManager.getAdjustedChunkRate(baseRate) else baseRate
			val polls = minOf(effectiveRate, uploadQueue.size)
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
