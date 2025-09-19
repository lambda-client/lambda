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
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.FoodComponent
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import java.awt.event.InputEvent

class EatTask @Ta5kBuilder constructor(
    val config: EatConfig = TaskFlowModule.eat,
    val inventory: InventoryConfig = TaskFlowModule.inventory,
) : Task<Unit>() {
    override val name: String
        get() = "Eating ${eatStack ?: "nothing"}"

    private var eatStack: ItemStack? = null
    private val selection get() = foodSelector(config)

    init {
        listen<TickEvent.Input.Pre> {
            val nutrition = eatStack?.item?.components?.get(DataComponentTypes.FOOD)?.nutrition ?: 0
            val wouldBeFull = nutrition + player.hungerManager.foodLevel > 20
            if (!config.eatUntilFull && !shouldEat(config) || wouldBeFull) {
                interaction.stopUsingItem(player)
                success()
                return@listen
            }

            if (!selection.matches(player.mainHandStack)) {
                selection.transfer(MainHandContainer, inventory)
                    ?.execute(this@EatTask) ?: failure("No food found")
                return@listen
            }
            eatStack = player.mainHandStack

            (interaction.interactItem(player, Hand.MAIN_HAND) as? ActionResult.Success)?.let {
                if (it.swingSource == ActionResult.SwingSource.CLIENT) player.swingHand(Hand.MAIN_HAND)
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
            }
        }
    }

    companion object {
        fun foodSelector(config: EatConfig) = selectStack {
            when {
                config.selectionMode == EatConfig.SelectionMode.Whitelist -> isOneOfItems(config.whitelist)
                else -> isNoneOfItems(config.blacklist)
            } and isFood()
        }

        @Ta5kBuilder
        fun eat(config: EatConfig) = EatTask(config)

        fun SafeContext.shouldEat(config: EatConfig) =
            player.hungerManager.foodLevel <= config.minFoodLevel

        fun hasFood(config: EatConfig, inventory: InventoryConfig) =
            foodSelector(config).transfer(MainHandContainer, inventory) != null
    }
}
