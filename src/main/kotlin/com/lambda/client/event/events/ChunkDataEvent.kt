package com.lambda.client.event.events

import com.lambda.client.event.Event
import net.minecraft.world.chunk.Chunk

/**
 * Event emitted when chunk data is read
 */
class ChunkDataEvent(val isFullChunk: Boolean, val chunk: Chunk): Event