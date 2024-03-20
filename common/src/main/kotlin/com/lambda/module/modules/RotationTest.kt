package com.lambda.module.modules

import com.lambda.config.InteractionSettings
import com.lambda.config.RotationSettings
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.util.world.EntityUtils.getClosestEntity
import net.minecraft.entity.passive.VillagerEntity

object RotationTest : Module(
    name = "RotationTest",
    description = "Test rotation",
    defaultTags = setOf()
) {
    private val rotationConfig = RotationSettings(this)
    private val interactionConfig = InteractionSettings(this)

    init {
        listener<RotationEvent.Pre> {
            val target = getClosestEntity<VillagerEntity>(
                player.eyePos, interaction.reachDistance.toDouble()
            ) ?: return@listener

            it.lookAt(rotationConfig, interactionConfig, target)
        }
    }
}