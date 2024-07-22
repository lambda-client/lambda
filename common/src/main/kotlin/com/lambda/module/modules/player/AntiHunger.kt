package com.lambda.module.modules.player

import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object AntiHunger : Module(
    name = "AntiHunger",
    description = "Reduces food consumption",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val cancelMovementState by setting("Cancel Movement State", true)
    
    init {
        listener<PlayerPacketEvent.Pre> { event ->
            if (cancelMovementState) event.isSprinting = false
            event.onGround = (player.fallDistance <= 0 || interaction.isBreakingBlock) && player.isFallFlying
        }
    }
}