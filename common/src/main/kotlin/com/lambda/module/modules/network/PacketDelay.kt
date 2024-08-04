package com.lambda.module.modules.network

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runGameScheduled
import kotlinx.coroutines.delay
import net.minecraft.network.ClientConnection
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket

object PacketDelay : Module(
    name = "PacketDelay",
    description = "Delays packets client-bound & server-bound",
    defaultTags = setOf(ModuleTag.NETWORK),
) {
    private val networkScope by setting("Network Scope", Direction.BOTH)
    private val packetScope by setting("Packet Scope", PacketType.ANY)
    private val inboundDelay by setting("Inbound Delay", 250L, 0L..5000L, 10L, unit = "ms") { networkScope != Direction.OUTBOUND }
    private val outboundDelay by setting("Outbound Delay", 250L, 0L..5000L, 10L, unit = "ms") { networkScope != Direction.INBOUND }

    enum class Direction {
        BOTH,
        INBOUND,
        OUTBOUND
    }

    enum class PacketType(val filter: (Packet<*>) -> Boolean) {
        ANY({ true }),
        KEEP_ALIVE({ it is KeepAliveC2SPacket })
    }

    init {
        listener<PacketEvent.Receive.Pre>(Int.MIN_VALUE) { event ->
            if (!connection.connection.isOpen) return@listener
            if (!packetScope.filter(event.packet)) return@listener
            event.cancel()

            runConcurrent {
                delay(inboundDelay)
                runGameScheduled {
                    if (connection.connection.packetListener?.accepts(event.packet) == false) return@runGameScheduled

                    ClientConnection.handlePacket(event.packet, connection.connection.packetListener)
                    connection.connection.packetsReceivedCounter++
                }
            }
        }

        listener<PacketEvent.Send.Pre>(Int.MIN_VALUE) { event ->
            if (!connection.connection.isOpen) return@listener
            if (!packetScope.filter(event.packet)) return@listener
            event.cancel()

            runConcurrent {
                delay(outboundDelay)
                runGameScheduled {
                    connection.connection.send(event.packet, null)
                    connection.connection.packetsSentCounter++
                }
            }
        }
    }
}
