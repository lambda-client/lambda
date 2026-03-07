/*
 * Copyright 2025 Lambda
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
import com.lambda.util.reflections.className
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket

// ToDo: HUD info
object PacketLimiter : Module(
	name = "PacketLimiter",
	description = "Limits the amount of packets sent to the server",
	tag = ModuleTag.NETWORK,
) {
	private var packetQueueMap = mutableMapOf<String, LimitedDecayQueue<PacketEvent.Send.Pre>>()
	private val globalQueue = LimitedDecayQueue<PacketEvent.Send.Pre>(1, 1)

	private val limit by setting("Limit per packet", 99, 1..100, 1, "The maximum amount of packets to send per given time interval", unit = " packets")
		.onValueChange { _, to -> packetQueueMap.forEach { (t, u) -> u.setSizeLimit(to) } }
	private val globalLimit by setting("Global Limit", 1000, 100..5000, 50, "The maximum amount of packets to send overall per given time interval", unit = " packets")
		.onValueChange { _, to -> globalQueue.setSizeLimit(to) }

	private val interval by setting("Duration", 4000L, 1L..10000L, 50L, "The interval / duration in milliseconds to limit packets for", unit = " ms")
		.onValueChange { _, to -> packetQueueMap.forEach { (t, u) -> u.setDecayTime(to) }; globalQueue.setDecayTime(to) }

	private val defaultIgnorePackets = setOf(
		nameOf<CommonPongC2SPacket>(),
		nameOf<PositionAndOnGround>(),
		nameOf<Full>(),
		nameOf<LookAndOnGround>(),
		nameOf<OnGroundOnly>(),
		nameOf<TeleportConfirmC2SPacket>()
	)

	inline fun <reified T> nameOf(): String = T::class.className

	// ToDo: Find a way to have a list of serverbound packets
	private val ignorePackets by setting("Ignore Packets", defaultIgnorePackets, description = "Packets to ignore when limiting")

	init {
		onEnable {
			packetQueueMap.clear()
			globalQueue.setDecayTime(interval)
			globalQueue.setSizeLimit(globalLimit)
		}

		listen<PacketEvent.Send.Pre>({ Int.MAX_VALUE }) {
			if (it.packet::class.java.name in ignorePackets) return@listen

			if (!globalQueue.add(it)) {
				it.cancel()
				return@listen
			}
			//            this@PacketLimiter.info("Packet sent: ${it.packet::class.simpleName} (${packetQueue.size} / $limit) ${Instant.now()}")
			val queue = packetQueueMap.getOrPut(it.packet::class.java.name) {
				LimitedDecayQueue(limit, interval)
			}
			if (queue.add(it)) return@listen

			it.cancel()
			this@PacketLimiter.info("Packet limit reached, dropping packet: ${it.packet::class.simpleName} (${queue.size} / $limit)")
		}
	}
}
