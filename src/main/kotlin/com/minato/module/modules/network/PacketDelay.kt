
package com.minato.module.modules.network

import com.minato.context.SafeContext
import com.minato.event.events.PacketEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runConcurrent
import com.minato.threading.runGameScheduled
import com.minato.threading.runSafe
import com.minato.util.ClientPacket
import com.minato.util.Describable
import com.minato.util.NamedEnum
import com.minato.util.PacketUtils.handlePacketSilently
import com.minato.util.PacketUtils.sendPacketSilently
import com.minato.util.ServerPacket
import kotlinx.coroutines.delay
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import java.util.concurrent.ConcurrentLinkedDeque

@Suppress("unused")
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
        tickedRenderer("PacketDelay Ticked Renderer") {
            if (mode != Mode.Static) return@tickedRenderer

            runSafe { flushPools(System.currentTimeMillis()) }
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
