package com.lambda.client.module.modules.misc


import net.minecraft.entity.item.EntityFireworkRocket
import net.minecraft.network.play.server.SPacketPlayerPosLook
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener

internal object ElytraFix: Module(
    name = "ElytraFix",
    category = Category.MISC,
    description = "Remove Firework Rockets on Rubberband",
) {
    init {
        safeListener<PacketEvent.Receive> {
            if (it.packet is SPacketPlayerPosLook && player.isElytraFlying) {
                world.getLoadedEntityList().filterIsInstance<EntityFireworkRocket>().forEach {
                    world.removeEntity(it)
                }
            }
        }
    }
}