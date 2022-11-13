package com.lambda.client.manager.managers

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.Packet
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentLinkedDeque

object PacketManager : Manager {
    private const val maxAge = 1000L

    val recentReceived = ConcurrentLinkedDeque<Pair<Packet<*>,Long>>()
    var totalReceived = 0

    val recentSent = ConcurrentLinkedDeque<Pair<Packet<*>,Long>>()
    var totalSent = 0

    var lastTeleportId = -1

    val packetQueue = ConcurrentLinkedDeque<Packet<*>>()

    init {
        listener<PacketEvent.Receive> {
            recentReceived.add(it.packet to System.currentTimeMillis())
            totalReceived++
        }

        listener<PacketEvent.Send> {
            recentSent.add(it.packet to System.currentTimeMillis())
            totalSent++
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            /** We can do something to handle the packets priority here */
            while (packetQueue.isNotEmpty()) {
                packetQueue.poll()?.let { packet ->
                    mc.player.connection.sendPacket(packet)
                }
            }

            val time = System.currentTimeMillis()
            while (recentReceived.isNotEmpty() && time - recentReceived.peek().second > maxAge) {
                recentReceived.poll()
            }

            while (recentSent.isNotEmpty() && time - recentSent.peek().second > maxAge) {
                recentSent.poll()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            lastTeleportId = -1
        }
    }

    fun postPacket(packet: Packet<*>) {
        packetQueue.add(packet)
    }

    fun isTeleporting(): Boolean {
        return lastTeleportId != -1
    }
}