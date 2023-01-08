package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.steerEntity
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.item.EntityBoat
import net.minecraft.entity.passive.AbstractHorse
import net.minecraft.entity.passive.EntityPig

object EntitySpeed : Module(
    name = "EntitySpeed",
    description = "Abuses client-sided movement to change the speed of rideable entities",
    category = Category.MOVEMENT
) {
    private val boatSpeed by setting("Boat Speed", 1.4f, 0.1f..10.0f, 0.05f)
    private val abstractHorseSpeed by setting("Horse Types Speed", 0.7f, 0.1f..10.0f, 0.05f)
    private val pigSpeed by setting("Pig Speed", 1.0f, 0.1f..10.0f, 0.05f)
    private val antiStuck by setting("Anti Stuck", true)
    private val maxJump by setting("Max Jump", true)

    init {
        safeListener<PlayerTravelEvent> {
            player.ridingEntity?.let { entity ->
                var tamper = false

                val speed = when {
                    entity is AbstractHorse && entity.controllingPassenger == player -> abstractHorseSpeed.also { tamper = true }
                    entity is EntityBoat && entity.controllingPassenger == player -> boatSpeed.also { tamper = true }
                    entity is EntityPig -> pigSpeed.also { tamper = true }
                    else -> .0f
                }

                if (!tamper) return@safeListener

                steerEntity(entity, speed, antiStuck)
                entity.rotationYaw = player.rotationYaw

                if (maxJump
                    && entity is AbstractHorse
                    && mc.gameSettings.keyBindJump.isKeyDown
                ) entity.setJumpPower(90)
            }
        }
    }
}
