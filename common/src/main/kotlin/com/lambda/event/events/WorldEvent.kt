/*
 * Copyright 2024 Lambda
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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.data.TrackedData
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
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

    class EntityUpdate(
        val entity: Entity,
        val data: TrackedData<*>,
    ) : WorldEvent(), ICancellable by Cancellable()

    class Collision(val pos: BlockPos, val state: BlockState, var shape: VoxelShape) : WorldEvent()
}
