

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
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.WorldChunk
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedRenderer(
	owner: Any,
	name: String,
	depthTest: SafeContext.() -> Boolean,
	private val update: RenderBuilder.(ClientWorld, FastVector) -> Unit
) : AbstractRenderer(name, depthTest) {
	private val chunkMap = ConcurrentHashMap<Long, ChunkData>()

	private val WorldChunk.chunkKey: Long
		get() = getChunkKey(pos.x, pos.z)

	private val WorldChunk.chunkData
		get() = chunkMap.getOrPut(chunkKey) { ChunkData(this) }

	private val rebuildQueue = ConcurrentLinkedDeque<ChunkData>()
	private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()

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
			val depth = depthTest()
			val queueSize = rebuildQueue.size
			val polls = minOf(StyleEditor.rebuildsPerTick, queueSize)
			repeat(polls) { rebuildQueue.poll()?.rebuild(depth) }
		}

		owner.listen<TickEvent.Pre> {
			val polls = minOf(StyleEditor.uploadsPerTick, uploadQueue.size)
			repeat(polls) { uploadQueue.poll()?.invoke() }
		}

		owner.listen<RenderEvent.RenderWorld> { render() }
		owner.listen<RenderEvent.RenderScreen> { renderScreen() }
	}

	private fun getChunkKey(chunkX: Int, chunkZ: Int) =
		(chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)

	fun rebuild() {
		rebuildQueue.clear()
		rebuildQueue.addAll(chunkMap.values)
	}

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
		val originX: Double = (chunk.pos.x shl 4).toDouble()
		val originY: Double = chunk.bottomY.toDouble()
		val originZ: Double = (chunk.pos.z shl 4).toDouble()

		val renderer = RegionRenderer()

		fun markDirty() {
			if (!rebuildQueue.contains(this)) rebuildQueue.add(this)
		}

		fun rebuild(depthTest: Boolean) {
			val chunkOriginVec = Vec3d(originX, originY, originZ)
			val scope = RenderBuilder(chunkOriginVec, depthTest = depthTest)
			
			for (x in chunk.pos.startX..chunk.pos.endX) {
				for (z in chunk.pos.startZ..chunk.pos.endZ) {
					for (y in chunk.bottomY..chunk.height) {
						update(scope, chunk.world as? ClientWorld ?: continue, fastVectorOf(x, y, z))
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
			update: RenderBuilder.(ClientWorld, FastVector) -> Unit
		) = ChunkedRenderer(this, name, depthTest, update)
	}
}
