package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.MovementUtils.addSpeed
import net.minecraft.sound.SoundEvents

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Allows you to fly with an elytra",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.GRIM)
) {
    // private val page by setting("Page", Page.GENERAL) // Uncomment when needed
    val mode by setting("Mode", Mode.BOOST)

    private val speed by setting("Speed", 0.02, 0.0..0.5, 0.005, description = "Speed to add when flying") { mode == Mode.BOOST }
    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    val rocketSpeed by setting("Rocket Speed", 2.0, 0.0 ..5.0, description = "Speed multiplier that the rocket gives you") { mode == Mode.ROCKET_BOOST }

    init {
        listener<MovementEvent.Pre> {
            when (mode) {
                Mode.BOOST -> {
                    if (player.isFallFlying && !player.isUsingItem) {
                        addSpeed(speed)
                    }
                }

                Mode.ROCKET_BOOST -> {


                }
            }
        }


        listener<ClientEvent.Sound> { event ->
            if (!mute) return@listener
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listener
            event.cancel()
        }
    }

    private enum class Page {
        GENERAL,
        // Add more when needed
    }

    enum class Mode {
        BOOST,
        ROCKET_BOOST,
        // Add more when needed
    }
}
