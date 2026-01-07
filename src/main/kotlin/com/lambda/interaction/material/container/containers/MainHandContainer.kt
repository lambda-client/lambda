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

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.inventory.InventoryRequest
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.player.SlotUtils.mainHandSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object MainHandContainer : MaterialContainer(Rank.MainHand) {
    context(safeContext: SafeContext)
    override val slots: List<Slot>
        get() = safeContext.player.mainHandSlots
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override val swapMethodPriority = 10

    override val description = buildText { literal("MainHand") }

    context(safeContext: SafeContext)
    override fun InventoryRequest.InvRequestBuilder.transfer(from: Slot, toHere: Slot) {
        swap(from.id, safeContext.player.inventory.selectedSlot)
    }
}
