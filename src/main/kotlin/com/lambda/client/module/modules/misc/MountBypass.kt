package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.passive.AbstractChestHorse
import net.minecraft.network.play.client.CPacketUseEntity

object MountBypass : Module(
    name = "MountBypass",
    description = "Attempts to allow you to mount chested animals on servers that block it",
    category = Category.MISC
) {
    init {
        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketUseEntity || it.packet.action != CPacketUseEntity.Action.INTERACT_AT) return@safeListener
            if (it.packet.getEntityFromWorld(world) is AbstractChestHorse) it.cancel()
        }
    }
}
