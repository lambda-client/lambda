package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraftforge.fml.common.gameevent.TickEvent

object NoSwing : Module(
    name = "NoSwing",
    description = "Cancels server or client swing animation",
    category = Category.PLAYER
) {
    private val mode by setting("Mode", Mode.CLIENT)

    private enum class Mode {
        CLIENT, SERVER
    }

    init {
        listener<PacketEvent.Send> {
            if (mode == Mode.SERVER && it.packet is CPacketAnimation) it.cancel()
        }

        safeListener<TickEvent.ClientTickEvent> {
            player.isSwingInProgress = false
            player.swingProgressInt = 0
            player.swingProgress = 0.0f
            player.prevSwingProgress = 0.0f
        }
    }
}