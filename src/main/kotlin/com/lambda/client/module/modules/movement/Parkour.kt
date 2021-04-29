package com.lambda.client.module.modules.movement

import net.minecraftforge.fml.common.gameevent.TickEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener

internal object Parkour : Module(
    name = "Parkour",
    description = "Automatically jump at the edge of blocks",
    category = Category.MOVEMENT
) {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (
                player.onGround && !player.isSneaking &&
                world.getCollisionBoxes(player, player.entityBoundingBox
                    .offset(0.0, -0.5, 0.0)
                    .expand(-0.001, 0.0, -0.001)).isEmpty()
            ) {
                player.jump()
            }
        }
    }
}