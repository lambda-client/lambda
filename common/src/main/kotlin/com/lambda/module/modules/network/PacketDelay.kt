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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import com.lambda.util.ClientPacket
import com.lambda.util.PacketUtils.handlePacketSilently
import com.lambda.util.PacketUtils.sendPacketSilently
import com.lambda.util.ServerPacket
import com.lambda.util.Timer
import kotlinx.coroutines.delay
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object PacketDelay : Module(
    name = "PacketDelay",
    description = "Delays packets client-bound & server-bound",
    defaultTags = setOf(ModuleTag.NETWORK),
) {
    private val mode by setting("Mode", Mode.STATIC)
    private val networkScope by setting("Network Scope", Direction.BOTH)
    private val packetScope by setting("Packet Scope", PacketType.ANY)
    private val inboundDelay by setting("Inbound Delay", 250.milliseconds, 1.milliseconds..5.seconds, 10.milliseconds) { networkScope != Direction.OUTBOUND }
    private val outboundDelay by setting("Outbound Delay", 250.milliseconds, 1.milliseconds..5.seconds, 10.milliseconds) { networkScope != Direction.INBOUND }

    private var outboundPool = ConcurrentLinkedDeque<ClientPacket>()
    private var inboundPool = ConcurrentLinkedDeque<ServerPacket>()
    private var outboundLastUpdate = Timer()
    private var inboundLastUpdate = Timer()

    init {
        listen<RenderEvent.World> {
            if (mode != Mode.STATIC) return@listen
            flushPools()
        }

        listen<PacketEvent.Send.Pre>(Int.MIN_VALUE) { event ->
            if (!packetScope.filter(event.packet)) return@listen

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

        listen<PacketEvent.Receive.Pre>(Int.MIN_VALUE) { event ->
            if (!packetScope.filter(event.packet)) return@listen

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
            flushPools()
        }
    }

    private fun SafeContext.flushPools() {
        outboundLastUpdate.runIfPassed(outboundDelay) {
            while (outboundPool.isNotEmpty()) {
                outboundPool.poll().let { packet ->
                    connection.sendPacketSilently(packet)
                }
            }
        }

        inboundLastUpdate.runIfPassed(inboundDelay) {
            while (inboundPool.isNotEmpty()) {
                inboundPool.poll().let { packet ->
                    connection.handlePacketSilently(packet)
                }
            }
        }
    }

    enum class Mode { STATIC, PULSE, }
    enum class Direction { BOTH, INBOUND, OUTBOUND }
    enum class PacketType(val filter: (Packet<*>) -> Boolean) {
        ANY({ true }),
        KEEP_ALIVE({ it is KeepAliveC2SPacket })
    }
}
