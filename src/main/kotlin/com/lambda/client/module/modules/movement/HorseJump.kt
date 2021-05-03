package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.client.mixin.extension.HorseJumpPower
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object HorseJump : Module(
    name = "HorseJump",
    description = "Lock a horses jump to the maximum",
    category = Category.MOVEMENT
) {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            player.HorseJumpPower = 1.0f
        }
    }
}