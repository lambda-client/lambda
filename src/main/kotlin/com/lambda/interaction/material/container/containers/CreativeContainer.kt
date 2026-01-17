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

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.inventory.InventoryRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SingleStackInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.slot.Slot

data object CreativeContainer : MaterialContainer(Rank.Creative) {
    context(_: SafeContext)
    override val slots: List<Slot>
	    get() = emptyList()
	override var stacks = emptyList<ItemStack>()

    override val swapMethodPriority = 11

    override val description = buildText { literal("Creative") }

    context(safeContext : SafeContext)
    override fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        clickCreativeStack(fromHere.stack, toSlot.id)
        safeContext.player.currentScreenHandler.slots[toSlot.id].stack = fromHere.stack
    }

    context(safeContext: SafeContext)
    override fun getSlot(stackSelection: StackSelection) =
        stackSelection.optimalStack?.let { stack ->
            Slot(
                object : SingleStackInventory {
                    override fun getStack() = stack
                    override fun setStack(stack: ItemStack?) {}
                    override fun markDirty() {}
                    override fun canPlayerUse(player: PlayerEntity?) = false
                },
                0, 0, 0
            )
        }

    context(safeContext: SafeContext)
    override fun materialAvailable(selection: StackSelection): Int =
        if (safeContext.player.isCreative && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

    context(safeContext: SafeContext)
    override fun spaceAvailable(selection: StackSelection): Int =
        if (safeContext.player.isCreative && correctScreenHandler && selection.optimalStack != null) Int.MAX_VALUE else -1

    context(safeContext: SafeContext)
    private val correctScreenHandler
        get() = safeContext.player.currentScreenHandler is PlayerScreenHandler || safeContext.player.currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler
}
