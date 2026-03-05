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

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.util.ClientPacket
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import com.lambda.util.ServerPacket
import kotlinx.coroutines.delay
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import java.util.concurrent.ConcurrentLinkedDeque

object PacketDelay : Module(
    name = "PacketDelay",
    description = "Delays packets client-bound & server-bound",
    tag = ModuleTag.NETWORK,
) {
    private val mode by setting("Mode", Mode.Static, description = "How the delay is applied: Static queues packets until a flush; Pulse delays each packet individually.")
    private val networkScope by setting("Network Scope", Direction.Both, description = "Which direction(s) to affect: inbound (server → you), outbound (you → server), or both.")
    private val packetScope by setting("Packet Scope", PacketType.Any, description = "What packets to delay. Choose all packets or a specific packet type.")
    private val inboundDelay by setting("Inbound Delay", 250L, 0L..5000L, 10L, unit = "ms", description = "Time to delay packets received from the server before processing.") { networkScope != Direction.Outbound }
    private val outboundDelay by setting("Outbound Delay", 250L, 0L..5000L, 10L, unit = "ms", description = "Time to delay packets sent to the server before sending.") { networkScope != Direction.Inbound }

    private var outboundPool = ConcurrentLinkedDeque<ServerPacket>()
    private var inboundPool = ConcurrentLinkedDeque<ClientPacket>()
    private var outboundLastUpdate = 0L
    private var inboundLastUpdate = 0L

    init {
        tickedRenderer("PacketDelay Ticked Renderer") { safeContext ->
            if (mode != Mode.Static) return@tickedRenderer

            with(safeContext) { flushPools(System.currentTimeMillis()) }
        }

        listen<PacketEvent.Send.Pre>({ Int.MIN_VALUE }) { event ->
            if (!packetScope.filter(event.packet)) return@listen

            when (mode) {
                Mode.Static -> {
                    outboundPool.add(event.packet)
                    event.cancel()
                }

                Mode.Pulse -> {
                    runConcurrent {
                        delay(outboundDelay)
                        runGameScheduled {
                            connection.sendPacketSilently(event.packet)
                        }
                    }
                    event.cancel()
                }
            }
        }

        listen<PacketEvent.Receive.Pre>({ Int.MIN_VALUE }) { event ->
            if (!packetScope.filter(event.packet)) return@listen

            when (mode) {
                Mode.Static -> {
                    inboundPool.add(event.packet)
                    event.cancel()
                }

                Mode.Pulse -> {
                    runConcurrent {
                        delay(inboundDelay)
                        runGameScheduled {
                            connection.handlePacketSilently(event.packet)
                        }
                    }
                    event.cancel()
                }
            }

            event.cancel()
        }

        onDisable {
            flushPools(System.currentTimeMillis())
        }
    }

    private fun SafeContext.flushPools(time: Long) {
        if (time - outboundLastUpdate >= outboundDelay) {
            while (outboundPool.isNotEmpty()) {
                outboundPool.poll().let { packet ->
                    connection.sendPacketSilently(packet)
                }
            }

            outboundLastUpdate = time
        }

        if (time - inboundLastUpdate >= inboundDelay) {
            while (inboundPool.isNotEmpty()) {
                inboundPool.poll().let { packet ->
                    connection.handlePacketSilently(packet)
                }
            }

            inboundLastUpdate = time
        }
    }

    enum class Mode(
        override val displayName: String,
        override val description: String,
    ) : NamedEnum, Describable {
        Static("Static", "Queue packets and release them in bursts based on your delay. Useful for batching traffic."),
        Pulse("Pulse", "Apply a per-packet delay before it is sent/processed. Useful for smoothing timing.")
    }

    enum class Direction(
        override val displayName: String,
        override val description: String,
    ) : NamedEnum, Describable {
        Both("Both", "Affects both outbound (client → server) and inbound (server → client) packets."),
        Inbound("Inbound", "Affects only packets received from the server."),
        Outbound("Outbound", "Affects only packets sent to the server.")
    }

    enum class PacketType(
        override val displayName: String,
        override val description: String,
        val filter: (Packet<*>) -> Boolean,
    ) : NamedEnum, Describable {
        Any("Any", "Delay every packet regardless of type.", { true }),
        KeepAlive("Keep-Alive", "Delay only KeepAlive packets (useful for simulating higher ping).", { it is KeepAliveC2SPacket })
    }

}
