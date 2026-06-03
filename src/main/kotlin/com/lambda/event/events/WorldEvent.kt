/*
 * Copyright 2026 Lambda
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
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.chunk.WorldChunk
import java.util.*

/**
 * Represents various events that can occur within the world.
 *
 * This class encapsulates different types of world-related events,
 * which can be used for listening and responding to changes or
 * occurrences in the game world.
 */
sealed class WorldEvent {
    // ToDo: Add doc and determine if there's a better place for this event
    // Represents the player joining the world
    class Join : Event

    class Leave : Event

    // ToDo: Maybe create a network event seal with some s2c events
    sealed class Player {
        /**
         * Event triggered upon player joining
         */
        data class Join(
            val name: String,
            val uuid: UUID,
            val entry: PlayerListEntry,
        ) : Event

        /**
         * Event triggered upon player leaving
         */
        data class Leave(
            val name: String,
            val uuid: UUID,
            val entry: PlayerListEntry,
        ) : Event
    }

    /**
     * Represents an event specific to chunk operations within the world.
     *
     * Chunk events are triggered during two main operations:
     * - When a chunk is loaded into memory.
     * - When a chunk is unloaded from memory (not triggered upon leaving the world).
     *
     * These events can be used to listen for and respond to changes in the state
     * of chunks within the game world, providing contextual data for the operations.
     */
    sealed class ChunkEvent {
        /**
         * Event triggering upon chunk loading
         */
        data class Load(
            val chunk: WorldChunk,
        ) : Event

        /**
         * Event triggering upon chunk unloading
         * Does not trigger when leaving the world
         */
        data class Unload(
            val chunk: WorldChunk,
        ) : Event
    }

    /**
     * Represents events related to block updates in the world.
     *
     * This sealed class encapsulates different types of block update events,
     * distinguishing between client-side state changes and server-side updates.
     */
    sealed class BlockUpdate {
        /**
         * Represents a client side block state change event within the world.
         *
         * @property pos The position of the block within the world where the change occurred.
         * @property oldState The block state prior to the change.
         * @property newState The block state after the change.
         */
        data class Client(
            val pos: BlockPos,
            val oldState: BlockState,
            val newState: BlockState,
        ) : Event

        /**
         * Represents a server block update event in the world.
         *
         * @property pos The position of the block in the world.
         * @property newState The new state of the block after the update.
         */
        data class Server(
            val pos: BlockPos,
            val newState: BlockState,
        ) : ICancellable by Cancellable()
    }

    /**
     * Represents a collision event in the game world.
     *
     * This event is triggered when a collision is detected between an entity or object and a block.
     *
     * @property pos The position of the block involved in the collision.
     * @property state The current state of the block involved in the collision.
     * @property shape The voxel shape of the block involved in the collision, which can be modified during the event.
     */
    data class Collision(
        val pos: BlockPos,
        val state: BlockState,
        var shape: VoxelShape,
    ) : Event
}
