package com.lambda.common.event.events

import com.lambda.common.event.Cancellable
import com.lambda.common.event.Event
import com.lambda.common.event.ICancellable
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
