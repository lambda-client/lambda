package com.lambda.graphics.renderer.esp

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.module.modules.client.RenderSettings
import com.lambda.threading.runGameBlocking
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import kotlinx.coroutines.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.WorldChunk
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Any,
    private val filter: (BlockView, BlockPos) -> Boolean,
    private val painter: (BlockView, BlockPos) -> Pair<Color, Color>
) {
    private val rendererMap = ConcurrentHashMap<ChunkPos, EspChunk>()
    private val WorldChunk.renderer get() = rendererMap.getOrPut(pos) {
        EspChunk(this, this@ChunkedESP)
    }
    private var ticks = 0

    private val uploadPool = ConcurrentLinkedDeque<() -> Unit>()
    private val rebuildPool = ConcurrentLinkedDeque<() -> Unit>()

    fun clear() {
        rendererMap.clear()
    }

    init {
        owner.concurrentListener<WorldEvent.BlockUpdate> { event ->
            world.getWorldChunk(event.pos).renderer.apply {
                markOutdated()
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
                    .filter { it.outdated && it.neighborsLoaded }
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

        fun markOutdated() {
            outdated = true
        }

        fun notifyNeighbors() {
            neighbors.forEach {
                owner.rendererMap[it]?.markOutdated()
            }
        }

        suspend fun rebuild() {
            outdated = false

            val newRenderer = runGameBlocking {
                EspRenderer()
            }

            iterateChunk { x, y, z ->
                checkAndDraw(newRenderer, BlockPos(x, y, z))
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

        private suspend fun <R: Any> runOnMainThreadAndWait(block: () -> R): R {
            var result: R? = null

            recordRenderCall {
                result = block()
            }

            while (result == null) delay(1)
            return result as R
            }

        private fun checkAndDraw(renderer: EspRenderer, blockPos: BlockPos): Boolean {
            if (!owner.filter(chunk, blockPos)) return false

            var sides = DirectionMask.ALL

            Direction.entries.forEach {
                if (!owner.filter(chunk.world, blockPos.add(it.vector))) return@forEach
                sides = sides.exclude(it.mask)
            }

            val (filledColor, outlineColor) = owner.painter(chunk, blockPos)
            renderer.build(Box(blockPos), filledColor, outlineColor, sides, DirectionMask.OutlineMode.AND)
            return true
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