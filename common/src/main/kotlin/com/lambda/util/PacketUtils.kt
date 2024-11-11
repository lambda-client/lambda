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
