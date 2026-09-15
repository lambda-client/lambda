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

import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.FriendHandler
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.combat.CombatUtils.hasDeadlyCrystal
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.tickDeltaF
import com.lambda.util.world.fastEntitySearch
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.registry.tag.ItemTags
import net.minecraft.item.Item
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

@Suppress("unused")
object AutoTotem : Module(
    name = "AutoTotem",
    description = "Intelligently manages offhand items with CPvP modes (Totem, Crystal, Gapple) and safety swaps",
    tag = ModuleTag.COMBAT,
	modulePriority = 100
) {
    enum class OffhandMode(
        override val displayName: String,
        override val description: String,
        val item: Item
    ) : NamedEnum, Describable {
        Totem("Totem", "Always equips Totem of Undying in the offhand.", Items.TOTEM_OF_UNDYING),
        Crystal("Crystal", "Equips End Crystals in the offhand for CPvP.", Items.END_CRYSTAL),
        Gapple("Gapple", "Equips Enchanted Golden Apples in the offhand.", Items.ENCHANTED_GOLDEN_APPLE),
        GoldenApple("Golden Apple", "Equips regular Golden Apples in the offhand.", Items.GOLDEN_APPLE),
        Shield("Shield", "Equips a Shield in the offhand for defense.", Items.SHIELD)
    }

    private val mode by setting("Mode", OffhandMode.Totem, "Primary item to hold in the offhand when safe")
    private val always by setting("Always Totem", false, "Always keeps a totem in the offhand regardless of safe state or selected mode")
    private val swordGap by setting("Sword Gap", true, "Swaps to Golden Apple when holding a sword or axe and pressing use/right-click")
    private val crystalOnCA by setting("Crystal on CA", true, "Automatically equips crystals in offhand when CrystalAura is active")
    private val fallbackToTotem by setting("Fallback to Totem", true, "Falls back to a Totem if the desired offhand item is missing from inventory")
    private val ignoreWhenHolding by setting("Ignore When Holding", false, "Ignore swapping to offhand when already holding a totem in main hand")
    private val minimumHealth by setting("Min Health", 12, 4..36, 1, "Minimum health threshold to force a totem swap", unit = " half-hearts")
    private val falls by setting("Falls", true, "Swap to totem if the player will die of fall damage")
    private val fallDistance by setting("Falls Distance", 8, 0..30, 1, "Number of blocks fallen before swapping", unit = " blocks") { falls }
    private val crystals by setting("Crystals", true, "Swap to totem if an End Crystal explosion would be lethal")
    private val creeper by setting("Creepers", true, "Swap to totem when an ignited Creeper is nearby")
    private val elytraSafety by setting("Elytra Safety", true, "Swap to totem when flying with Elytra at dangerous speeds or low altitude")
    private val players by setting("Players", false, "Swap to totem if a nearby hostile player is detected")
    private val minPlayerDistance by setting("Player Distance", 32, 8..64, 2, "Distance to detect players to force totem") { players }
    private val friends by setting("Friends", false, "Exclude friends from triggering player-based swaps") { players }

    @JvmStatic var offhandOverride: (() -> Boolean)? = null

    fun isDangerous(context: SafeContext): Boolean =
        Reason.entries.any { it.check(context) }

    init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::inventoryConfig)
			}

        listen<TickEvent.Pre> {
            // Prevent swapping when an external container (chest, furnace, etc.) is open
            if (player.currentScreenHandler !== player.playerScreenHandler && player.currentScreenHandler.syncId != 0) return@listen

            val dangerous = isDangerous(this)
            if (!dangerous && offhandOverride?.invoke() == true) return@listen

            val targetItem = when {
                dangerous || always -> Items.TOTEM_OF_UNDYING
                swordGap && isHoldingWeaponAndUsing() -> Items.ENCHANTED_GOLDEN_APPLE
                crystalOnCA && CrystalAura.isEnabled -> Items.END_CRYSTAL
                else -> mode.item
            }

            equipOffhand(targetItem)
        }
    }

    private fun SafeContext.isHoldingWeaponAndUsing(): Boolean {
        val holdingWeapon = player.mainHandStack.isIn(ItemTags.SWORDS) || player.mainHandStack.isIn(ItemTags.AXES)
        return holdingWeapon && mc.options.useKey.isPressed
    }

    private fun SafeContext.equipOffhand(targetItem: Item) {
        if (player.offHandStack.item == targetItem) return
        if (targetItem == Items.TOTEM_OF_UNDYING && ignoreWhenHolding && player.isHolding(Items.TOTEM_OF_UNDYING)) return

        // Find the slot with target item in player's current screen handler
        var slot = targetItem.select()
            .filter(player.currentScreenHandler.slots)
            .firstOrNull()

        // Fallback between enchanted golden apple and standard golden apple for eating
        if (slot == null && targetItem == Items.ENCHANTED_GOLDEN_APPLE) {
            slot = Items.GOLDEN_APPLE.select().filter(player.currentScreenHandler.slots).firstOrNull()
        } else if (slot == null && targetItem == Items.GOLDEN_APPLE) {
            slot = Items.ENCHANTED_GOLDEN_APPLE.select().filter(player.currentScreenHandler.slots).firstOrNull()
        }

        // If requested item is missing, fallback to totem if permitted
        if (slot == null && targetItem != Items.TOTEM_OF_UNDYING && fallbackToTotem) {
            slot = Items.TOTEM_OF_UNDYING.select().filter(player.currentScreenHandler.slots).firstOrNull()
        }

        if (slot == null) return
        if (player.offHandStack.item == slot.stack.item) return

        val cursor = player.currentScreenHandler.cursorStack
        val emptySlot = player.currentScreenHandler.slots.findLast { !cursor.isEmpty && it.canInsert(cursor) }

        inventoryRequest {
            emptySlot?.let { pickup(it.id, 0) }
            swapWithHotbar(slot.id, 40)
        }.submit()
    }

    enum class Reason(val check: SafeContext.() -> Boolean) {
        Health({ player.fullHealth < minimumHealth }),
        Creeper({ creeper && fastEntitySearch<CreeperEntity>(15.0).any {
            it.getLerpedFuseTime(mc.tickDeltaF) > 0.0
                    && it.pos.distanceTo(player.pos) <= 5.0
        } }),
        Player({ players && fastEntitySearch<PlayerEntity>(minPlayerDistance.toDouble()).any { otherPlayer ->
            otherPlayer != player
                    && player.distanceTo(otherPlayer) <= minPlayerDistance
                    && (!friends || !FriendHandler.isFriend(otherPlayer.uuid))
        } }),
        EndCrystal({ crystals && hasDeadlyCrystal() }),
        FallDamage({ falls && (isFallDeadly() || player.fallDistance > fallDistance) }),
        Elytra({ elytraSafety && player.isGliding && (player.velocity.lengthSquared() > 0.8 || player.y < player.world.bottomY + 25) })
    }
}
