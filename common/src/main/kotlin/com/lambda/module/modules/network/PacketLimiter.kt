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
import com.lambda.graphics.renderer.gui.FontRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.math.Vec2d
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket
import java.awt.Color

// ToDo: HUD info
object PacketLimiter : Module(
    name = "PacketLimiter",
    description = "Limits the amount of packets sent to the server",
    defaultTags = setOf(ModuleTag.NETWORK)
) {
    private val page by setting("Page", Page.General)

    private val limit by setting("Limit", 99, 1..1000, 1, "The maximum amount of packets to send per given time interval", unit = " packets") { page == Page.General }
        .onValueChange { _, to -> packetQueue.setSizeLimit(to) }

    private val interval by setting("Duration", 4000L, 1L..10000L, 50L, "The interval / duration in milliseconds to limit packets for", unit = " ms") { page == Page.General }
        .onValueChange { _, to -> packetQueue.setDecayTime(to) }

    private val defaultIgnorePackets = setOf(
        CommonPongC2SPacket::class,
        PositionAndOnGround::class,
        Full::class,
        LookAndOnGround::class,
        OnGroundOnly::class,
        TeleportConfirmC2SPacket::class
    )

    private val limitAllPackets by setting("Limit All", false, "Limit all send packets") { page == Page.General }
    private val ignorePackets by setting("Ignore Packets", defaultIgnorePackets.mapNotNull { it.simpleName }, "Packets to ignore when limiting") { page == Page.General && limitAllPackets }

    private val limitClicks by setting("Clicks Limit", true, "Limits the amount of click packets you can send to prevent kicks on certain servers.") { page == Page.Clicks }
    private val limitClickWindowSize by setting("Click Limit Window Size", 4.0, 0.1..10.0, 0.1, "Click limit window size", unit = " s") { page == Page.Clicks && limitClicks }
        .onValueChange { _, to -> clickPacketQueue.setDecayTime((to * 1000).toLong()) }
    private val limitClickRate by setting("Click Limit Rate", 19, 1..40, 1, "Click limit rate", unit = " packets/sec") { page == Page.Clicks && limitClicks }
        .onValueChange { _, to -> clickPacketQueue.setSizeLimit((limitClickWindowSize * to).toInt()) }
    private val limitClickRender by setting("Render Limit in Container", true, "Render the amount of clicks remaining in the container screen") { page == Page.Clicks && limitClicks }

    private var packetQueue = LimitedDecayQueue<PacketEvent.Send.Pre>(999, 1000)
    private var clickPacketQueue = LimitedDecayQueue<Unit>(500, 30000)

    private val clickPacketsWindowAmount: Double
        get() = limitClickWindowSize * limitClickRate

    private val clickPacketsRemaining: Int
        get() = clickPacketsWindowAmount.toInt() - clickPacketQueue.size

    private val canSendClickPackets: Boolean
        get() = clickPacketQueue.size + 1 <= clickPacketsWindowAmount

    init {
        listen<PacketEvent.Send.Pre>(Int.MAX_VALUE) {
            if (it.packet::class.simpleName in ignorePackets) return@listen
            if (packetQueue.add(it)) return@listen

            it.cancel()
            this@PacketLimiter.info("Packet limit reached, dropping packet: ${it.packet::class.simpleName} (${packetQueue.size} / $limit)")
        }

        listen<PlayerEvent.SlotClick> {
            if (limitClicks && !canSendClickPackets) it.cancel()
            else if (clickPacketQueue.add(Unit)) return@listen

            this@PacketLimiter.info("Slot click limit reached, dropping ${it.action} at ${it.slot} in ${it.screenHandler::class.simpleName} (${clickPacketQueue.size} / $limitClickRate)")
        }

        listen<RenderEvent.GUI.Fixed> {
            if (!limitClickRender) return@listen

            val screen = mc.currentScreen as? GenericContainerScreen ?: return@listen
            val mcScale = mc.window.scaleFactor
            val fontHeight = FontRenderer.getHeight(mcScale * 1.5)
            val position = Vec2d(screen.x * mcScale, screen.y * mcScale - fontHeight)

            FontRenderer.drawString("Clicks Remaining: $clickPacketsRemaining", position, Color(0x9DFFFF))
        }

        onEnable {
            packetQueue = LimitedDecayQueue(limit, interval)
        }
    }

    enum class Page {
        General,
        Clicks
    }
}
