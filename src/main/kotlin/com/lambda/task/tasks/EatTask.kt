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

package com.lambda.task.tasks

import com.lambda.config.groups.EatConfig
import com.lambda.config.groups.EatConfig.Companion.reasonEating
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.util.item.ItemUtils.nutrition
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

class EatTask @Ta5kBuilder constructor(
    val config: EatConfig,
    val inventory: InventoryConfig
) : Task<Unit>() {
    override val name: String
        get() = reason.message(eatStack ?: ItemStack.EMPTY)

    private var eatStack: ItemStack? = null
    private var reason = EatConfig.Reason.None
    private var holdingUse = false

    override fun SafeContext.onStart() {
        reason = reasonEating(config)
    }

    init {
        listen<TickEvent.Input.Pre> {
            if (holdingUse && !reason.shouldKeepEating(config, eatStack)) {
                mc.options.useKey.isPressed = false
                holdingUse = false
                interaction.stopUsingItem(player)
                success()
                return@listen
            }

            if (player.isUsingItem) {
                if (!holdingUse) {
                    mc.options.useKey.isPressed = true
                    holdingUse = true
                }
                return@listen
            }

            val foodFinder = reason.selector(config)
            if (!foodFinder.matches(player.mainHandStack)) {
                if (holdingUse) {
                    mc.options.useKey.isPressed = false
                    holdingUse = false
                }
                foodFinder.transfer(MainHandContainer, inventory)
                    ?.execute(this@EatTask) ?: failure("No food found")
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
        fun eat(
            config: EatConfig = TaskFlowModule.eat,
            inventory: InventoryConfig = TaskFlowModule.inventory,
        ) = EatTask(config, inventory)
    }
}
