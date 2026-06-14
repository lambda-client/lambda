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

package com.lambda.module.modules.combat

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendHandler
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundHandler.playSound
import com.lambda.util.CommunicationUtils
import com.lambda.util.CommunicationUtils.prefix
import com.lambda.util.FormattingUtils.format
import com.lambda.util.NamedEnum
import com.lambda.util.combat.CombatUtils.hasDeadlyCrystal
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.tickDeltaF
import com.lambda.util.player.SlotUtils.allStacks
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import com.lambda.util.text.text
import com.lambda.util.world.fastEntitySearch
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.network.message.LastSeenMessageList
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.GameMode
import java.awt.Color
import java.time.Instant
import java.util.BitSet

@Suppress("unused")
object AutoDisconnect : Module(
    name = "AutoDisconnect",
    description = "Automatically disconnects when in danger or on low health",
    tag = ModuleTag.COMBAT,
    modulePriority = -100
) {
    private const val INVALID_HOTBAR_SLOT = 42
    private const val IMPOSSIBLE_CHAT_TIMESTAMP = -1L

    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        DisconnectConditions("Conditions"),
    }

    private val health by setting("Health", true, "Disconnect from the server when health is below the set limit.")//.group(Group.DisconnectConditions)
    private val minimumHealth by setting("Min Health", 10, 1..36, 1, "Set the minimum health threshold for disconnection.", unit = " half-hearts") { health }//.group(Group.DisconnectConditions)
    private val yLevel by setting("Y Level", false, "Disconnect from the server when the player is below a certain y level")//.group(Group.DisconnectConditions)
    private val minimumYLevel by setting("Minimum Y Level", 50, 0..319, 1, "The minimum y level the player can be at before disconnecting") { yLevel }//.group(Group.DisconnectConditions)
    private val falls by setting("Falls", false, "Disconnect if the player will die of fall damage")//.group(Group.DisconnectConditions)
    private val fallDistance by setting("Falls Time", 10, 0..30, 1, "Number of blocks fallen before disconnecting for fall damage.", unit = " blocks") { falls }//.group(Group.DisconnectConditions)
    private val crystals by setting("Crystals", false, "Disconnect if an End Crystal explosion would be lethal.")//.group(Group.DisconnectConditions)
    private val creeper by setting("Creepers", true, "Disconnect when an ignited Creeper is nearby.")//.group(Group.DisconnectConditions)
    private val totem by setting("Totem", false, "Disconnect if the number of Totems of Undying is below the required amount.")//.group(Group.DisconnectConditions)
    private val minTotems by setting("Min Totems", 2, 1..10, 1, "Set the minimum number of Totems of Undying required to prevent disconnection.") { totem }//.group(Group.DisconnectConditions)
    private val players by setting("Players", false, "Disconnect if a nearby player is detected within the set distance.")//.group(Group.DisconnectConditions)
    private val minPlayerDistance by setting("Player Distance", 64, 32..128, 4, "Set the distance to detect players for disconnection.") { players }//.group(Group.DisconnectConditions)
    private val friends by setting("Friends", false, "Exclude friends from triggering player-based disconnections.") { players }//.group(Group.DisconnectConditions)

    private val onDamage by setting("On Damage", false, "Disconnect from the server when you take damage.")//.group(Group.DisconnectConditions)

    // ToDo: Only those DamageTypes are reported by the server. why?
    private val generic by setting("Generic", false, "Disconnect from the server when you get generic damage. (will always trigger!)") { onDamage }//.group(Group.DisconnectConditions)
    private val inFire by setting("Burning", false, "Disconnect from the server when you take fire damage.") { onDamage }//.group(Group.DisconnectConditions)
    private val lava by setting("Lava", false, "Disconnect from the server when you get lava.") { onDamage }//.group(Group.DisconnectConditions)
    private val hotFloor by setting("Hot Floor", false, "Disconnect from the server when you get hot floor.") { onDamage }//.group(Group.DisconnectConditions)
    private val drown by setting("Drown", false, "Disconnect from the server when you get drown.") { onDamage }//.group(Group.DisconnectConditions)
    private val cactus by setting("Cactus", false, "Disconnect from the server when you get cactus.") { onDamage }//.group(Group.DisconnectConditions)
    private val fall by setting("Fall", false, "Disconnect from the server when you fall.") { onDamage }//.group(Group.DisconnectConditions)
    private val outOfWorld by setting("Out of World", false, "Disconnect from the server when you get out of the world.") { onDamage }//.group(Group.DisconnectConditions)
    private val wither by setting("Wither", false, "Disconnect from the server when you get wither damage.") { onDamage }//.group(Group.DisconnectConditions)
    private val stalagmite by setting("Stalagmite", false, "Disconnect from the server when you get stalagmite damage.") { onDamage }//.group(Group.DisconnectConditions)
    private val arrow by setting("Arrow", false, "Disconnect from the server when you get arrow damage.") { onDamage }//.group(Group.DisconnectConditions)
    private val trident by setting("Trident", false, "Disconnect from the server when you get trident damage.") { onDamage }//.group(Group.DisconnectConditions)

    private val hideDetails by setting("Hide Details on Disconnect Screen", false, "Initially hide all details on the disconnect screen")//.group(Group.General)
    private val invalidHotbarDisconnect by setting("Invalid Hotbar Disconnect", false, "Sends an invalid hotbar selection to force the server to kick the player")//.group(Group.General)
    private val attackSelfDisconnect by setting("Attack Self Disconnect", false, "Sends an attack self packet to force the server to kick the player")//.group(Group.General)
    private val impossibleTimestampChatDisconnect by setting("Impossible Timestamp Chat Disconnect", false, "Sends a chat message with an impossible timestamp to force the server to kick the player")//.group(Group.General)

    private var disconnectDetails: DisconnectDetails? = null
    private var disconnectInProgress: Boolean = false
    var lastReconnectTarget: ReconnectTarget? = null

    init {
        listen<TickEvent.Pre> {
            Reason.entries.filter {
                it.check()
            }.forEach { reason ->
                reason.generateReason(this)?.let { reasonText ->
                    disconnect(reasonText, reason)
                    return@listen
                }
            }
        }

        listen<WorldEvent.Join> {
            disconnectInProgress = false
        }

        listen<PlayerEvent.Health> { event ->
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
            highlighted(amount.format())
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
                highlighted(it.format())
            }
            literal(".")
        }.let {
            disconnect(it)
        }
    }

    private fun SafeContext.disconnect(reasonText: Text, reason: Reason? = null) {
        if (player.gameMode != GameMode.SURVIVAL && player.gameMode != GameMode.ADVENTURE || disconnectInProgress) return
        if (reason == Reason.Health || reason == Reason.Totem) disable()
        disconnectInProgress = true
        ScreenshotRecorder.takeScreenshot(Lambda.mc.framebuffer, 1) { image ->
            val imageIdentifier = Identifier.of("lambda", "auto_disconnect_screenshot")
            val texture = NativeImageBackedTexture({ "auto-disconnect-screenshot" }, image)
            mc.textureManager.registerTexture(imageIdentifier, texture)
            disconnectDetails = DisconnectDetails(
                imageIdentifier = imageIdentifier,
                imageHeight = image.height,
                imageWidth = image.width,
                reason = reasonText,
                details = generateInfo(reasonText),
                hideDetails = hideDetails
            )

            sendForcedDisconnectPackets()
            connection.connection.disconnect(generateInfo(reasonText))

            playSound(SoundEvents.BLOCK_ANVIL_LAND)
        }
    }

    private fun SafeContext.sendForcedDisconnectPackets() {
        if (invalidHotbarDisconnect) {
            connection.sendPacket(UpdateSelectedSlotC2SPacket(INVALID_HOTBAR_SLOT))
        }

        if (attackSelfDisconnect) {
            interaction.attackEntity(player, player)
        }

        if (impossibleTimestampChatDisconnect) {
            connection.sendPacket(
                ChatMessageC2SPacket(
                    "",
                    Instant.ofEpochSecond(IMPOSSIBLE_CHAT_TIMESTAMP),
                    0L,
                    null,
                    LastSeenMessageList.Acknowledgment(
                        0,
                        BitSet.valueOf(ByteArray(LastSeenMessageList.MAX_ENTRIES)),
                        LastSeenMessageList.Acknowledgment.NO_CHECKSUM
                    )
                )
            )
        }
    }

    fun consumeDetails(): DisconnectDetails? {
        val details = disconnectDetails
        disconnectDetails = null
        if (details != null) {
            disconnectInProgress = false
        }
        return details
    }

    private fun SafeContext.generateInfo(text: Text) = buildText {
        text(prefix(CommunicationUtils.LogLevel.Warn.logoColor))
        text(text)
        literal("\n\n")
        literal("Disconnected at ")
        highlighted(player.pos.format())
        literal(" on ")
        highlighted(CommunicationUtils.currentTime())
        literal(" with ")
        highlighted(player.fullHealth.format())
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
        Health({ health }, {
            if (player.fullHealth < minimumHealth) {
                buildText {
                    literal("Health ")
                    highlighted(player.fullHealth.format())
                    literal(" below minimum of ")
                    highlighted("$minimumHealth")
                    literal("!")
                }
            } else null
        }),
        YLevel({ yLevel }, {
            if (player.pos.y < minimumYLevel) {
                buildText {
                    literal("Player went below y level ")
                    highlighted("$minimumYLevel")
                    literal("!")
                }
            } else null
        }),
        Totem({ totem }, {
            val totemCount = player.allStacks.count { it.item == Items.TOTEM_OF_UNDYING }
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
        Creeper({ creeper }, {
            fastEntitySearch<CreeperEntity>(15.0).find {
                it.getLerpedFuseTime(mc.tickDeltaF) > 0.0
                        && it.pos.distanceTo(player.pos) <= 5.0
            }?.let { creeper ->
                buildText {
                    literal("An ignited creeper was ")
                    highlighted(creeper.pos.distanceTo(player.pos).format())
                    literal(" blocks away!")
                }
            }
        }),
        Player({ players }, {
            fastEntitySearch<PlayerEntity>(minPlayerDistance.toDouble()).find { otherPlayer ->
                otherPlayer != player
                        && player.distanceTo(otherPlayer) <= minPlayerDistance
                        && (!friends || !FriendHandler.isFriend(otherPlayer.uuid))
            }?.let { otherPlayer ->
                buildText {
                    literal("The player ")
                    text(otherPlayer.name)
                    literal(" was ")
                    highlighted("${otherPlayer.distanceTo(player).format()} blocks away")
                    literal("!")
                }
            }
        }),
        EndCrystal({ crystals }, {
            if (hasDeadlyCrystal())
                buildText {
                    literal("There was an end crystal close to you that would've killed you")
                }
            else null
        }),
        FallDamage({ falls }, {
            if (isFallDeadly() && player.fallDistance > fallDistance &&
                !player.hasStatusEffect(StatusEffects.LEVITATION) &&
                (player.gameMode == GameMode.ADVENTURE || player.gameMode == GameMode.SURVIVAL)
            ) buildText { literal("You were about to fall and die") }
            else null
        })
    }
}

data class DisconnectDetails(
    val imageIdentifier: Identifier,
    val imageHeight: Int,
    val imageWidth: Int,
    val reason: Text,
    val details: Text,
    val hideDetails: Boolean
)

sealed interface ReconnectTarget

data class MultiplayerReconnectTarget(
    val address: ServerAddress,
    val info: ServerInfo,
    val cookieStorage: CookieStorage
) : ReconnectTarget

data class SingleplayerReconnectTarget(
    val levelName: String
) : ReconnectTarget
