package com.lambda.graphics.renderer.esp

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.vertex.BufferUsage
import com.lambda.module.modules.client.RenderSettings
import com.lambda.threading.awaitMainThread
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.WorldView
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Any,
    private val update: ESPRenderer.(WorldView, Int, Int, Int) -> Unit
) {
    private val rendererMap = ConcurrentHashMap<Long, EspChunk>()
    private val WorldChunk.renderer get() = rendererMap.getOrPut(pos.toLong()) {
        EspChunk(this, this@ChunkedESP)
    }

    private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()
    private val rebuildQueue = ConcurrentLinkedDeque<EspChunk>()

    private var ticks = 0

    fun rebuild() {
        rebuildQueue.clear()
        rebuildQueue.addAll(rendererMap.values)
    }

    init {
        concurrentListener<WorldEvent.BlockUpdate> { event ->
            world.getWorldChunk(event.pos).renderer.notifyChunks()
        }

        concurrentListener<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.notifyChunks()
        }

        concurrentListener<WorldEvent.ChunkEvent.Unload> { event ->
            rendererMap.remove(event.chunk.pos.toLong())?.notifyChunks()
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
            update: ESPRenderer.(WorldView, Int, Int, Int) -> Unit
        ) = ChunkedESP(this, update)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: ChunkedESP) {
        var renderer: ESPRenderer? = null

        private val chunkOffsets = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)

        val neighbors = chunkOffsets.map {
            ChunkPos(chunk.pos.x + it.first, chunk.pos.z + it.second)
        }.toTypedArray()

        fun notifyChunks() {
            neighbors.forEach {
                owner.rendererMap[it.toLong()]?.let {
                    owner.rebuildQueue.apply {
                        if (!contains(it)) add(it)
                    }
                }
            }
        }

        suspend fun rebuild() {
            val newRenderer = awaitMainThread {
                ESPRenderer(BufferUsage.STATIC)
            }

            iterateChunk { x, y, z ->
                owner.update(newRenderer, chunk.world, x, y, z)
            }

            owner.uploadQueue.add {
                newRenderer.upload()
                renderer = newRenderer
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