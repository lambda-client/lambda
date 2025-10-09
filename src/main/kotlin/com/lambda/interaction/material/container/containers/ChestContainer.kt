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
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.material.transfer.SlotTransfer.Companion.deposit
import com.lambda.interaction.material.transfer.SlotTransfer.Companion.withdraw
import com.lambda.task.tasks.OpenContainer
import com.lambda.util.Communication.info
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

data class ChestContainer(
    override var stacks: List<ItemStack>,
    val blockPos: BlockPos,
    val containedInStash: StashContainer? = null
) : MaterialContainer(Rank.CHEST) {
    override val description =
        buildText {
            literal("Chest at ")
            highlighted(blockPos.toShortString())
            containedInStash?.let { stash ->
                literal(" (contained in ")
                highlighted(stash.name)
                literal(")")
            }
        }

//    override fun prepare() =
//        moveIntoEntityRange(blockPos).onSuccess { _, _ ->
////            when {
////                ChestBlock.hasBlockOnTop(world, blockPos) -> breakBlock(blockPos.up())
////                ChestBlock.hasCatOnTop(world, blockPos) -> kill(cat)
////            }
//            if (ChestBlock.isChestBlocked(world, blockPos)) {
//                throw ChestBlockedException()
//            }
//        }

    context(automated: Automated)
    override fun withdraw(selection: StackSelection) =
        OpenContainer(blockPos, automated)
            .then {
                info("Withdrawing $selection from ${it.type}")
                withdraw(it, selection)
            }

    context(automated: Automated)
    override fun deposit(selection: StackSelection) =
        OpenContainer(blockPos, automated)
            .then {
                info("Depositing $selection to ${it.type}")
                deposit(it, selection)
            }
}
