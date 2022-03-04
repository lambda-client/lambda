package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.playerPosLookPitch
import com.lambda.client.mixin.extension.playerPosLookYaw
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.server.SPacketPlayerPosLook

object AntiForceLook : Module(
    name = "AntiForceLook",
    description = "Stops server packets from turning your head",
    category = Category.PLAYER
) {
    init {
        safeListener<PacketEvent.Receive> {
            if (it.packet !is SPacketPlayerPosLook) return@safeListener
            it.packet.playerPosLookYaw = player.rotationYaw
            it.packet.playerPosLookPitch = player.rotationPitch
        }
    }
}