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
import com.lambda.interaction.construction.result.ComparableResult
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task.Companion.buildTask
import com.lambda.util.item.ItemStackUtils.equal
import net.minecraft.item.ItemStack

data object CreativeContainer : MaterialContainer(Rank.CREATIVE) {
    override var stacks = emptyList<ItemStack>()
    override val name = "Creative"

    override fun available(selection: StackSelection): Int =
        if (mc.player?.isCreative == true && selection.optimalStack != null) Int.MAX_VALUE else 0

    override fun spaceLeft(selection: StackSelection) = Int.MAX_VALUE

    override fun deposit(selection: StackSelection) = buildTask("CreativeDeposit") {
        if (!player.isCreative) {
            // ToDo: Maybe switch gamemode?
            throw NotInCreativeModeException()
        }

        interaction.clickCreativeStack(
            ItemStack.EMPTY,
            36 + player.inventory.selectedSlot
        )
    }

    // Withdraws items from the creative menu to the player's main hand
    override fun withdraw(selection: StackSelection) = buildTask("CreativeWithdraw") {
        selection.optimalStack?.let { optimalStack ->
            if (player.mainHandStack.equal(optimalStack)) return@buildTask

            if (!player.isCreative) {
                // ToDo: Maybe switch gamemode?
                throw NotInCreativeModeException()
            }

            interaction.clickCreativeStack(
                optimalStack,
                36 + player.inventory.selectedSlot
            )
            return@buildTask
        }

        throw NoOptimalStackException()
    }

    class NotInCreativeModeException : IllegalStateException("Insufficient permission: not in creative mode")
    class NoOptimalStackException : IllegalStateException("Cannot move item: no optimal stack")
}
