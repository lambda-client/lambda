package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable
import net.minecraft.network.packet.Packet
import com.lambda.event.EventFlow

/**
 * An abstract class representing a [PacketEvent] in the [EventFlow].
 *
 * A [PacketEvent] is a type of [Event] that is triggered when a packet is sent or received.
 * It has two subclasses: [Send] and [Receive],
 * which are triggered when a packet is sent and received, respectively.
 *
 * Each subclass has two further subclasses: [Pre] and [Post],
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
