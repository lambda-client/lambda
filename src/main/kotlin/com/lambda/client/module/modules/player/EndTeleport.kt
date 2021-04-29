package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.server.SPacketDisconnect
import net.minecraft.network.play.server.SPacketRespawn
import net.minecraft.util.text.TextComponentString

internal object EndTeleport : Module(
    name = "EndTeleport",
    category = Category.PLAYER,
    description = "Allows for teleportation when going through end portals"
) {
    private val confirmed by setting("Confirm", false)

    init {
        onEnable {
            if (mc.currentServerData == null) {
                MessageSendHelper.sendWarningMessage("$chatName This module does not work in singleplayer")
                disable()
            } else if (!confirmed) {
                MessageSendHelper.sendWarningMessage("$chatName This module will kick you from the server! It is part of the exploit and cannot be avoided")
            }
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet !is SPacketRespawn) return@safeListener
            if (it.packet.dimensionID == 1 && confirmed) {
                connection.handleDisconnect(SPacketDisconnect(TextComponentString("Attempting teleportation exploit")))
                disable()
            }
        }
    }
}