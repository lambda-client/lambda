package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.accessor.AccessorEntity
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent

object ElytraFastClose : Module(
    name = "ElytraFastClose",
    description = "Closes elytra on ground without waiting for the server",
    category = Category.MOVEMENT
) {
    private val stopMotion by setting("Stop Motion", true)
    private val yThreshold by setting ("Y Distance", 0.05, 0.0..1.0, 0.01)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START || !player.isElytraFlying) return@safeListener
            if (world.collidesWithAnyBlock(player.entityBoundingBox.offset(0.0, -yThreshold, 0.0))) {
                if (stopMotion) {
                    player.motionX = 0.0
                    player.motionY = 0.0
                    player.motionZ = 0.0
                }
                (player as AccessorEntity).invokeSetFlag(7, false)
                connection.sendPacket(CPacketPlayer(true))
            }
        }
    }

}