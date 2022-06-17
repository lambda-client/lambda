package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoJump : Module(
    name = "AutoJump",
    description = "Automatically jumps if possible",
    category = Category.MOVEMENT
) {
    private val delay by setting("Delay", 10, 0..40, 1, unit = " ticks")

    private val timer = TickTimer(TimeUnit.TICKS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (player.isInWater || player.isInLava) player.motionY = 0.1
            else if (player.onGround && timer.tick(delay.toLong())) player.jump()
        }
    }
}