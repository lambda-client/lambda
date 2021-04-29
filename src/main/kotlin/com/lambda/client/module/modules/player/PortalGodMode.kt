package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.event.listener.listener
import net.minecraft.network.play.client.CPacketConfirmTeleport

internal object PortalGodMode : Module(
    name = "PortalGodMode",
    category = Category.PLAYER,
    description = "Don't take damage in portals"
) {
    private val instantTeleport by setting("Instant Teleport", true)

    private var packet: CPacketConfirmTeleport? = null

    init {
        onEnable {
            packet = null
        }

        onDisable {
            runSafe {
                if (instantTeleport) packet?.let {
                    connection.sendPacket(it)
                }
            }
        }

        listener<PacketEvent.Send> {
            if (it.packet !is CPacketConfirmTeleport) return@listener
            it.cancel()
            packet = it.packet
        }
    }
}