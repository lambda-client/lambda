package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
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
    private val mode by setting("Mode", Mode.BOOST)

    private val speed by setting("Speed", 0.02, 0.0..0.5, 0.005, description = "Speed to add when flying") { mode == Mode.BOOST }
    private val elytraMute by setting("Mute Elytra", false, "Mutes elytra sound")

    init {
        listener<MovementEvent.Pre> {
            when (mode) {
                Mode.BOOST -> {
                    if (player.isFallFlying && !player.isUsingItem) {
                        addSpeed(speed)
                    }
                }
            }
        }

        listener<ClientEvent.Sound> { event ->
            if (!elytraMute) return@listener
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
        // Add more when needed
    }
}
