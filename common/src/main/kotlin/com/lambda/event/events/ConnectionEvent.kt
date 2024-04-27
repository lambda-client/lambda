package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.network.listener.PacketListener
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent
import net.minecraft.text.Text

abstract class ConnectionEvent : Event {
    class Connect(
        val host: String,
        val port: Int,
        val listener: PacketListener,
        val intent: ConnectionIntent,
    ) : ConnectionEvent()

    class Disconnect(val reason: Text) : ConnectionEvent()
}
