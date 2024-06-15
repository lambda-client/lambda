package com.lambda.graphics.renderer.esp

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.modules.client.RenderSettings
import com.lambda.threading.runGameBlocking
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.WorldView
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Any,
    private val update: EspRenderer.(WorldView, Int, Int, Int) -> Unit
) {
    private val rendererMap = ConcurrentHashMap<Long, EspChunk>()
    private val WorldChunk.renderer get() = rendererMap.getOrPut(pos.toLong()) {
        EspChunk(this, this@ChunkedESP)
    }
    private var ticks = 0

    private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()
    private val rebuildQueue = ConcurrentLinkedDeque<EspChunk>()

    // i completely dont like to listen to enable events

    fun rebuild() {
        rebuildQueue.clear()
        rebuildQueue.addAll(rendererMap.values)
    }

    init {
        concurrentListener<WorldEvent.BlockUpdate> { event ->
            world.getWorldChunk(event.pos).renderer.apply {
                queueRebuild()
                notifyNeighbors()
            }
        }

        concurrentListener<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.notifyNeighbors()
        }

        concurrentListener<WorldEvent.ChunkEvent.Unload> { event ->
            rendererMap.remove(event.chunk.pos.toLong())?.notifyNeighbors()
        }

        owner.concurrentListener<TickEvent.Pre> {
            if (++ticks % RenderSettings.updateFrequency == 0) {
                val polls = minOf(RenderSettings.rebuildsPerTick, rebuildQueue.size)

                repeat(polls) {
                    rebuildQueue.poll()?.rebuild()
                }
                ticks = 0
            }
        }

        owner.listener<TickEvent.Pre> {
            if (uploadQueue.isEmpty()) return@listener

            val polls = minOf(RenderSettings.uploadsPerTick, uploadQueue.size)

            repeat(polls) {
                uploadQueue.poll()?.invoke()
            }
        }

        owner.listener<RenderEvent.World> {
            rendererMap.values.forEach {
                it.renderer?.render()
            }
        }
    }

    companion object {
        fun Any.newChunkedESP(
            update: EspRenderer.(WorldView, Int, Int, Int) -> Unit
        ) = ChunkedESP(this, update)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: ChunkedESP) {
        var renderer: EspRenderer? = null

        private val chunkOffsets = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
        val neighbors = chunkOffsets.map {
            ChunkPos(chunk.pos.x + it.first, chunk.pos.z + it.second)
        }.toTypedArray()
        val neighborsLoaded get() = neighbors.all { it.isLoaded() }

        fun ChunkPos.isLoaded() = chunk.world.chunkManager.isChunkLoaded(x, z)

        fun queueRebuild() {
            if (owner.rebuildQueue.contains(this)) return
            owner.rebuildQueue.add(this)
        }

        fun notifyNeighbors() {
            neighbors.forEach {
                owner.rendererMap[it.toLong()]?.queueRebuild()
            }
        }

        suspend fun rebuild() {
            val newRenderer = runGameBlocking {
                EspRenderer()
            }

            iterateChunk { x, y, z ->
                owner.update(newRenderer, chunk.world, x, y, z)
            }

            val upload = {
                newRenderer.upload()
                renderer?.clear()
                renderer = newRenderer
            }

            when (RenderSettings.uploadScheduler) {
                RenderSettings.UploadScheduler.Instant -> {
                    runGameBlocking {
                        upload()
                    }
                }
                RenderSettings.UploadScheduler.Delayed -> {
                    owner.uploadQueue.add(upload)
                }
            }
        }

        private fun iterateChunk(block: (Int, Int, Int) -> Unit) = chunk.apply {
            for (x in pos.startX..pos.endX) {
                for (z in pos.startZ..pos.endZ) {
                    for (y in bottomY..topY) {
                        block(x, y, z)
                    }
                }
            }
        }
    }
}