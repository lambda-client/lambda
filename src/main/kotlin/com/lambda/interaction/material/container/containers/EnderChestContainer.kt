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

package com.lambda.interaction.material.container.containers

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.Task
import com.lambda.util.Communication.info
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

object EnderChestContainer : MaterialContainer(Rank.ENDER_CHEST) {
    override var stacks = emptyList<ItemStack>()
    private var placePos: BlockPos? = null

    override val description = buildText { literal("Ender Chest") }

//    override fun prepare(): Task<*> {
//        TODO("Not yet implemented")
//    }
//        findBlock(Blocks.ENDER_CHEST).onSuccess { pos ->
//            moveIntoEntityRange(pos)
//            placePos = pos
//        }.onFailure {
//            acquireStack(Items.ENDER_CHEST.select()).onSuccess { _, stack ->
//                placeContainer(stack).onSuccess { _, pos ->
//                    placePos = pos
//                }
//            }
//        }

    class EnderchestWithdrawal @Ta5kBuilder constructor(selection: StackSelection) : Task<Unit>() {
        override val name = "Withdrawing $selection from ender chest"

        override fun SafeContext.onStart() {
            info("Not yet implemented")
            success()
        }
    }

    context(automated: Automated)
    override fun withdraw(selection: StackSelection) = EnderchestWithdrawal(selection)

    class EnderchestDeposit @Ta5kBuilder constructor(selection: StackSelection) : Task<Unit>() {
        override val name = "Depositing $selection into ender chest"

        override fun SafeContext.onStart() {
            info("Not yet implemented")
            success()
        }
    }

    context(automated: Automated)
    override fun deposit(selection: StackSelection) = EnderchestDeposit(selection)
}
