package com.lambda.client.module.modules.combat

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.CPacketUseEntity

object AntiFriendHit : Module(
    name = "AntiFriendHit",
    description = "Prevents hitting friends",
    category = Category.COMBAT
) {
    init {
        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketUseEntity || it.packet.action != CPacketUseEntity.Action.ATTACK) return@safeListener
            val entity = it.packet.getEntityFromWorld(world)
            if (entity is EntityPlayer && FriendManager.isFriend(entity.name)) {
                it.cancel()
            }
        }
    }
}
