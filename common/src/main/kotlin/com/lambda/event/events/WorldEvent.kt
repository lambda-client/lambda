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
import net.minecraft.entity.Entity
import net.minecraft.entity.data.TrackedData
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.chunk.WorldChunk

sealed class WorldEvent {
    sealed class ChunkEvent : Event {
        /**
         * Event triggering upon chunk loading
         */
        data class Load(
            val chunk: WorldChunk
        ) : Event

        /**
         * Event triggering upon chunk unloading
         * Does not trigger when leaving the world
         */
        data class Unload(
            val chunk: WorldChunk
        ) : Event
    }

    /**
     * Represents a block update in the world
     */
    sealed class BlockUpdate(
        val pos: BlockPos,
        val state: BlockState,
        val flags: Int,
        val maxUpdateDepth: Int,
    ) {
        class Pre(pos: BlockPos, state: BlockState, flags: Int, depth: Int) : BlockUpdate(pos, state, flags, depth), ICancellable by Cancellable()
        class Post(pos: BlockPos, state: BlockState, flags: Int, depth: Int) : BlockUpdate(pos, state, flags, depth), Event
    }

    /**
     * Represents an entity being added to the world
     */
    class EntitySpawn(
        val entity: Entity
    ) : ICancellable by Cancellable()

    /**
     * Triggered upon entity data modification
     */
    class EntityUpdate(
        val entity: Entity,
        val data: TrackedData<*>,
    ) : ICancellable by Cancellable()

    /**
     * Triggered upon player colliding with a block
     */
    data class Collision(
        val pos: BlockPos,
        val state: BlockState,
        var shape: VoxelShape
    ) : Event
}
