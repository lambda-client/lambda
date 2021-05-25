package com.lambda.client.module.modules.combat

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.event.listener.listener
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.CPacketUseEntity

object AntiFriendHit : Module(
    name = "AntiFriendHit",
    description = "Don't hit your friends",
    category = Category.COMBAT
) {
    init {
        listener<PacketEvent.Send> {
            if (it.packet !is CPacketUseEntity || it.packet.action != CPacketUseEntity.Action.ATTACK) return@listener
            val entity = mc.world?.let { world -> it.packet.getEntityFromWorld(world) } ?: return@listener
            if (entity is EntityPlayer && FriendManager.isFriend(entity.name)) {
                it.cancel()
            }
        }
    }
}