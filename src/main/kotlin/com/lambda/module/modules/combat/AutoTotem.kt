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

import com.lambda.config.groups.InventorySettings
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.containers.OffHandContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.util.Communication.info
import com.lambda.util.NamedEnum
import com.lambda.util.combat.CombatUtils.hasDeadlyCrystal
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.tickDelta
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

object AutoTotem : Module(
    name = "AutoTotem",
    description = "Swaps the your off-hand item to a totem",
    tag = ModuleTag.COMBAT,
) {
    private val log by setting("Log Message", true).group(Group.General)

    private val minimumHealth by setting("Min Health", 10, 6..36, 1, "Set the minimum health threshold to swap", unit = " half-hearts").group(Group.General)
    private val falls by setting("Falls", true, "Swap if the player will die of fall damage").group(Group.General)
    private val fallDistance by setting("Falls Time", 10, 0..30, 1, "Number of blocks fallen before swapping", unit = " blocks") { falls }.group(Group.General)
    private val crystals by setting("Crystals", true, "Swap if an End Crystal explosion would be lethal").group(Group.General)
    private val creeper by setting("Creepers", true, "Swap when an ignited Creeper is nearby").group(Group.General)
    private val players by setting("Players", false, "Swap if a nearby player is detected within the set distance").group(Group.General)
    private val minPlayerDistance by setting("Player Distance", 64, 32..128, 4, "Set the distance to detect players to swap") { players }.group(Group.General)
    private val friends by setting("Friends", false, "Exclude friends from triggering player-based swaps") { players }.group(Group.General)

    override val inventoryConfig = InventorySettings(this, Group.Inventory)

    init {
        listen<TickEvent.Pre> {
            if (Reason.entries.none { it.check(this) }) return@listen

            if (!player.isHolding(Items.TOTEM_OF_UNDYING)) {
                Items.TOTEM_OF_UNDYING.select()
                    .transfer(OffHandContainer)
                    ?.finally { if (log) info("Swapped the off-hand item with a totem") }
                    ?.run()
            }
        }
    }

    enum class Reason(val check: SafeContext.() -> Boolean) {
        Health({ player.fullHealth < minimumHealth }),
        Creeper({ creeper && fastEntitySearch<CreeperEntity>(15.0).any {
            it.getLerpedFuseTime(mc.tickDelta) > 0.0
                    && it.pos.distanceTo(player.pos) <= 5.0
        } }),
        Player({ players && fastEntitySearch<PlayerEntity>(minPlayerDistance.toDouble()).any { otherPlayer ->
            otherPlayer != player
                    && player.distanceTo(otherPlayer) <= minPlayerDistance
                    && (!friends || !FriendManager.isFriend(otherPlayer.uuid))
        } }),
        EndCrystal({ crystals && hasDeadlyCrystal() }),
        FallDamage({ falls && isFallDeadly() && player.fallDistance > fallDistance })
    }

    enum class Group(override val displayName: String): NamedEnum {
        General("General"),
        Inventory("Inventory"),
    }
}
