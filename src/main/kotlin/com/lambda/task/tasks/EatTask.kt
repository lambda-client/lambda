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

package com.lambda.task.tasks

import com.lambda.config.blocks.EatConfig
import com.lambda.config.blocks.EatConfig.Companion.reasonEating
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.inventory.container.containers.HotbarContainer
import com.lambda.interaction.inventory.container.containers.InventoryContainer
import com.lambda.interaction.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

class EatTask @Ta5kBuilder constructor(
    automated: Automated
) : Task<Unit>(), Automated by automated {
    override val name: String
        get() = reason.message(eatStack ?: ItemStack.EMPTY)

    private var eatStack: ItemStack? = null
    private var reason = EatConfig.Reason.None
    private var holdingUse = false

    override fun SafeContext.onStart() {
        reason = runSafeAutomated { reasonEating() }
    }

    init {
        listen<TickEvent.Input.Post> {
            if (holdingUse && !reason.shouldKeepEating(eatStack)) {
                mc.options.useKey.isPressed = false
                holdingUse = false
                interaction.stopUsingItem(player)
                success()
                return@listen
            }

            val foodFinder = reason.selector()
            val hotbarSlot = foodFinder.filter(HotbarContainer.slots).firstOrNull()
            if (hotbarSlot != null) {
                val request =
                    hotbarRequest(hotbarSlot.index) {
                        keepTicks(hotbarConfig.keepTicks.coerceAtLeast(1))
                    }.submit()
                if (!request.done) return@listen
            } else {
                val inventorySlot = foodFinder.filter(InventoryContainer.slots).firstOrNull()
                if (inventorySlot != null) runSafeAutomated {
                    InventoryContainer.transfer(foodFinder, HotbarContainer)
                }
                if (holdingUse) {
                    mc.options.useKey.isPressed = false
                    holdingUse = false
                }
                return@listen
            }

            if (player.isUsingItem) {
                if (!holdingUse) {
                    mc.options.useKey.isPressed = true
                    holdingUse = true
                }
                return@listen
            }

            eatStack = player.mainHandStack

            (interaction.interactItem(player, Hand.MAIN_HAND) as? ActionResult.Success)?.let {
                if (it.swingSource == ActionResult.SwingSource.CLIENT) player.swingHand(Hand.MAIN_HAND)
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                mc.options.useKey.isPressed = true
                holdingUse = true
            }
        }
    }

    companion object {
        @Ta5kBuilder
        context(automated: Automated)
        fun eat() = EatTask(automated)
    }
}
