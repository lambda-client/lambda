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
import com.lambda.event.EventFlow
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.util.ClientPacket
import com.lambda.util.ServerPacket

/**
 * An abstract class representing a [PacketEvent] in the [EventFlow].
 *
 * A [PacketEvent] is a type of [Event] that is triggered when a packet is sent or received.
 * It has two sealed subclasses: [Send] and [Receive],
 * which are triggered when a packet is sent and received, respectively.
 *
 * Each subclass has two further subclasses: `Pre` and `Post`,
 * which are triggered before and after the packet is sent or received.
 *
 * The [PacketEvent] class is designed to be extended by any class that needs to react to packet events.
 *
 * @see Send
 * @see Receive
 */
sealed class PacketEvent {
    /**
     * Represents a [PacketEvent] that is triggered when a packet is sent.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is sent.
     */
    sealed class Send {
        abstract val packet: ClientPacket

        /**
         * Represents the event triggered before a packet is sent.
         *
         * @param packet the packet that is about to be sent.
         */
        data class Pre(override val packet: ClientPacket) : Send(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is sent.
         *
         * @param packet the packet that has been sent.
         */
        data class Post(override val packet: ClientPacket) : Send(), Event
    }

    /**
     * Represents a [PacketEvent] that is triggered when a packet is received.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is received.
     */
    sealed class Receive {
        abstract val packet: ServerPacket

        /**
         * Represents the event triggered before a packet is received.
         *
         * @param packet the packet that is about to be received.
         */
        data class Pre(override val packet: ServerPacket) : Receive(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is received.
         *
         * @param packet the packet that has been received.
         */
        data class Post(override val packet: ServerPacket) : Receive(), Event
    }
}
