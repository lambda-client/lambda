package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.ICancellable
import net.minecraft.network.Packet

abstract class PacketEvent(val packet: Packet<*>) : Event, ICancellable by Cancellable() {
    class Receive(packet: Packet<*>) : PacketEvent(packet)
    class PostReceive(packet: Packet<*>) : PacketEvent(packet)
    class Send(packet: Packet<*>) : PacketEvent(packet)
    class PostSend(packet: Packet<*>) : PacketEvent(packet)
}