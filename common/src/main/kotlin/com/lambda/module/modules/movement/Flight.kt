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

import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.tag.ModuleTag
import com.lambda.module.Module
import com.lambda.util.Communication.debug
import com.lambda.util.Communication.info
import com.lambda.util.NamedEnum
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket


object Flight : Module(
    name = "Flight",
    description = "Allows you to fly",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    val mode by setting("Mode", Mode.CREATIVE);

    private val speed by setting("Speed", 1.0, 0.1..5.0, 0.01)

    // Creative
    private val preventDisable by setting("Prevent Disable", true, description = "Prevent the server from disabling the flying ability") { mode == Mode.CREATIVE }
    private val conceal by setting("Conceal", true, description = "Cancels outgoing player ability update packets") { mode == Mode.CREATIVE }
    private val onlyAllow by setting("Only Allow", false, description = "Allows you to fly just like in creative mode") { mode == Mode.CREATIVE }

    data class Abilities(val flyingAllowed: Boolean, val flySpeed: Float)

    private var preAbilities = Abilities(false, 0.15F) // Store if server allows us to fly so we don't break flying ability after disabling

    enum class Mode(override val displayName: String) : NamedEnum {
        GROUNDSPOOF("Ground spoof"), // TODO: Implement
        CREATIVE("Creative"),
    }


    init {
        // Cancel packets that disable flying state/ability
        listen<PacketEvent.Receive.Pre> { event ->
            if (!preventDisable) return@listen
            val packet = event.packet
            if (packet is PlayerAbilitiesS2CPacket) {
                // If the server attempts to enforce the abilities present before toggling, cancel
                if (packet.allowFlying() == preAbilities.flyingAllowed || packet.isFlying != isEnabled) {
                    info("server tried to cancel flight")
                    debug(packet.toString())
                    event.cancel()
                }
            }
        }

        // Prevent outgoing player ability updates because it will stand out when a survival player starts flying
        listen<PacketEvent.Send.Pre> { event ->
            if (!conceal) return@listen
            val packet = event.packet
            if (packet is UpdatePlayerAbilitiesC2SPacket) {
                debug(packet.toString())
                event.cancel()
            }
        }

        listen<MovementEvent.Player.Pre> { event ->
            when (mode) {
                Mode.GROUNDSPOOF -> {
                }

                Mode.CREATIVE -> {
                    player.abilities.allowFlying = true
                    if (!onlyAllow) {
                        player.abilities.flying = true
                        player.abilities.flySpeed = 0.05F * speed.toFloat()
                    }
                }
            }
        }

        onEnable {
            when (mode) {
                Mode.GROUNDSPOOF -> {
                }

                Mode.CREATIVE -> {
                    preAbilities = Abilities(player.abilities.allowFlying,player.abilities.flySpeed)
                    player.abilities.allowFlying = true;
                    if (!onlyAllow) player.abilities.flying = true;
                }
            }
        }

        onDisable {
            when (mode) {
                Mode.GROUNDSPOOF -> {
                }

                Mode.CREATIVE -> {
                    player.abilities.flying = false;

                    // Set abilities back to server defaults
                    player.abilities.allowFlying = preAbilities.flyingAllowed
                    player.abilities.flySpeed = preAbilities.flySpeed
                }
            }
        }
    }
}