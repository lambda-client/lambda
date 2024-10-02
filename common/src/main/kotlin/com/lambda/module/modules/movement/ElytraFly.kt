package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.player.MovementUtils.addSpeed
import net.minecraft.entity.LivingEntity
import net.minecraft.sound.SoundEvents

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Allows you to fly with an elytra",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.GRIM)
) {
    // private val page by setting("Page", Page.GENERAL) // Uncomment when needed

    private val playerBoost by setting("Player Boost", true, description = "Boosts the player when flying")
    private val playerSpeed by setting("Player Speed", 0.02, 0.0..0.5, 0.005, description = "Speed to add when flying") { playerBoost }
    val rocketBoost by setting("Rocket Boost", false, description = "Boosts the player when using a firework")
    private val rocketSpeed by setting("Rocket Speed", 2.0, 0.0 ..2.0, description = "Speed multiplier that the rocket gives you") { rocketBoost }

    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    init {
        listener<MovementEvent.Pre> {
            if (playerBoost && player.isFallFlying && !player.isUsingItem) {
                addSpeed(playerSpeed)
            }
        }

        listener<ClientEvent.Sound> { event ->
            if (!mute) return@listener
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listener
            event.cancel()
        }
    }

    @JvmStatic
    fun boostRocket(shooter: LivingEntity) {
        runSafe {
            if (shooter != player) return@runSafe

            val vec = player.rotationVector
            val velocity = player.velocity

            val d = 1.5 * rocketSpeed
            val e = 0.1 * rocketSpeed

            player.velocity = velocity.add(
                vec.x * e + (vec.x * d - velocity.x) * 0.5,
                vec.y * e + (vec.y * d - velocity.y) * 0.5,
                vec.z * e + (vec.z * d - velocity.z) * 0.5
            )
        }
    }

    private enum class Page {
        GENERAL,
        // Add more when needed
    }
}
