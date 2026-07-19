
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.EventFlow
import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable
import com.minato.util.ClientPacket
import com.minato.util.ServerPacket

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
        abstract val packet: ServerPacket

        /**
         * Represents the event triggered before a packet is sent.
         *
         * @param packet the packet that is about to be sent.
         */
        data class Pre(override val packet: ServerPacket) : Send(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is sent.
         *
         * @param packet the packet that has been sent.
         */
        data class Post(override val packet: ServerPacket) : Send(), Event
    }

    /**
     * Represents a [PacketEvent] that is triggered when a packet is received.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is received.
     */
    sealed class Receive {
        abstract val packet: ClientPacket

        /**
         * Represents the event triggered before a packet is received.
         *
         * @param packet the packet that is about to be received.
         */
        data class Pre(override val packet: ClientPacket) : Receive(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is received.
         *
         * @param packet the packet that has been received.
         */
        data class Post(override val packet: ClientPacket) : Receive(), Event
    }
}
