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

package com.lambda.module.modules.combat.autodisconnect

import com.lambda.Lambda
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.interaction.handlers.FriendHandler
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundHandler.playSound
import com.lambda.util.CommunicationUtils
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.EnchantmentUtils.forEachEnchantment
import com.lambda.util.FormattingUtils.format
import com.lambda.util.ServerTPSUtils
import com.lambda.util.SpeedUnit
import com.lambda.util.TickTimer
import com.lambda.util.combat.CombatUtils.crystalDamage
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.tickDeltaF
import com.lambda.util.item.ItemStackUtils.bundleContents
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.player.PlayerUtils.isIn2b2tQueue
import com.lambda.util.player.MovementUtils.moveDelta
import com.lambda.util.player.SlotUtils.allStacks
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.SlotUtils.inventoryStacks
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import com.lambda.util.text.text
import com.lambda.util.world.WorldUtils.isLoaded
import com.lambda.util.world.fastEntitySearch
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.message.LastSeenMessageList
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.GameMode
import java.awt.Color
import java.time.Instant
import java.util.*

@Suppress("unused")
object AutoDisconnect : Module(
    name = "AutoDisconnect",
    description = "Automatically disconnects when in danger or on low health",
    tag = ModuleTag.COMBAT,
    modulePriority = -100
) {
    private const val INVALID_HOTBAR_SLOT = 42
    private const val IMPOSSIBLE_CHAT_TIMESTAMP = -1L

    private const val MAX_CRYSTAL_DAMAGE_RANGE = 12.0
    private const val CRYSTAL_TRIGGER_RANGE = 6.0

    // Equipment slots shown per player, in display order (hands first, then armor).
    private val EQUIPMENT_SLOTS = listOf(
        "Main Hand" to EquipmentSlot.MAINHAND,
        "Off Hand" to EquipmentSlot.OFFHAND,
        "Head" to EquipmentSlot.HEAD,
        "Chest" to EquipmentSlot.CHEST,
        "Legs" to EquipmentSlot.LEGS,
        "Feet" to EquipmentSlot.FEET,
    )

    // Armor slots shown in the local inventory's "Armor" group, top to bottom.
    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )
    // Ticks to wait after (re)joining before re-arming, so triggers don't re-arm against a
    // world whose entities haven't synced in yet (there's no clean "entities loaded" signal).
    private const val JOIN_GRACE_TICKS = 20

    private const val TRIGGERS_TAB = "Triggers"
    private const val GENERAL_TAB = "General"
    private const val DISPLAY_TAB = "Display"

    private const val PACKET_DISCONNECT_GROUP = "Packet Disconnect Methods"
    private const val DAMAGE_TRIGGER_GROUP = "Damage Triggers"
    private const val HEALTH_GROUP = "Health Settings"
    private const val Y_LEVEL_GROUP = "Y Level Settings"
    private const val FALLS_GROUP = "Falls Settings"
    private const val CRYSTALS_GROUP = "Crystals Settings"
    private const val CREEPERS_GROUP = "Creepers Settings"
    private const val TOTEM_GROUP = "Totem Settings"
    private const val PLAYERS_GROUP = "Players Settings"
    private const val ARMOR_GROUP = "Armor Settings"
    private const val ENTITY_GROUP = "Entity Settings"

    @Tab(TRIGGERS_TAB) private val health by setting("Health", true, "Disconnect from the server when health is below the set limit.")
    @Tab(TRIGGERS_TAB) @Group(HEALTH_GROUP) private val minimumHealth by setting("Min Health", 10, 1..36, 1, "Set the minimum health threshold for disconnection.", unit = " half-hearts") { health }
    @Tab(TRIGGERS_TAB) @Group(HEALTH_GROUP) private val healthSmart by setting("Smart Toggle", true, "Stop re-triggering on health until it climbs back above the minimum.") { health }
    @Tab(TRIGGERS_TAB) @Group(HEALTH_GROUP) private val reEnableThreshold by setting("Re-enable Threshold", 14, 1..36, 1, "Once health climbs above this, the Health trigger re-arms. Never lower than Min Health.", unit = " half-hearts") { health && healthSmart }

    @Tab(TRIGGERS_TAB) private val yLevel by setting("Y Level", false, "Disconnect from the server when the player is below a certain y level")
    @Tab(TRIGGERS_TAB) @Group(Y_LEVEL_GROUP) private val minimumYLevel by setting("Minimum Y Level", 50, 0..319, 1, "The minimum y level the player can be at before disconnecting") { yLevel }
    @Tab(TRIGGERS_TAB) @Group(Y_LEVEL_GROUP) private val yLevelSmart by setting("Smart Toggle", true, "Stop re-triggering on y level until the player climbs back above it.") { yLevel }

    @Tab(TRIGGERS_TAB) private val falls by setting("Falls", false, "Disconnect if the player will die of fall damage")
    @Tab(TRIGGERS_TAB) @Group(FALLS_GROUP) private val fallDistance by setting("Fall Distance", 10, 0..30, 1, "Number of blocks fallen before disconnecting for fall damage.", unit = " blocks") { falls }
    @Tab(TRIGGERS_TAB) @Group(FALLS_GROUP) private val fallsSmart by setting("Smart Toggle", true, "Stop re-triggering on fall damage until the threat clears.") { falls }

    @Tab(TRIGGERS_TAB) private val crystals by setting("Crystals", false, "Disconnect if an End Crystal is close by")
    @Tab(TRIGGERS_TAB) @Group(CRYSTALS_GROUP) private val playerNearCrystal by setting("Player Near Crystal", true, "Disconnect if a player is near an End Crystal near you") { crystals }
    @Tab(TRIGGERS_TAB) @Group(CRYSTALS_GROUP) private val projectileNearCrystal by setting("Projectile Near Crystal", true, "Disconnect if a projectile is near an End Crystal near you") { crystals }
    @Tab(TRIGGERS_TAB) @Group(CRYSTALS_GROUP) private val crystalIgnoreFriends by setting("Ignore Friends", false, "Exclude friends from triggering crystal-based disconnections.") { crystals && playerNearCrystal }
    @Tab(TRIGGERS_TAB) @Group(CRYSTALS_GROUP) private val crystalsSmart by setting("Smart Toggle", false, "Stop re-triggering on crystals until it will no longer be triggered ") { crystals }

    @Tab(TRIGGERS_TAB) private val creeper by setting("Creepers", true, "Disconnect when an ignited Creeper is nearby.")
    @Tab(TRIGGERS_TAB) @Group(CREEPERS_GROUP) private val creeperSmart by setting("Smart Toggle", true, "Stop re-triggering on creepers until none are nearby.") { creeper }

    @Tab(TRIGGERS_TAB) private val totem by setting("Totem", false, "Disconnect if the number of Totems is below the required amount.")
    @Tab(TRIGGERS_TAB) @Group(TOTEM_GROUP) private val minTotems by setting("Min Totems", 2, 1..10, 1, "Set the minimum number of Totems of Undying required to prevent disconnection.") { totem }
    @Tab(TRIGGERS_TAB) @Group(TOTEM_GROUP) private val totemSmart by setting("Smart Toggle", true, "Stop re-triggering on totems until you're back above the minimum.") { totem }

    @Tab(TRIGGERS_TAB) private val players by setting("Players", false, "Disconnect if a nearby player is detected within the set distance.")
    @Tab(TRIGGERS_TAB) @Group(PLAYERS_GROUP) private val minPlayerDistance by setting("Player Distance", 64, 32..128, 4, "Set the distance to detect players for disconnection.") { players }
    @Tab(TRIGGERS_TAB) @Group(PLAYERS_GROUP) private val ignoreFriends by setting("Ignore Friends", false, "Exclude friends from triggering player-based disconnections.") { players }
    @Tab(TRIGGERS_TAB) @Group(PLAYERS_GROUP) private val playersSmart by setting("Smart Toggle", false, "Stop re-triggering on players until none are within range.") { players }

    @Tab(TRIGGERS_TAB) private val armor by setting("Armor", false, "Disconnect when an equipped armor piece's durability drops below the set limit.")
    @Tab(TRIGGERS_TAB) @Group(ARMOR_GROUP) private val minArmorDurability by setting("Min Armor Durability", 10, 1..50, 1, "Disconnect when any equipped armor piece's remaining durability falls below this.") { armor }
    @Tab(TRIGGERS_TAB) @Group(ARMOR_GROUP) private val armorSmart by setting("Smart Toggle", true, "Stop re-triggering on armor until durability climbs back above the minimum.") { armor }

    @Tab(TRIGGERS_TAB) private val entities by setting("Entity", false, "Disconnect when an entity of a selected type is within range.")
    @Tab(TRIGGERS_TAB) @Group(ENTITY_GROUP) private val selectedEntities by setting("Entities", setOf(Registries.ENTITY_TYPE.getId(EntityType.TNT_MINECART).path), Registries.ENTITY_TYPE.ids.map { it.path }.sorted(), "Select specific entities.") { entities }
    @Tab(TRIGGERS_TAB) @Group(ENTITY_GROUP) private val entityRange by setting("Entity Range", 10.0, 0.0..100.0, 1.0, "The range to check for entities.", unit = " blocks") { entities }
    @Tab(TRIGGERS_TAB) @Group(ENTITY_GROUP) private val entitiesSmart by setting("Smart Toggle", true, "Stop re-triggering on entities until none of the selected types are within range.") { entities }

    // ToDo: Only those DamageTypes are reported by the server. why?
    @Tab(TRIGGERS_TAB) private val damage by setting("Damage", false, "Disconnect when taking damage of a selected type.")
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val generic by setting("Generic", false, "Disconnect from the server when you take generic damage. (will always trigger!)") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val inFire by setting("Burning", false, "Disconnect from the server when you take fire damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val lava by setting("Lava", false, "Disconnect from the server when you take lava damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val hotFloor by setting("Hot Floor", false, "Disconnect from the server when you take \"hot floor\" damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val drown by setting("Drown", false, "Disconnect from the server when you take drowning damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val cactus by setting("Cactus", false, "Disconnect from the server when you take cactus damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val fall by setting("Fall", false, "Disconnect from the server when you take fall damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val outOfWorld by setting("Out of World", false, "Disconnect from the server when you take \"out of the world\" damage") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val wither by setting("Wither", false, "Disconnect from the server when you take wither damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val stalagmite by setting("Stalagmite", false, "Disconnect from the server when you take stalagmite damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val arrow by setting("Arrow", false, "Disconnect from the server when you take arrow damage.") { damage }
    @Tab(TRIGGERS_TAB) @Group(DAMAGE_TRIGGER_GROUP) private val trident by setting("Trident", false, "Disconnect from the server when you take trident damage.") { damage }

    @Tab(GENERAL_TAB) @Group(PACKET_DISCONNECT_GROUP) private val invalidHotbarDisconnect by setting("Select Invalid Hotbar Slot", false, "Sends an invalid hotbar selection to force the server to kick the player")
    @Tab(GENERAL_TAB) @Group(PACKET_DISCONNECT_GROUP) private val attackSelfDisconnect by setting("Attack Self", false, "Sends an attack self packet to force the server to kick the player")
    @Tab(GENERAL_TAB) @Group(PACKET_DISCONNECT_GROUP) private val impossibleTimestampChatDisconnect by setting("Send Impossible Chat Timestamp", false, "Sends a chat message with an impossible timestamp to force the server to kick the player")

    @Tab(DISPLAY_TAB) private val hideDetails by setting("Hide Details on Disconnect Screen", false, "Initially hide all details on the disconnect screen")
    @Tab(DISPLAY_TAB) private val showCoordinates by setting("Show Coordinates", true, "Show the player's coordinates on the disconnect screen")
    @Tab(DISPLAY_TAB) private val showTime by setting("Show Time", true, "Show the time of disconnection on the disconnect screen")

    private var disconnectDetails: DisconnectDetails? = null
    private var disconnectInProgress: Boolean = false
    private val joinTimer = TickTimer()
    var lastReconnectTarget: ReconnectTarget? = null

    init {
        listen<TickEvent.Pre> {
            joinTimer.tick()

            // Re-enable disarmed triggers once everything is loaded
            if (isLoaded(player.blockPos) && !isIn2b2tQueue() && joinTimer.hasSurpassed(JOIN_GRACE_TICKS)) {
                Reason.entries.forEach { reason ->
                    if (reason.smartToggle() && !reason.armed && (!reason.enabled() || reason.shouldRearm(this))) {
                        reason.armed = true
                        // Notify only when it recovered while active, not when it re-armed due to being disabled.
                        if (reason.enabled()) AutoDisconnect.info("${reason.displayName} re-armed")
                    }
                }
            }
            //check for reasons to disconnect
            val matchingReason = Reason.entries
                .firstNotNullOfOrNull { reason ->
                    if (reason.enabled() && (!reason.smartToggle() || reason.armed) && reason.generateReason(this) != null) {
                        reason
                    }
                    else null
                }
            if (matchingReason != null) {
                val reasonText = matchingReason.generateReason(this) ?: buildText { literal("Reason lost to the abyss") }
                val willDisarm = matchingReason.smartToggle()
                if (requestDisconnect(reasonText, matchingReason.takeIf { willDisarm }) && willDisarm) {
                    matchingReason.armed = false
                }
            }
        }

        listen<WorldEvent.Join> {
            disconnectInProgress = false
            joinTimer.reset()
        }

        listen<PlayerEvent.Health> { event ->
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
            requestDisconnect(it)
        }
    }

    /**
     * Performs the disconnect for [reasonText] if allowed, returning true if it went
     * through. Returns false (and does nothing) when the player isn't in a survival-like
     * game mode or a disconnect is already in progress.
     */
    private fun SafeContext.requestDisconnect(reasonText: Text, disarmedTrigger: Reason? = null): Boolean {
        if (player.gameMode != GameMode.SURVIVAL && player.gameMode != GameMode.ADVENTURE || disconnectInProgress) return false
        disconnectInProgress = true
        ScreenshotRecorder.takeScreenshot(Lambda.mc.framebuffer, 1) { image ->
            val imageIdentifier = Identifier.of("lambda", "auto_disconnect_screenshot")
            val texture = NativeImageBackedTexture({ "auto-disconnect-screenshot" }, image)
            mc.textureManager.registerTexture(imageIdentifier, texture)
            disconnectDetails = DisconnectDetails(
                imageIdentifier = imageIdentifier,
                imageHeight = image.height,
                imageWidth = image.width,
                reason = if (disarmedTrigger != null) buildText {
                    text(reasonText)
                    literal(" (")
                    highlighted("${disarmedTrigger.displayName} trigger disarmed")
                    literal(")")
                } else reasonText,
                sections = disconnectSections(disarmedTrigger),
                hideDetails = hideDetails
            )

            sendForcedDisconnectPackets()
            //message should never appear to the user, but text is included as a backup in case it does
            connection.connection.disconnect(buildText{ literal("AutoDisconnect: $reasonText") })

            playSound(SoundEvents.BLOCK_ANVIL_LAND)
        }
        return true
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

    private fun SafeContext.disconnectSections(disarmedTrigger: Reason? = null): List<DetailSection> {
        val sections = mutableListOf<DetailSection>()

        // When this disconnect disarmed a smart trigger, call it out on the first line.
        if (disarmedTrigger != null) {
            sections += DetailSection.TextSection(
                buildText {
                    literal("Disarmed the ")
                    highlighted(disarmedTrigger.displayName)
                    literal(" smart trigger.")
                }
            )
        }

        // Always-visible summary: the reason plus when it happened.
        if (showTime) {
            sections += DetailSection.TextSection(
            buildText {
                    literal("Disconnected")
                        literal(" on ")
                        highlighted(CommunicationUtils.currentTime())
                    literal(".")
                }
            )
        }

        // Smart triggers and their armed state, one child per active trigger with its smart toggle on.
        val smartReasons = Reason.entries.filter { it.enabled() && it.smartToggle() }
        if (smartReasons.isNotEmpty()) {
            sections += DetailSection.CollapsibleSection(
                header = Text.literal("Smart Triggers"),
                children = smartReasons.map { reason ->
                    DetailSection.TextSection(
                        buildText {
                            literal("${reason.displayName}: ")
                            if (reason.armed) color(Color.GREEN) { literal("Armed") }
                            else highlighted("Disarmed")
                        }
                    )
                },
                expanded = true
            )
        }

        // Environmental hazards, tucked into a collapsible section when present.
        val hazards = buildList {
            if (player.isSubmergedInWater) add(
                buildText {
                    literal("Submerged in water, had ")
                    highlighted("${player.air}")
                    literal(" ticks left of breath.")
                }
            )
            if (player.isInLava) add(
                buildText { literal("In lava") }
            )
            if (player.isOnFire) add(
                buildText {
                    literal("Burning for ")
                    highlighted("${player.fireTicks}")
                    literal(" more ticks.")
                }
            )
        }
        // The local player: location, health, speed, environmental hazards, and inventory.
        sections += DetailSection.CollapsibleSection(
            header = Text.literal("Player"),
            children = buildList {
                if (showCoordinates) add(
                    DetailSection.TextSection(
                        buildText {
                            literal("Coordinates: ")
                            highlighted(player.pos.format(separator = ", ", precision = 1))
                        }
                    )
                )
                add(
                    DetailSection.TextSection(
                        buildText {
                            literal("Health: ")
                            highlighted(player.fullHealth.format(precision = 1))
                        }
                    )
                )
                add(
                    DetailSection.TextSection(
                        buildText {
                            literal("Speed: ")
                            highlighted(SpeedUnit.BlocksPerSecond.convertFromMinecraft(player.moveDelta).format(precision = 1))
                            literal(" ${SpeedUnit.BlocksPerSecond.unitName}")
                        }
                    )
                )
                if (hazards.isNotEmpty()) add(
                    DetailSection.CollapsibleSection(
                        header = Text.literal("Environment"),
                        body = buildText {
                            hazards.forEachIndexed { index, hazard ->
                                if (index > 0) literal("\n")
                                text(hazard)
                            }
                        }
                    )
                )
                add(
                    DetailSection.CollapsibleSection(
                        header = Text.literal("Inventory"),
                        children = listOf(
                            itemGroup("Hotbar", player.hotbarStacks, player.inventory.selectedSlot),
                            itemGroup("Off Hand", listOf(player.getEquippedStack(EquipmentSlot.OFFHAND))),
                            itemGroup("Inventory", player.inventoryStacks),
                            itemGroup("Armor", ARMOR_SLOTS.map { player.getEquippedStack(it) }),
                        ),
                        expanded = false
                    )
                )
            },
            expanded = true
        )

        // Server-side metrics, below the player and collapsed by default; each line is opt-in via the Display tab.
        val serverDetails = buildList {
            add(DetailSection.TextSection(
                buildText {
                    literal("Ping: ")
                    val ping = connection.getPlayerListEntry(player.uuid)?.latency ?: -1
                    if (ping >= 0) highlighted("$ping ms") else highlighted("unknown")
                }
            ))
            add(DetailSection.TextSection(
                buildText {
                    literal("TPS: ")
                    val tps = ServerTPSUtils.recentData(ServerTPSUtils.TickFormat.Tps).lastOrNull()
                    if (tps != null) {
                        highlighted(tps.format(precision = 1))
                        literal(" t/s")
                    } else highlighted("unknown")
                }
            ))
            add(DetailSection.TextSection(
                buildText {
                    literal("In-game time: ")
                    highlighted(formatDayTime(world.timeOfDay))
                }
            ))
        }
        if (serverDetails.isNotEmpty()) {
            sections += DetailSection.CollapsibleSection(
                header = Text.literal("Server Details"),
                children = serverDetails,
                expanded = false
            )
        }

        // Every tracked player, each expandable into position plus per-item equipment.
        val otherPlayers = world.players
            .filter { it != player }
            .sortedBy { player.distanceTo(it) }
        if (otherPlayers.isNotEmpty()) {
            sections += DetailSection.CollapsibleSection(
                header = Text.literal("Nearby Players"),
                children = otherPlayers.map { playerSection(it) },
                expanded = false
            )
        }

        // Every loaded non-player entity, nearest first. Players are covered by the Nearby Players section.
        val nearbyEntities = world.entities
            .filter { it !is PlayerEntity }
            .sortedBy { player.distanceTo(it) }
        if (nearbyEntities.isNotEmpty()) {
            sections += DetailSection.CollapsibleSection(
                header = Text.literal("Nearby Entities"),
                children = nearbyEntities.map { entitySection(it) },
                expanded = false
            )
        }

        // Everyone on the server tab list, whether or not they're within render distance.
        val onlinePlayers = connection.listedPlayerListEntries
            .map { it.profile.name }
            .sorted()
        if (onlinePlayers.isNotEmpty()) {
            sections += DetailSection.CollapsibleSection(
                header = Text.literal("Online Players"),
                children = onlinePlayers.map { name ->
                    DetailSection.TextSection(buildText { literal(name) })
                },
                expanded = false
            )
        }

        return sections
    }

    /**
     * Formats a world [timeOfDay] (in ticks) as a 24-hour in-game clock. Tick 0 is
     * 06:00, so the day-fraction is offset by 6000 ticks before scaling to minutes.
     */
    private fun formatDayTime(timeOfDay: Long): String {
        val dayTick = ((timeOfDay % 24000L) + 24000L) % 24000L
        val minuteOfDay = ((dayTick + 6000L) % 24000L) * 24 * 60 / 24000
        return "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }

    /**
     * A collapsible card for [other]: header of "name - distance", a body with its
     * position, and one expandable child per non-empty equipment slot.
     */
    private fun SafeContext.playerSection(other: PlayerEntity): DetailSection.CollapsibleSection {
        fun slotEntry(label: String, slot: EquipmentSlot): DetailSection {
            val stack = other.getEquippedStack(slot)
            return if (stack.isEmpty) {
                DetailSection.TextSection(
                    buildText {
                        literal("$label: ")
                        color(Color.GRAY) { literal("Empty") }
                    }
                )
            } else {
                itemRow(stack, label)
            }
        }

        val (armor, hands) = EQUIPMENT_SLOTS.partition { it.second in ARMOR_SLOTS }

        val equipment = hands.map { (label, slot) -> slotEntry(label, slot) } +
            DetailSection.CollapsibleSection(
                header = Text.literal("Armor"),
                children = armor.map { (label, slot) -> slotEntry(label, slot) },
                expanded = false
            )

        return DetailSection.CollapsibleSection(
            header = buildText {
                text(other.name)
                literal(" - ")
                highlighted("${other.distanceTo(player).format(precision = 2)} blocks")
            },
            body = buildText {
                literal("Position: ")
                highlighted(other.pos.format(separator = ", ", precision = 1))
            },
            children = equipment,
            expanded = false
        )
    }

    /**
     * A row for a single [entity]: header of "type - distance". Expands to its held
     * items and armor plus any active status effects (and burning) when it has any;
     * otherwise it's a plain line. Non-living entities (items, projectiles, …) never
     * have equipment or effects, so they render as plain lines.
     */
    private fun SafeContext.entitySection(entity: Entity): DetailSection {
        val header = buildText {
            text(entity.type.name)
            literal(" - ")
            highlighted("${entity.distanceTo(player).format(precision = 2)} blocks")
        }

        val children = buildList {
            if (entity is LivingEntity) {
                EQUIPMENT_SLOTS.forEach { (label, slot) ->
                    val stack = entity.getEquippedStack(slot)
                    if (!stack.isEmpty) add(itemRow(stack, label))
                }
                entity.statusEffects.forEach { effect ->
                    add(
                        DetailSection.TextSection(
                            buildText {
                                text(effect.effectType.value().name)
                                if (effect.amplifier > 0) literal(" ${effect.amplifier + 1}")
                            }
                        )
                    )
                }
            }
            if (entity.isOnFire) add(DetailSection.TextSection(buildText { literal("Burning") }))
        }

        return if (children.isEmpty()) DetailSection.TextSection(header)
        else DetailSection.CollapsibleSection(header = header, children = children, expanded = false)
    }

    /**
     * A collapsible group of inventory [stacks] (e.g. Hotbar, Armor). Empty stacks
     * are dropped; an empty group shows an "Empty" note instead of children. The
     * stack at [selectedIndex] (index into [stacks], before filtering) is marked
     * as selected.
     */
    private fun itemGroup(title: String, stacks: List<ItemStack>, selectedIndex: Int? = null): DetailSection.CollapsibleSection {
        val rows = stacks.mapIndexedNotNull { index, stack ->
            if (stack.isEmpty) null else itemRow(stack, selected = index == selectedIndex)
        }

        return DetailSection.CollapsibleSection(
            header = Text.literal(title),
            body = if (rows.isEmpty()) buildText { color(Color.GRAY) { literal("Empty") } } else null,
            children = rows,
            expanded = false
        )
    }

    /**
     * A row for a single [stack], optionally prefixed with a slot [label]. Expands
     * only when it has something to show: enchantments (listed in the body) and/or
     * shulker-box/bundle contents (listed as nested rows in the same format).
     * Otherwise it's a plain line.
     */
    private fun itemRow(stack: ItemStack, label: String? = null, selected: Boolean = false): DetailSection {
        val header = itemHeader(stack, label, selected)
        val enchantments = stack.forEachEnchantment { entry, level -> Enchantment.getName(entry, level) }
        val contents = (stack.shulkerBoxContents + stack.bundleContents).filter { !it.isEmpty }

        if (enchantments.isEmpty() && contents.isEmpty()) {
            return DetailSection.TextSection(header)
        }

        return DetailSection.CollapsibleSection(
            header = header,
            body = if (enchantments.isEmpty()) {
                null
            } else {
                buildText {
                    enchantments.forEachIndexed { index, enchantment ->
                        if (index > 0) literal("\n")
                        text(enchantment)
                    }
                }
            },
            children = contents.map { itemRow(it) },
            expanded = false
        )
    }

    /**
     * "{label}: {type} ({name}) ({remaining}/{max}) ({count})" for a stack: the
     * [label] prefix only when given, the custom name in parentheses after the item
     * type only when named, the durability (remaining/max) only when damageable, and
     * the count in parentheses only when stackable. When [selected], appends a
     * "(selected)" marker in the arrow color.
     */
    private fun itemHeader(stack: ItemStack, label: String? = null, selected: Boolean = false): Text = buildText {
        if (label != null) literal("$label: ")

        val customName = stack.get(DataComponentTypes.CUSTOM_NAME)
        if (customName != null) {
            text(stack.itemName)
            color(ClickGuiLayout.textDisabled) {
                literal(" (")
                text(customName)
                literal(")")
            }
        } else {
            text(stack.itemName)
        }

        if (stack.isDamageable) {
            color(ClickGuiLayout.textDisabled) { literal(" (${stack.maxDamage - stack.damage}/${stack.maxDamage})") }
        }

        if (stack.maxCount > 1) {
            color(ClickGuiLayout.textDisabled) { literal(" (${stack.count})") }
        }

        if (selected) {
            highlighted(" (selected)")
        }
    }

    enum class Reason(
        val displayName: String,
        val enabled: () -> Boolean,
        val smartToggle: () -> Boolean,
        val generateReason: SafeContext.() -> Text?,
        val shouldRearm: SafeContext.() -> Boolean = { generateReason(this) == null }
    ) {
        Health("Health", { health }, { healthSmart }, {
            if (player.fullHealth < minimumHealth) {
                buildText {
                    literal("Health ")
                    highlighted(player.fullHealth.format())
                    literal(" below minimum of ")
                    highlighted("$minimumHealth")
                    literal("!")
                }
            } else null
        }, shouldRearm = { player.fullHealth > maxOf(reEnableThreshold, minimumHealth) }),
        YLevel("Y Level", { yLevel }, { yLevelSmart }, {
            if (player.pos.y < minimumYLevel) {
                buildText {
                    literal("Player went below y level ")
                    highlighted("$minimumYLevel")
                    literal("!")
                }
            } else null
        }),
        Totem("Totem", { totem }, { totemSmart }, {
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
        Creeper("Creepers", { creeper }, { creeperSmart }, {
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
        Player("Players", { players }, { playersSmart }, {
            fastEntitySearch<PlayerEntity>(minPlayerDistance.toDouble()).find { otherPlayer ->
                otherPlayer != player
                        && player.distanceTo(otherPlayer) <= minPlayerDistance
                        && (!ignoreFriends || FriendHandler.isFriend(otherPlayer.uuid))
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
        EndCrystal("Crystals", { crystals }, { crystalsSmart }, {
            fastEntitySearch<EndCrystalEntity>(MAX_CRYSTAL_DAMAGE_RANGE).firstNotNullOfOrNull { crystal ->
                if (crystalDamage(crystal.pos, player) <= 0.0) return@firstNotNullOfOrNull null

                val requiresTrigger = playerNearCrystal || projectileNearCrystal
                var potentialCrystalTriggerer: Entity? = null
                if (playerNearCrystal) {
                    potentialCrystalTriggerer = fastEntitySearch<PlayerEntity>(CRYSTAL_TRIGGER_RANGE, crystal.blockPos)
                        .firstOrNull { !(crystalIgnoreFriends && FriendHandler.isFriend(it.uuid)) }
                }
                if (potentialCrystalTriggerer == null && projectileNearCrystal) {
                    potentialCrystalTriggerer = fastEntitySearch<ProjectileEntity>(CRYSTAL_TRIGGER_RANGE, crystal.blockPos).firstOrNull()
                }
                if (requiresTrigger && potentialCrystalTriggerer == null) {
                    return@firstNotNullOfOrNull null
                }

                buildText {
                    literal("An End Crystal was ")
                    highlighted(crystal.pos.distanceTo(player.pos).format())
                    literal(" blocks away")
                    potentialCrystalTriggerer?.let {
                        literal(" and could be detonated by ")
                        if (it.customName != null) text(it.name)
                        else highlighted(it.name.string)
                    }
                }
            }
        }),
        FallDamage("Falls", { falls }, { fallsSmart }, {
            if (isFallDeadly() && player.fallDistance > fallDistance &&
                !player.hasStatusEffect(StatusEffects.LEVITATION) &&
                (player.gameMode == GameMode.ADVENTURE || player.gameMode == GameMode.SURVIVAL)
            ) buildText { literal("You just fell more than ${player.fallDistance} blocks and would take lethal damage") }
            else null
        }),
        Armor("Armor", { armor }, { armorSmart }, {
            player.armorSlots.firstOrNull { slot ->
                slot.stack.isDamageable && slot.stack.maxDamage - slot.stack.damage < minArmorDurability
            }?.let { slot ->
                buildText {
                    literal("Armor piece ")
                    text(slot.stack.name)
                    literal(" has only ")
                    highlighted("${slot.stack.maxDamage - slot.stack.damage}")
                    literal(" durability left, below minimum of ")
                    highlighted("$minArmorDurability")
                    literal("!")
                }
            }
        }),
        NearbyEntity("Entity", { entities }, { entitiesSmart }, {
            fastEntitySearch<Entity>(entityRange).find { entity ->
                entity != player
                        && player.distanceTo(entity) <= entityRange
                        && Registries.ENTITY_TYPE.getId(entity.type).path in selectedEntities
            }?.let { entity ->
                buildText {
                    literal("A ")
                    if (entity.customName != null) text(entity.name)
                    else highlighted(entity.name.string)
                    literal(" was ")
                    highlighted("${entity.distanceTo(player).format()} blocks away")
                    literal("!")
                }
            }
        });

        /**
         * A reason that fires is disarmed and won't trigger
         * again until its condition clears (it is then re-armed in the tick loop)
         */
        var armed: Boolean = true
    }
}

data class DisconnectDetails(
    val imageIdentifier: Identifier,
    val imageHeight: Int,
    val imageWidth: Int,
    val reason: Text,
    val sections: List<DetailSection>,
    val hideDetails: Boolean
)

sealed interface ReconnectTarget

data class MultiplayerReconnectTarget(
    val address: ServerAddress,
    val info: ServerInfo,
    val cookieStorage: CookieStorage?
) : ReconnectTarget

data class SingleplayerReconnectTarget(
    val levelName: String
) : ReconnectTarget