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

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import kotlinx.coroutines.delay
import net.minecraft.network.listener.ClientPacketListener
import net.minecraft.network.listener.ServerPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import java.util.concurrent.ConcurrentLinkedDeque

object PacketDelay : Module(
    name = "PacketDelay",
    description = "Delays packets client-bound & server-bound",
    defaultTags = setOf(ModuleTag.NETWORK),
) {
    private val mode by setting("Mode", Mode.STATIC)
    private val networkScope by setting("Network Scope", Direction.BOTH)
    private val packetScope by setting("Packet Scope", PacketType.ANY)
    private val inboundDelay by setting("Inbound Delay", 250L, 0L..5000L, 10L, unit = "ms") { networkScope != Direction.OUTBOUND }
    private val outboundDelay by setting("Outbound Delay", 250L, 0L..5000L, 10L, unit = "ms") { networkScope != Direction.INBOUND }

    private var outboundPool = ConcurrentLinkedDeque<Packet<out ServerPacketListener>>()
    private var inboundPool = ConcurrentLinkedDeque<Packet<out ClientPacketListener>>()
    private var outboundLastUpdate = 0L
    private var inboundLastUpdate = 0L

    init {
        listener<RenderEvent.World> {
            if (mode != Mode.STATIC) return@listener

            flushPools(System.currentTimeMillis())
        }

        listener<PacketEvent.Send.Pre>(Int.MIN_VALUE) { event ->
            if (!packetScope.filter(event.packet)) return@listener

            when (mode) {
                Mode.STATIC -> {
                    outboundPool.add(event.packet)
                    event.cancel()
                }

                Mode.PULSE -> {
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

        listener<PacketEvent.Receive.Pre>(Int.MIN_VALUE) { event ->
            if (!packetScope.filter(event.packet)) return@listener

            when (mode) {
                Mode.STATIC -> {
                    inboundPool.add(event.packet)
                    event.cancel()
                }

                Mode.PULSE -> {
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

    enum class Mode { STATIC, PULSE, }
    enum class Direction { BOTH, INBOUND, OUTBOUND }
    enum class PacketType(val filter: (Packet<*>) -> Boolean) {
        ANY({ true }),
        KEEP_ALIVE({ it is KeepAliveC2SPacket })
    }
}
