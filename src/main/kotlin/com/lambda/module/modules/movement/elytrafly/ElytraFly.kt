/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.movement.elytrafly

import com.lambda.config.ConfigEditor.hideAllExcept
import com.lambda.config.Tab
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.config.withEdits
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.modules.movement.elytrafly.modes.BounceElytraFly
import com.lambda.module.modules.movement.elytrafly.modes.GeneralElytraFly
import com.lambda.module.modules.movement.elytrafly.modes.GrimControlElytraFly
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.player.MovementUtils.addSpeed
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Modifies elytra functionality to allow for more control",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val mode by setting("Fly Mode", FlyMode.Bounce)
        .onValueChange { from, to ->
            from.elytraFly.onDisableListeners.forEach { it() }
            to.elytraFly.onEnableListeners.forEach { it() }
        }

    private val boostSpeed by setting("Boost", 0.00, 0.0..0.5, 0.005, description = "Speed to add when flying")
    private val rocketSpeed by setting("Rocket Speed", 1.0, 0.0..2.0, 0.01, description = "Speed multiplier that the rocket gives you")
    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")
    val fakeFly by setting("Fake Fly", false, "Rapidly swaps the chestplate and elytra to give the appearance the player is flying without an elytra. May also reduce durability loss")

    private const val BOUNCE_TAB = "Bounce"
    private const val CONTROL_TAB = "Control"
    private const val GRIM_CONTROL_TAB = "Grim Control"
    private const val PACKET_TAB = "Packet"
    private const val GENERAL_TAB = "None"

    @Tab(GENERAL_TAB) @JvmStatic val generalMode by configBlock(GeneralElytraFly(this))
    @Tab(BOUNCE_TAB) @JvmStatic val bounceMode by configBlock(BounceElytraFly(this))
    @Tab(GRIM_CONTROL_TAB) @JvmStatic val grimControlMode by configBlock(GrimControlElytraFly(this))
//    @Tab(CONTROL_TAB) @JvmStatic val controlMode by configBlock(ControlElytraFly(this))
//    @Tab(PACKET_TAB) @JvmStatic val packetMode by configBlock(PacketElytraFly(this))

    init {
        setDefaultAutomationConfig()
            .withEdits {
	            hideAllExcept(::inventoryConfig, ::rotationConfig)
            }

        onEnable { mode.elytraFly.onEnableListeners.forEach { it() } }
        onDisable { mode.elytraFly.onDisableListeners.forEach { it() } }

        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen
            mode.elytraFly.onFlagListeners.forEach { it() }
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

    enum class FlyMode(private val elytraFlyGetter: () -> ElytraFlyMode) {
        Bounce({ bounceMode }),
//        Control({ controlMode }),
        GrimControl({ grimControlMode }),
//        Packet({ packetMode }),
        General({ generalMode });

        val elytraFly get() = elytraFlyGetter()
    }
}