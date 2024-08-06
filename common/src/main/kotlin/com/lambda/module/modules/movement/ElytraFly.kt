package com.lambda.module.modules.movement;
import com.lambda.event.events.MovementEvent
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.util.player.MovementUtils.addSpeed

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Elytra Go Brrr",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.GRIM)
) {
    private val speed by setting("Speed", 0.0, 0.0..0.5, 0.005)
    init {
        listener<MovementEvent.Pre> {
            if (player.isFallFlying && !player.isUsingItem) {
                addSpeed(speed)
            }
        }
    }
}



