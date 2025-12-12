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
import com.lambda.interaction.material.transfer.SlotTransfer.Companion.deposit
import com.lambda.interaction.material.transfer.SlotTransfer.Companion.withdraw
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainer
import com.lambda.task.tasks.PlaceContainer
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerStack: ItemStack,
) : MaterialContainer(Rank.ShulkerBox) {
    override val description =
        buildText {
            highlighted(shulkerStack.name.string)
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot ")
            highlighted("$slotInContainer")
        }

    private val slotInContainer: Int get() = containedIn.stacks.indexOf(shulkerStack)

    class ShulkerWithdraw(
        private val selection: StackSelection,
        private val shulkerStack: ItemStack,
        automated: Automated
    ) : Task<Unit>(), Automated by automated {
        override val name = "Withdraw $selection from ${shulkerStack.name.string}"

        override fun SafeContext.onStart() {
            PlaceContainer(shulkerStack, this@ShulkerWithdraw).then { placePos ->
                OpenContainer(placePos, this@ShulkerWithdraw).then { screen ->
                    withdraw(screen, selection).then {
                        breakAndCollectBlock(placePos).finally {
                            success()
                        }
                    }
                }
            }.execute(this@ShulkerWithdraw)
        }
    }

    context(automated: Automated)
    override fun withdraw(selection: StackSelection) = ShulkerWithdraw(selection, shulkerStack, automated)

    class ShulkerDeposit(
        private val selection: StackSelection,
        private val shulkerStack: ItemStack,
        automated: Automated
    ) : Task<Unit>(), Automated by automated {
        override val name = "Deposit $selection into ${shulkerStack.name.string}"

        override fun SafeContext.onStart() {
            PlaceContainer(shulkerStack, this@ShulkerDeposit).then { placePos ->
                OpenContainer(placePos, this@ShulkerDeposit).then { screen ->
                    deposit(screen, selection).then {
                        breakAndCollectBlock(placePos).finally {
                            success()
                        }
                    }
                }
            }.execute(this@ShulkerDeposit)
        }
    }

    context(automated: Automated)
    override fun deposit(selection: StackSelection) = ShulkerDeposit(selection, shulkerStack, automated)

    context(safeContext: SafeContext)
    override fun isImmediatelyAccessible() = false
}
