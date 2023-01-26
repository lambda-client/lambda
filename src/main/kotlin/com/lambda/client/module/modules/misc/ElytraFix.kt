package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.boostedEntity
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.movement.ElytraFlight
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.accessor.AccessorEntityFireworkRocket
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityFireworkRocket
import net.minecraft.network.play.server.SPacketPlayerPosLook

object ElytraFix : Module(
    name = "ElytraFix",
    description = "Fixes firework rubberband induced velocity desync",
    category = Category.MISC
) {
    init {
        safeListener<PacketEvent.Receive> { event ->
            if (event.packet is SPacketPlayerPosLook && player.isElytraFlying) {
                world.getLoadedEntityList().filterIsInstance<EntityFireworkRocket>().forEach {
                    if (it.boostedEntity == player) world.removeEntity(it)
                }
            }
        }
    }

    fun shouldWork(entity: EntityLivingBase) = EntityPlayerSP::class.java.isAssignableFrom(entity.javaClass)
        && ElytraFlight.isEnabled
        && ElytraFlight.mode.value == ElytraFlight.ElytraFlightMode.VANILLA

    fun shouldModify(entity: EntityLivingBase) = shouldWork(entity)
        && entity.world.loadedEntityList
        .filterIsInstance<AccessorEntityFireworkRocket>()
        .any {
            it.boostedEntity.equals(entity)
        }
}