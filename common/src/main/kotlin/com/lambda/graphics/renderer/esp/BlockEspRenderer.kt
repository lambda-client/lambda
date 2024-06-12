package com.lambda.graphics.renderer.esp

import com.lambda.event.Muteable
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import kotlinx.coroutines.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.WorldChunk
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class BlockEspRenderer private constructor(
    private val owner: Any,
    private val filter: (BlockView, BlockPos) -> Boolean,
    private val painter: (BlockView, BlockPos) -> Pair<Color, Color>
) {
    private val rendererMap = ConcurrentHashMap<WorldChunk, EspChunk>()
    private val WorldChunk.renderer get() = rendererMap.getOrPut(this) {
        EspChunk(this, this@BlockEspRenderer)
    }

    init {
        listener<WorldEvent.BlockUpdate> { event ->
            world.getWorldChunk(event.pos).renderer.update = true
        }

        listener<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.updateNeighbors()
        }

        listener<WorldEvent.ChunkEvent.Unload> { event ->
            rendererMap.remove(event.chunk)?.updateNeighbors()
        }

        owner.listener<RenderEvent.World> {
            rendererMap.values.forEach {
                it.renderer?.render()
            }
        }

        CoroutineScope(newSingleThreadExecutor().asCoroutineDispatcher()).launch {
            while (true) {
                delay(100)

                if ((owner as? Muteable)?.isMuted == true) continue
                rendererMap.values.forEach {
                    it.tick()
                    delay(1)
                }
            }
        }
    }

    companion object {
        fun Any.newEspRenderer(
            filter: (BlockView, BlockPos) -> Boolean,
            painter: (BlockView, BlockPos) -> Pair<Color, Color>
        ) = BlockEspRenderer(this, filter, painter)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: BlockEspRenderer) {
        private val scope = CoroutineScope(newSingleThreadExecutor().asCoroutineDispatcher())

        var renderer: EspRenderer? = null

        var update = true
        private val chunkOffsets = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)

        fun updateNeighbors() {
            scope.launch {
                chunkOffsets.forEach { ofs ->
                    chunk.world.chunkManager.getWorldChunk(
                        chunk.pos.x + ofs.first,
                        chunk.pos.z + ofs.second
                    )?.let {
                        owner.rendererMap[it]?.update = true
                    }
                }
            }
        }

        fun tick() {
            if (!update) return

            // Chunk could only be drawn when all neighbors are loaded
            if (chunkOffsets.count {
                    chunk.world.isChunkLoaded(
                        chunk.pos.x + it.first,
                        chunk.pos.z + it.second
                    )
                } < 4) return

            update = false

            scope.launch {
                val newRenderer = runOnMainThreadAndWait(::EspRenderer)

                iterateChunk { x, y, z ->
                    checkAndDraw(newRenderer, BlockPos(x, y, z))
                }

                runOnMainThreadAndWait {
                    newRenderer.upload()
                    renderer = newRenderer
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