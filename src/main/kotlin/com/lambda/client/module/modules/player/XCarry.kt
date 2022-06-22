package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.windowID
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import net.minecraft.network.play.client.CPacketCloseWindow

object XCarry : Module(
    name = "XCarry",
    description = "Store items in crafting slots",
    category = Category.PLAYER
) {
    init {
        listener<PacketEvent.Send> {
            if (it.packet is CPacketCloseWindow && it.packet.windowID == 0) {
                it.cancel()
            }
        }
    }
}