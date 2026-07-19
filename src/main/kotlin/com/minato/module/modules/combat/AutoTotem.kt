
package com.minato.module.modules.combat

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.handlers.FriendHandler
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.combat.CombatUtils.hasDeadlyCrystal
import com.minato.util.combat.DamageUtils.isFallDeadly
import com.minato.util.extension.fullHealth
import com.minato.util.extension.tickDeltaF
import com.minato.util.world.fastEntitySearch
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

@Suppress("unused")
object AutoTotem : Module(
    name = "AutoTotem",
    description = "Swaps the your off-hand item to a totem",
    tag = ModuleTag.COMBAT,
	modulePriority = 100
) {
	private val always by setting("Always", true, "Always attempt to keep a totem in offhand")
	private val ignoreWhenHolding by setting("Ignore When Holding", false, "Ignore swapping to offhand when already holding a totem")
	private val minimumHealth by setting("Min Health", 10, 6..36, 1, "Set the minimum health threshold to swap", unit = " half-hearts") { !always }
    private val falls by setting("Falls", true, "Swap if the player will die of fall damage") { !always }
    private val fallDistance by setting("Falls Time", 10, 0..30, 1, "Number of blocks fallen before swapping", unit = " blocks") { !always && falls }
    private val crystals by setting("Crystals", true, "Swap if an End Crystal explosion would be lethal") { !always }
    private val creeper by setting("Creepers", true, "Swap when an ignited Creeper is nearby") { !always }
    private val players by setting("Players", false, "Swap if a nearby player is detected within the set distance") { !always }
    private val minPlayerDistance by setting("Player Distance", 64, 32..128, 4, "Set the distance to detect players to swap") { !always && players }
    private val friends by setting("Friends", false, "Exclude friends from triggering player-based swaps") { !always && players }

    init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::inventoryConfig)
			}

        listen<TickEvent.Pre> {
            if (!always && Reason.entries.none { it.check(this) }) return@listen

            if ((!ignoreWhenHolding || !player.isHolding(Items.TOTEM_OF_UNDYING)) && player.offHandStack.item != Items.TOTEM_OF_UNDYING) {
                Items.TOTEM_OF_UNDYING.select()
	                .filterSlots(player.currentScreenHandler.slots)
	                .takeIf { it.isNotEmpty() }
	                ?.let { totems ->
		                val cursor = player.currentScreenHandler.cursorStack
		                val targetSlot = player.currentScreenHandler.slots
			                .findLast { !cursor.isEmpty && it.canInsert(cursor) }

						inventoryRequest {
							targetSlot?.let { pickup(it.id, 0) }
							swap(totems.first().id, 40)
						}.submit()
	                }
            }
        }
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
        FallDamage({ falls && isFallDeadly() && player.fallDistance > fallDistance })
    }
}
