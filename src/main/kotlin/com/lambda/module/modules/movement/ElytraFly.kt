/*
 * Copyright 2025 Lambda
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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.player.MovementUtils.addSpeed
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.sound.SoundEvents

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Allows you to fly with an elytra",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val mode by setting("Mode", FlyMode.Bounce)

    //ToDo: Implement these commented out settings
//    private val takeoff by setting("Takeoff", true, "Automatically jumps and initiates gliding") { mode == FlyMode.Bounce }
    private val autoPitch by setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds") { mode == FlyMode.Bounce }
    private val pitch by setting("Pitch", 70, 0..90, 1) { autoPitch && mode == FlyMode.Bounce }
    private val jump by setting("Jump", true, "Automatically jumps") { mode == FlyMode.Bounce }
    @JvmStatic val sprint by setting("Sprint", true) { mode == FlyMode.Bounce }
//    private val passObstacles by setting("Pass Obstacles", true, "Automatically paths around obstacles using baritone") { mode == FlyMode.Bounce }

    private val boostSpeed by setting("Boost", 0.02, 0.0..0.5, 0.005, description = "Speed to add when flying")
    private val rocketSpeed by setting("Rocket Speed", 0.0, 0.0 ..2.0, description = "Speed multiplier that the rocket gives you") { mode == FlyMode.Enhanced }

    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    init {
        setDefaultAutomationConfig {
            applyEdits {
                hideAllGroupsExcept(inventoryConfig)
            }
        }

        listen<TickEvent.Pre> {
            if (mode != FlyMode.Bounce) return@listen
            if (autoPitch) {
                rotationRequest {
                    pitch(pitch.toFloat())
                }.submit()
            }

            if (mode == FlyMode.Bounce && player.isGliding)
                connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
        }

        listen<MovementEvent.InputUpdate> { event ->
            if (player.isGliding && mode == FlyMode.Bounce && jump) {
                event.input.jump()
            }
        }

        listen<MovementEvent.Player.Pre> {
            if (player.isElytraFlying && !player.isUsingItem) {
                addSpeed(boostSpeed)
            }
        }

        listen<ClientEvent.Sound> { event ->
            if (!mute) return@listen
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listen
            event.cancel()
        }
    }

    @JvmStatic
    fun boostRocket() = runSafe {
        if (mode == FlyMode.Bounce) return@runSafe
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

    enum class FlyMode {
        Bounce,
        Enhanced
    }
}
