/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listen
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
    private val rocketBoost by setting("Rocket Boost", false, description = "Boosts the player when using a firework")
    private val rocketSpeed by setting("Rocket Speed", 2.0, 0.0 ..2.0, description = "Speed multiplier that the rocket gives you") { rocketBoost }

    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    @JvmStatic
    val doBoost: Boolean get() = isEnabled && rocketBoost

    init {
        listen<MovementEvent.Player.Pre> {
            if (playerBoost && player.isElytraFlying && !player.isUsingItem) {
                addSpeed(playerSpeed)
            }
        }

        listen<ClientEvent.Sound> { event ->
            if (!mute) return@listen
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listen
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
