package com.lambda.event.events

import com.lambda.event.Cancellable
import com.lambda.event.ICancellable
import com.lambda.event.Event
import net.minecraft.network.packet.Packet

abstract class PacketEvent : Event {
    abstract class Send : PacketEvent() {
        class Pre(val packet: Packet<*>) : Send(), ICancellable by Cancellable()
        class Post(val packet: Packet<*>) : Send()
    }

    abstract class Receive : PacketEvent() {
        class Pre(val packet: Packet<*>) : Receive(), ICancellable by Cancellable()
        class Post(val packet: Packet<*>) : Receive()
    }
}