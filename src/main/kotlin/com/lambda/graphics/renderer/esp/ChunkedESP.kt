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

package com.lambda.graphics.renderer.esp

import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.events.onStaticRender
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.module.modules.client.StyleEditor
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Any,
    private val update: ShapeBuilder.(World, FastVector) -> Unit
) {
    private val rendererMap = ConcurrentHashMap<Long, EspChunk>()
    private val WorldChunk.renderer
        get() = rendererMap.getOrPut(pos.toLong()) {
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
        listenConcurrently<WorldEvent.BlockUpdate.Client> { event ->
            world.getWorldChunk(event.pos).renderer.notifyChunks()
        }

        listenConcurrently<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.notifyChunks()
        }

        listenConcurrently<WorldEvent.ChunkEvent.Unload> { event ->
            rendererMap.remove(event.chunk.pos.toLong())?.notifyChunks()
        }

        owner.onStaticRender { builder ->
            if (++ticks % StyleEditor.updateFrequency == 0) {
                val polls = minOf(StyleEditor.rebuildsPerTick, rebuildQueue.size)

                repeat(polls) {
                    rebuildQueue.poll()?.rebuild(builder)
                }
                ticks = 0
            }
        }

        owner.listen<TickEvent.Pre> {
            if (uploadQueue.isEmpty()) return@listen

            val polls = minOf(StyleEditor.uploadsPerTick, uploadQueue.size)

            repeat(polls) {
                uploadQueue.poll()?.invoke()
            }
        }
    }

    companion object {
        fun Any.newChunkedESP(
            update: ShapeBuilder.(World, FastVector) -> Unit
        ) = ChunkedESP(this@newChunkedESP, update)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: ChunkedESP) {
        val neighbors = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1).map { ChunkPos(chunk.pos.x + it.first, chunk.pos.z + it.second) }

        fun notifyChunks() {
            neighbors.forEach {
                owner.rendererMap[it.toLong()]?.let {
                    owner.rebuildQueue.apply {
                        if (!contains(it)) add(it)
                    }
                }
            }
        }

        fun rebuild(builder: ShapeBuilder) =
            iterateChunk { owner.update(builder, chunk.world, it) }

        inline fun iterateChunk(crossinline block: (FastVector) -> Unit) = chunk.apply {
            for (x in pos.startX..pos.endX) {
                for (z in pos.startZ..pos.endZ) {
                    for (y in bottomY..height) {
                        block(fastVectorOf(x, y, z))
                    }
                }
            }
        }
    }
}
