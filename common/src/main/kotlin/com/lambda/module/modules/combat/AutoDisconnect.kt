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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Communication
import com.lambda.util.Communication.prefix
import com.lambda.util.Formatting.string
import com.lambda.util.combat.CombatUtils.hasDeadlyCrystal
import com.lambda.util.extension.tickDelta
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.text.*
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
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

    private val onDamage by setting("On Damage", false, "Disconnect from the server when you take damage.")

    // ToDo: Only those DamageTypes are reported by the server. why?
    private val generic by setting("Generic", false, "Disconnect from the server when you get generic damage. (will always trigger!)") { onDamage }
    private val inFire by setting("Burning", false, "Disconnect from the server when you take fire damage.") { onDamage }
    private val lava by setting("Lava", false, "Disconnect from the server when you get lava.") { onDamage }
    private val hotFloor by setting("Hot Floor", false, "Disconnect from the server when you get hot floor.") { onDamage }
    private val drown by setting("Drown", false, "Disconnect from the server when you get drown.") { onDamage }
    private val cactus by setting("Cactus", false, "Disconnect from the server when you get cactus.") { onDamage }
    private val fall by setting("Fall", false, "Disconnect from the server when you fall.") { onDamage }
    private val outOfWorld by setting("Out of World", false, "Disconnect from the server when you get out of the world.") { onDamage }
    private val wither by setting("Wither", false, "Disconnect from the server when you get wither damage.") { onDamage }
    private val stalagmite by setting("Stalagmite", false, "Disconnect from the server when you get stalagmite damage.") { onDamage }
    private val arrow by setting("Arrow", false, "Disconnect from the server when you get arrow damage.") { onDamage }
    private val trident by setting("Trident", false, "Disconnect from the server when you get trident damage.") { onDamage }

    init {
        listen<TickEvent.Pre>(-1000) {
            Reason.entries.filter {
                it.check()
            }.forEach { reason ->
                reason.generateReason(this)?.let { reasonText ->
                    disconnect(reasonText, reason)
                    return@listen
                }
            }
        }

        listen<PlayerEvent.Damage> { event ->
            if (!onDamage) return@listen

            val damageHandlers = listOf(
                inFire to DamageTypes.IN_FIRE,
                lava to DamageTypes.LAVA,
                hotFloor to DamageTypes.HOT_FLOOR,
                drown to DamageTypes.DROWN,
                cactus to DamageTypes.CACTUS,
                fall to DamageTypes.FALL,
                outOfWorld to DamageTypes.OUT_OF_WORLD,
                generic to DamageTypes.GENERIC,
                wither to DamageTypes.WITHER,
                stalagmite to DamageTypes.STALAGMITE,
                arrow to DamageTypes.ARROW,
                trident to DamageTypes.TRIDENT
            )

            player.recentDamageSource?.let { source ->
                damageHandlers.firstOrNull { (enabled, damageSource) ->
                    enabled && source.isOf(damageSource)
                }?.let {
                    damageDisconnect(source, event.amount)
                }
            }
        }
    }

    private fun SafeContext.damageDisconnect(source: DamageSource, amount: Float) {
        buildText {
            literal("Got ")
            highlighted(amount.string)
            literal(" damage of type ")
            highlighted(source.name)
            source.attacker?.let {
                literal(" from attacker ")
                if (it.customName != null) text(it.name)
                else highlighted(it.name.string)
            }
            source.source?.let {
                literal(" by source ")
                if (it.customName != null) text(it.name)
                else highlighted(it.name.string)
            }
            source.position?.let {
                literal(" at position ")
                highlighted(it.string)
            }
            literal(".")
        }.let {
            disconnect(it)
        }
    }

    private fun SafeContext.disconnect(reasonText: Text, reason: Reason? = null) {
        if (reason == Reason.HEALTH || reason == Reason.TOTEM) disable()
        connection.connection.disconnect(generateInfo(reasonText))
        playSound(SoundEvents.BLOCK_ANVIL_LAND)
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
        highlighted(player.health.string)
        literal(" health.")
        if (player.isSubmergedInWater) {
            literal("\n")
            literal("Submerged in water, had ")
            highlighted("${player.air}")
            literal(" ticks left of breath.")
        }
        if (player.isInLava) {
            literal("\n")
            literal("In lava, had ")
            highlighted("${player.air}")
            literal(" ticks left of breath.")
        }
        if (player.isOnFire) {
            literal("\n")
            literal("Burning for ")
            highlighted("${player.fireTicks}")
            literal(" ticks.")
        }
        if (isDisabled) {
            color(Color.YELLOW) {
                literal("\n\n")
                literal("AutoDisconnect disabled.")
            }
        }
    }

    enum class Reason(val check: () -> Boolean, val generateReason: SafeContext.() -> Text?) {
        HEALTH({ health }, {
            if (player.health < minimumHealth) {
                buildText {
                    literal("Health ")
                    highlighted(player.health.string)
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
                    highlighted(creeper.pos.distanceTo(player.pos).string)
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
                    literal("The player ")
                    text(otherPlayer.name)
                    literal(" was ")
                    highlighted("${otherPlayer.distanceTo(player).string} blocks away")
                    literal("!")
                }
            }
        }),
        END_CRYSTAL({ crystals }, {
            if (hasDeadlyCrystal(1.0))
                buildText {
                    literal("There was an end crystal close to you that would've killed you")
                }
            else null
        });
    }
}
