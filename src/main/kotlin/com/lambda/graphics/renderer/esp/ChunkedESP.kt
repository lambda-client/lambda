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

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.module.Module
import com.lambda.module.modules.client.StyleEditor
import com.lambda.threading.awaitMainThread
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ChunkedESP private constructor(
    owner: Module,
    private val update: ShapeBuilder.(World, FastVector) -> Unit
) {
    private val rendererMap = ConcurrentHashMap<Long, EspChunk>()
    private val WorldChunk.renderer
        get() = rendererMap.getOrPut(pos.toLong()) { EspChunk(this, this@ChunkedESP) }

    private val uploadQueue = ConcurrentLinkedDeque<() -> Unit>()
    private val rebuildQueue = ConcurrentLinkedDeque<EspChunk>()

    private var ticks = 0

    fun rebuild() {
        rebuildQueue.clear()
        rebuildQueue.addAll(rendererMap.values)
    }

    init {
        listen<WorldEvent.BlockUpdate.Client> { event ->
            world.getWorldChunk(event.pos).renderer.notifyChunks()
        }

        listen<WorldEvent.ChunkEvent.Load> { event ->
            event.chunk.renderer.notifyChunks()
        }

        listen<WorldEvent.ChunkEvent.Unload> {
            val pos = it.chunk.pos.toLong()
            rendererMap.remove(pos)?.notifyChunks()
        }

        owner.listenConcurrently<TickEvent.Pre> {
            val polls = minOf(StyleEditor.rebuildsPerTick, rebuildQueue.size)
            repeat(polls) { rebuildQueue.poll()?.rebuild() }
        }

        owner.listen<TickEvent.Pre>  {
            val polls = minOf(StyleEditor.uploadsPerTick, uploadQueue.size)
            repeat(polls) { uploadQueue.poll()?.invoke() }
        }

        owner.listen<RenderEvent.Render> {
            rendererMap.values.forEach { it.renderer.render() }
        }
    }

    companion object {
        fun Module.newChunkedESP(
            update: ShapeBuilder.(World, FastVector) -> Unit
        ) = ChunkedESP(this@newChunkedESP, update)
    }

    private class EspChunk(val chunk: WorldChunk, val owner: ChunkedESP) {
        var renderer = Treed(static = true)
        private val builder: ShapeBuilder
            get() = ShapeBuilder(renderer.faceBuilder, renderer.edgeBuilder)

        /*val neighbors = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
            .map { ChunkPos(chunk.pos.x + it.first, chunk.pos.z + it.second) }*/

        fun notifyChunks() {
            owner.rendererMap[chunk.pos.toLong()]?.let {
                if (!owner.rebuildQueue.contains(it))
                    owner.rebuildQueue.add(it)
            }
        }

        suspend fun rebuild() {
            renderer.clear()
            renderer = awaitMainThread { Treed(static = true) }

            for (x in chunk.pos.startX..chunk.pos.endX)
                for (z in chunk.pos.startZ..chunk.pos.endZ)
                    for (y in chunk.bottomY..chunk.height)
                        owner.update(builder, chunk.world, fastVectorOf(x, y, z))

            owner.uploadQueue.add { renderer.upload() }
        }
    }
}
