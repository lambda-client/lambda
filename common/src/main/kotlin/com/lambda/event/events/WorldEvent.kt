package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.WorldChunk

abstract class WorldEvent : Event {
    abstract class ChunkEvent : WorldEvent() {
        abstract val world: ClientWorld
        abstract val chunk: WorldChunk

        class Load(
            override val world: ClientWorld,
            override val chunk: WorldChunk
        ) : ChunkEvent()

        class Unload(
            override val world: ClientWorld,
            override val chunk: WorldChunk
        ) : ChunkEvent()
    }

    class BlockUpdate(
        val pos: BlockPos,
        val state: BlockState,
        val flags: Int
    ) : WorldEvent(), ICancellable by Cancellable()

    class EntitySpawn(
        val entity: Entity
    ) : WorldEvent(), ICancellable by Cancellable()
}