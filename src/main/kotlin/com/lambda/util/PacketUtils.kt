/*
 * Copyright 2026 Lambda
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
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.listener.PacketListener
import net.minecraft.network.listener.ServerPlayPacketListener
import net.minecraft.network.packet.Packet

object PacketUtils {
    /**
     * Sends a packet through the regular packet pipeline
     */
    fun ClientPlayNetworkHandler.sendPacket(packetSupplier: () -> Packet<*>) = connection.send(packetSupplier())

    /**
     * Sends a packet to the server without notifying the client.
     * It bypasses the mixins that would normally intercept the packet
     * and send it through the client's event bus.
     */
    fun ClientPlayNetworkHandler.sendPacketSilently(packet: Packet<*>) {
        if (!connection.isOpen) return

        connection.send(packet, null, true)
        connection.packetsSentCounter++
    }

    /**
     * Handles a packet without notifying the client.
     * It bypasses the mixins that would normally intercept the packet
     * and send it through the client's event bus.
     */
    fun ClientPlayNetworkHandler.handlePacketSilently(packet: Packet<*>) {
        if (!connection.isOpen || connection.packetListener?.accepts(packet) != true) return

        @Suppress("UNCHECKED_CAST")
        (packet as Packet<PacketListener>).apply(connection.packetListener as PacketListener)
        connection.packetsReceivedCounter++
    }
}

typealias ClientPacket = Packet<out ClientPlayPacketListener>
typealias ServerPacket = Packet<out ServerPlayPacketListener>
