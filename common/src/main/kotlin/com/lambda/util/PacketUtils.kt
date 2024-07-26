package com.lambda.util

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.packet.Packet

object PacketUtils {
    fun ClientPlayNetworkHandler.sendPacketSilently(packet: Packet<*>) {
        connection.send(packet, null, true)
    }
}