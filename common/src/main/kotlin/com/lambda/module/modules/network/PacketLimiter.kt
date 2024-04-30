package com.lambda.module.modules.network

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.collections.LimitedDecayQueue
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket

// ToDo: HUD info
object PacketLimiter : Module(
    name = "PacketLimiter",
    description = "Limits the amount of packets sent to the server",
    defaultTags = setOf(ModuleTag.NETWORK)
) {
    private var packetQueue = LimitedDecayQueue<PacketEvent.Send.Pre>(99, 1000)
    private val limit by setting("Limit", 99, 1..100, 1, "The maximum amount of packets to send per second").apply {
        onValueChange { _, to ->
            packetQueue.setMaxSize(to)
        }
    }
    private val interval by setting("Duration", 1000L, 1L..1000L, 50L, "The duration in milliseconds to limit packets for").apply {
        onValueChange { _, to ->
            packetQueue.setInterval(to)
        }
    }

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

        listener<PacketEvent.Send.Pre>(Int.MAX_VALUE) {
            if (it.packet::class.simpleName in ignorePackets) return@listener

//            this@PacketLimiter.info("Packet sent: ${it.packet::class.simpleName} (${packetQueue.size} / $limit) ${Instant.now()}")
            if (packetQueue.add(it)) return@listener

            it.cancel()
            this@PacketLimiter.info("Packet limit reached, dropping packet: ${it.packet::class.simpleName} (${packetQueue.size} / $limit)")
        }
    }
}