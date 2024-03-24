package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.EventFlow
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.event.events.PacketEvent.Receive
import com.lambda.event.events.PacketEvent.Send
import com.lambda.event.events.PacketEvent.Send.Post
import com.lambda.event.events.PacketEvent.Send.Pre
import net.minecraft.network.packet.Packet

/**
 * An abstract class representing a [PacketEvent] in the [EventFlow].
 *
 * A [PacketEvent] is a type of [Event] that is triggered when a packet is sent or received.
 * It has two subclasses: [Send] and [Receive],
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
     * Representing a [PacketEvent] that is triggered when a packet is sent.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is sent.
     */
    abstract class Send : PacketEvent() {
        class Pre(val packet: Packet<*>) : Send(), ICancellable by Cancellable()
        class Post(val packet: Packet<*>) : Send()
    }

    /**
     * Representing a `PacketEvent` that is triggered when a packet is received.
     * It has two subclasses: [Pre] and [Post], which are triggered before and after the packet is received.
     */
    abstract class Receive : PacketEvent() {
        class Pre(val packet: Packet<*>) : Receive(), ICancellable by Cancellable()
        class Post(val packet: Packet<*>) : Receive()
    }
}