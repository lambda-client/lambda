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

package com.lambda.module.modules.network

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.*
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket

// ToDo: HUD info
object PacketLimiter : Module(
    name = "PacketLimiter",
    description = "Limits the amount of packets sent to the server",
    defaultTags = setOf(ModuleTag.NETWORK)
) {
    private var packetQueue = LimitedDecayQueue<PacketEvent.Send.Pre>(99, 1000)
    private val limit by setting("Limit", 99, 1..100, 1, "The maximum amount of packets to send per given time interval", unit = " packets")
        .onValueChange { _, to -> packetQueue.setMaxSize(to) }
    
    private val interval by setting("Duration", 1000L, 1L..1000L, 50L, "The interval / duration in milliseconds to limit packets for", unit = " ms")
        .onValueChange { _, to -> packetQueue.setDecayTime(to) }

    private val defaultIgnorePackets = setOf(
        CommonPongC2SPacket::class,
        PositionAndOnGround::class,
        Full::class,
        LookAndOnGround::class,
        OnGroundOnly::class,
        TeleportConfirmC2SPacket::class
    )
    private val ignorePackets by setting("Ignore Packets", defaultIgnorePackets.mapNotNull { it.simpleName }, "Packets to ignore when limiting")

    init {
        onEnable {
            packetQueue = LimitedDecayQueue(limit, interval)
        }

        listen<PacketEvent.Send.Pre>(Int.MAX_VALUE) {
            if (it.packet::class.simpleName in ignorePackets) return@listen

//            this@PacketLimiter.info("Packet sent: ${it.packet::class.simpleName} (${packetQueue.size} / $limit) ${Instant.now()}")
            if (packetQueue.add(it)) return@listen

            it.cancel()
            this@PacketLimiter.info("Packet limit reached, dropping packet: ${it.packet::class.simpleName} (${packetQueue.size} / $limit)")
        }
    }
}
