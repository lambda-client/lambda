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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.collections.LimitedDecayQueue
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.*
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket
import net.minecraft.text.Text
import kotlin.math.floor

// ToDo: HUD info
object PacketLimiter : Module(
    name = "PacketLimiter",
    description = "Limits the amount of packets sent to the server",
    defaultTags = setOf(ModuleTag.NETWORK)
) {
    private var packetQueue = LimitedDecayQueue<PacketEvent.Send.Pre>(999, 1000)
    private var clickPacketQueue = LimitedDecayQueue<Long>(500, 30000)
    private val limit by setting("Limit", 99, 1..1000, 1, "The maximum amount of packets to send per given time interval", unit = " packets")
        .onValueChange { _, to -> packetQueue.setSizeLimit(to) }

    private val interval by setting("Duration", 4000L, 1L..10000L, 50L, "The interval / duration in milliseconds to limit packets for", unit = " ms")
        .onValueChange { _, to -> packetQueue.setDecayTime(to) }

    private val defaultIgnorePackets = setOf(
        CommonPongC2SPacket::class,
        PositionAndOnGround::class,
        Full::class,
        LookAndOnGround::class,
        OnGroundOnly::class,
        TeleportConfirmC2SPacket::class
    )
    private val limitAllPackets by setting("Limit All", false, "Limit all send packets")
    private val ignorePackets by setting("Ignore Packets", defaultIgnorePackets.mapNotNull { it.simpleName }, "Packets to ignore when limiting") { limitAllPackets }
    private val limitClickPackets by setting("Clicks limit", true, "Limits the amount of click packets you can send to prevent kicks.")
    private val limitClickWindowSize by setting("Click limit window size", 4f, 0.1f..10.0f, 0.1f, "Click limit window size", unit = " s") {
        limitClickPackets
    }.onValueChange { _, to -> clickPacketQueue.setDecayTime((to * 1000).toLong()) }
    private val limitClickRate by setting("Click limit rate", 19.3f, 0.1f..40f, 0.1f, "Click limit rate", unit = " packets/sec") {
        limitClickPackets
    }.onValueChange { _, to -> clickPacketQueue.setSizeLimit((limitClickWindowSize * to).toInt()) }
    private val limitClickRender by setting("Render Limit in Container", true, "Render the amount of clicks remaining in the container screen") {
        limitClickPackets
    }

    private val clickPacketsWindowAmount: Int
        get() = floor(limitClickWindowSize * limitClickRate).toInt()
    private val clickPacketsRemaining: Int
        get() = clickPacketsWindowAmount - clickPacketQueue.size

    init {
        onEnable {
            packetQueue = LimitedDecayQueue(limit, interval)
        }

        listen<PacketEvent.Send.Pre>(Int.MAX_VALUE) {
            if (it.packet::class.simpleName in ignorePackets) return@listen

            if (limitClickPackets && it.packet is ClickSlotC2SPacket) {
                if (!canSendClickPackets(1)) {
                    it.cancel()
                    return@listen
                } else {
                    clickPacketQueue.add(System.currentTimeMillis())
                }
            }

//            this@PacketLimiter.info("Packet sent: ${it.packet::class.simpleName} (${packetQueue.size} / $limit) ${Instant.now()}")
            if (packetQueue.add(it)) return@listen

            it.cancel()
            this@PacketLimiter.info("Packet limit reached, dropping packet: ${it.packet::class.simpleName} (${packetQueue.size} / $limit)")
        }

        listen<PlayerEvent.SlotClick>{
            if (!limitClickPackets) return@listen
            if (!canSendClickPackets(1)) {
                it.cancel()
                return@listen
            }
        }

        listen<RenderEvent.GUI.Container> {
            if (!limitClickRender) return@listen
            val renderScreen: HandledScreen<*> = it.genericContainerScreen
            val context: DrawContext = it.drawContext
            val x = renderScreen.x
            val y = renderScreen.y

            RenderSystem.disableDepthTest()
            val remainingText = "Clicks Remaining: $clickPacketsRemaining"
            context.drawText(renderScreen.textRenderer, Text.literal(remainingText), x + renderScreen.backgroundWidth, y, 4210752, false)
            RenderSystem.enableDepthTest()
        }
    }

    fun canSendClickPackets(packets: Int) = clickPacketQueue.size + packets < clickPacketsWindowAmount
}
