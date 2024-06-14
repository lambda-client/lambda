package com.lambda.graphics.renderer.esp

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.modules.client.RenderSettings
import com.lambda.threading.runGameBlocking
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.WorldChunk
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Any,
    private val update: EspRenderer.(BlockView, BlockPos) -> Boolean
) {
    private val rendererMap = ConcurrentHashMap<ChunkPos, EspChunk>()
    private val WorldChunk.renderer get() = rendererMap.getOrPut(pos) {
        EspChunk(this, this@ChunkedESP)
    }
    private var ticks = 0

    private val uploadPool = ConcurrentLinkedDeque<() -> Unit>()
    private val rebuildPool = ConcurrentLinkedDeque<EspChunk>()

    // i completely dont like to listen to enable events

    fun clear() {
        rendererMap.clear()
    }

    init {
        owner.concurrentListener<WorldEvent.BlockUpdate> { event ->
            world.getWorldChunk(event.pos).renderer.apply {
                queueRebuild()
                notifyNeighbors()
            }
        }

        owner.concurrentListener<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.notifyNeighbors()
        }

        owner.concurrentListener<WorldEvent.ChunkEvent.Unload> { event ->
            rendererMap.remove(event.chunk.pos)?.notifyNeighbors()
        }

        owner.concurrentListener<TickEvent.Pre> {
            if (++ticks % RenderSettings.updateFrequency == 0) {
                rendererMap.values
                    .filter { it.neighborsLoaded }
                    .forEach { it.rebuild() }
                ticks = 0
            }
        }

        owner.listener<TickEvent.Pre> {
            if (uploadPool.isEmpty()) return@listener

            uploadPool
                .take(RenderSettings.uploadsPerTick)
                .forEach { it() }
        }

        owner.listener<RenderEvent.World> {
            rendererMap.values.forEach {
                it.renderer?.render()
            }
        }
    }

    companion object {
        fun Any.newChunkedESP(
            filter: (BlockView, BlockPos) -> Boolean,
            painter: (BlockView, BlockPos) -> Pair<Color, Color>
        ) = ChunkedESP(this, filter, painter)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: ChunkedESP) {
        var renderer: EspRenderer? = null
        var outdated = true

        private val chunkOffsets = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
        val neighbors = chunkOffsets.map {
            ChunkPos(chunk.pos.x + it.first, chunk.pos.z + it.second)
        }.toTypedArray()
        val neighborsLoaded get() = neighbors.all { it.isLoaded() }

        fun ChunkPos.isLoaded() = chunk.world.chunkManager.isChunkLoaded(x, z)

        fun queueRebuild() {
            owner.rebuildPool.add(this)
        }

        fun notifyNeighbors() {
            neighbors.forEach {
                owner.rendererMap[it]?.queueRebuild()
            }
        }

        suspend fun rebuild() {
            outdated = false

            val newRenderer = runGameBlocking {
                EspRenderer()
            }

            iterateChunk { x, y, z ->
                draw(newRenderer, BlockPos(x, y, z))
            }

            val upload = {
                newRenderer.upload()
                renderer = newRenderer
            }

            when (RenderSettings.uploadScheduler) {
                RenderSettings.UploadScheduler.Instant -> {
                    runGameBlocking {
                        upload()
                    }
                }
                RenderSettings.UploadScheduler.Delayed -> {
                    owner.uploadPool.add(upload)
                }
            }
        }

        private fun draw(renderer: EspRenderer, x: Int, y: Int, z: Int) {
            if (!owner.update(chunk, blockPos)) return false
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