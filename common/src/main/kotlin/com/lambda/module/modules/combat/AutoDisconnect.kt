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

package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Communication
import com.lambda.util.Communication.prefix
import com.lambda.util.Formatting.string
import com.lambda.util.combat.Explosion.explosionDamage
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.text.*
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import java.awt.Color

object AutoDisconnect : Module(
    name = "AutoDisconnect",
    description = "Automatically disconnects when in danger or on low health",
    defaultTags = setOf(ModuleTag.COMBAT)
) {
    private val health by setting("Health", true, "Disconnect from the server when health is below the set limit.")
    private val minimumHealth by setting("Min Health", 10, 6..36, 1, description = "Set the minimum health threshold for disconnection.", unit = " hearts") { health }
    private val crystals by setting("Crystals", false, "Disconnect if an End Crystal explosion would be lethal.")
    private val creeper by setting("Creepers", true, "Disconnect when an ignited Creeper is nearby.")
    private val totem by setting("Totem", false, "Disconnect if the number of Totems of Undying is below the required amount.")
    private val minTotems by setting("Min Totems", 2, 1..10, 1, "Set the minimum number of Totems of Undying required to prevent disconnection.") { totem }
    private val players by setting("Players", false, "Disconnect if a nearby player is detected within the set distance.")
    private val minPlayerDistance by setting("Player Distance", 64, 32..128, 4, "Set the distance to detect players for disconnection.") { players }
    private val friends by setting("Friends", false, "Exclude friends from triggering player-based disconnections.") { players }

    init {
        listen<TickEvent.Pre>(-1000) {
            Reason.entries.filter {
                it.check()
            }.forEach { reason ->
                reason.generateReason(this)?.let { reasonText ->
                    disconnect(reason, reasonText)
                    return@listen
                }
            }
        }
    }

    private fun SafeContext.disconnect(reason: Reason, reasonText: Text) {
        connection.connection.disconnect(generateInfo(reasonText))
        playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)
        if (reason == Reason.HEALTH || reason == Reason.TOTEM) disable()
    }

    private fun SafeContext.generateInfo(text: Text) = buildText {
        text(prefix(Communication.LogLevel.WARN.logoColor))
        text(text)
        literal("\n\n")
        literal("Disconnected at ")
        highlighted(player.pos.string)
        literal(" on ")
        highlighted(Communication.currentTime())
        literal(" with ")
        highlighted("%.2f".format(player.health))
        literal(" health.")
        if (player.isOnFire) {
            literal("\n")
            literal("Burning for ")
            highlighted("${player.fireTicks}")
            literal(" ticks.")
        }

        color(Color.YELLOW) {
            literal("\n\n")
            literal("AutoDisconnect disabled.")
        }
    }

    enum class Reason(val check: () -> Boolean, val generateReason: SafeContext.() -> Text?) {
        HEALTH({ health }, {
            if (player.health < minimumHealth) {
                buildText {
                    literal("Health ")
                    highlighted("%.2f".format(player.health))
                    literal(" below minimum of ")
                    highlighted("$minimumHealth")
                    literal("!")
                }
            } else null
        }),
        TOTEM({ totem }, {
            val totemCount = player.combined.count { it.item == Items.TOTEM_OF_UNDYING }
            if (totemCount < minTotems) {
                buildText {
                    literal("Only ")
                    highlighted("$totemCount")
                    literal(" totems left, required minimum: ")
                    highlighted("$minTotems")
                    literal("!")
                }
            } else null
        }),
        CREEPER({ creeper }, {
            fastEntitySearch<CreeperEntity>(15.0).find {
                it.getClientFuseTime(mc.tickDelta) > 0.0
                        && it.pos.distanceTo(player.pos) <= 5.0
            }?.let { creeper ->
                buildText {
                    literal("An ignited creeper was ")
                    highlighted("%.2f".format(creeper.pos.distanceTo(player.pos)))
                    literal(" blocks away!")
                }
            }
        }),
        PLAYER({ players }, {
            fastEntitySearch<PlayerEntity>(minPlayerDistance.toDouble()).find { otherPlayer ->
                otherPlayer != player
                        && player.distanceTo(otherPlayer) <= minPlayerDistance
                        && (!friends || !FriendManager.isFriend(otherPlayer.uuid))
            }?.let { otherPlayer ->
                buildText {
                    literal("A player (${otherPlayer.name}) was ")
                    highlighted("${"%.2f".format(otherPlayer.distanceTo(player))} blocks away")
                    literal("!")
                }
            }
        }),
        END_CRYSTAL({ crystals }, {
            fastEntitySearch<EndCrystalEntity>(10.2).find {
                player.health - explosionDamage(it.pos, player, 6.0) <= 1.0
            }?.let { crystal ->
                val damage = explosionDamage(crystal.pos, player, 6.0)
                buildText {
                    literal("An end crystal at ")
                    highlighted(crystal.pos.string)
                    literal(" could give you ")
                    highlighted("$damage")
                    literal(" damage what would kill you!")
                }
            }
        });
    }
}