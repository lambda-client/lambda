/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.util.player.SlotUtils.combined
import net.minecraft.item.ItemStack

object InventoryContainer : MaterialContainer(Rank.INVENTORY) {
    override var stacks: List<ItemStack>
        get() = mc.player?.combined ?: emptyList()
        set(_) {}
    override val name = "Inventory"

    override fun withdraw(selection: StackSelection) = emptyTask("WithdrawFromInventory")

    override fun deposit(selection: StackSelection) = emptyTask("DepositToInventory")
}
