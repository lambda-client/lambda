package com.lambda.graphics.renderer.esp

import com.lambda.core.Loadable
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.concurrentListener
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.chunk.WorldChunk
import java.util.concurrent.ConcurrentHashMap

object ChunkStorage : Loadable {
    val chunkMap = ChunkMap()

    init {
        concurrentListener<WorldEvent.ChunkEvent.Load> { event ->
            chunkMap[event.chunk.pos.toLong()] = event.chunk
        }

        concurrentListener<WorldEvent.ChunkEvent.Unload> { event ->
            chunkMap.remove(event.chunk.pos.toLong())
        }

        concurrentListener<ConnectionEvent.Disconnect> {
            chunkMap.clear()
        }
    }
}

typealias ChunkMap = ConcurrentHashMap<Long, WorldChunk>