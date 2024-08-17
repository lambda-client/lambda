package com.lambda.util

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.ClientConnection
import net.minecraft.network.listener.ClientPacketListener
import net.minecraft.network.listener.ServerPacketListener
import net.minecraft.network.packet.Packet

object PacketUtils {
    /**
     * Sends a packet to the server without notifying the client.
     * It bypasses the mixins that would normally intercept the packet
     * and send it through the client's event bus.
     *
     * @param packet The packet to send.
     */
    fun ClientPlayNetworkHandler.sendPacketSilently(packet: ClientPacket) {
        if (!connection.isOpen) return
        if (connection.packetListener?.accepts(packet) == true)
            return // LOG.debug("Client tried to send client-bound packet {} to server ", packet)

        connection.send(packet, null, true)
        connection.packetsSentCounter++
    }

    /**
     * Handles a packet without notifying the client.
     * It bypasses the mixins that would normally intercept the packet
     * and send it through the client's event bus.
     *
     * @param packet The packet to handle.
     */
    fun ClientPlayNetworkHandler.handlePacketSilently(packet: ServerPacket) {
        if (!connection.isOpen) return
        if (connection.packetListener?.accepts(packet) == false)
            return // LOG.debug("Client tried to handle server-bound packet {}", packet)

        ClientConnection.handlePacket(packet, connection.packetListener)
        connection.packetsReceivedCounter++
    }
}

typealias ClientPacket = Packet<out ServerPacketListener>
typealias ServerPacket = Packet<out ClientPacketListener>
