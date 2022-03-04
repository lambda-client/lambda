package com.lambda.client.manager.managers

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.server.SPacketPlayerPosLook

object PacketManager : Manager {
    var lastTeleportId = -1

    init {
        listener<PacketEvent.Receive> {
            when (it.packet) {
                is SPacketPlayerPosLook -> {
                    lastTeleportId = it.packet.teleportId
                }
            }
        }

        safeListener<ConnectionEvent> {
            lastTeleportId = -1
        }
    }
}