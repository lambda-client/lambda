package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.event.events.PacketEvent.Receive
import com.lambda.event.events.PacketEvent.Send
import net.minecraft.network.listener.ClientPacketListener
import net.minecraft.network.listener.ServerPacketListener
import net.minecraft.network.packet.Packet

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
abstract class PacketEvent : Event {
    /**
     * Represents a [PacketEvent] that is triggered when a packet is sent.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is sent.
     */
    sealed class Send : PacketEvent() {
        /**
         * Represents the event triggered before a packet is sent.
         *
         * @param packet the packet that is about to be sent.
         */
        class Pre(val packet: Packet<out ServerPacketListener>) : Send(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is sent.
         *
         * @param packet the packet that has been sent.
         */
        class Post(val packet: Packet<out ServerPacketListener>) : Send()
    }

    /**
     * Represents a [PacketEvent] that is triggered when a packet is received.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is received.
     */
    sealed class Receive : PacketEvent() {
        /**
         * Represents the event triggered before a packet is received.
         *
         * @param packet the packet that is about to be received.
         */
        class Pre(val packet: Packet<out ClientPacketListener>) : Receive(), ICancellable by Cancellable()

        /**
         * Represents the event triggered after a packet is received.
         *
         * @param packet the packet that has been received.
         */
        class Post(val packet: Packet<out ClientPacketListener>) : Receive()
    }
}
