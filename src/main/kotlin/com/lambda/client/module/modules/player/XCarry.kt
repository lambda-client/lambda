package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.windowID
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.event.listener.listener
import net.minecraft.network.play.client.CPacketCloseWindow

internal object XCarry : Module(
    name = "XCarry",
    category = Category.PLAYER,
    description = "Store items in crafting slots"
) {
    init {
        listener<PacketEvent.Send> {
            if (it.packet is CPacketCloseWindow && it.packet.windowID == 0) {
                it.cancel()
            }
        }
    }
}