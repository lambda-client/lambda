package com.lambda.client.command.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.text.formatValue
import net.minecraft.entity.Entity

object VanishNoKillCommand : ClientCommand(
    name = "vanishNoKill",
    description = "Allows you to vanish using an entity, without killing the entity off."
) {
    private var vehicle: Entity? = null

    init {
        executeSafe {
            if (player.ridingEntity != null && vehicle == null) {
                vehicle = player.ridingEntity?.also {
                    player.dismountRidingEntity()
                    sendChatMessage("Vehicle " + formatValue(it.name) + " ID: " + formatValue(it.entityId) + " dismounted.")
                }
            } else {
                vehicle?.let {
                    it.isDead = false
                    player.startRiding(it, true)
                    sendChatMessage("Vehicle " + formatValue(it.name) + " ID: " + formatValue(it.entityId) + " mounted.")
                    vehicle = null
                } ?: sendChatMessage("Not riding any vehicles.")
            }
        }
    }
}