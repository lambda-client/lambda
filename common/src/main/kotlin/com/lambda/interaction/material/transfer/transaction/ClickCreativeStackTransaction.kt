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

package com.lambda.interaction.material.transfer.transaction

import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.transfer.InventoryTransaction
import com.lambda.util.player.SlotUtils.clickSlot
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.SlotActionType

class ClickCreativeStackTransaction @Ta5kBuilder constructor(
    private val stack: ItemStack,
    private val slotId: Int,
) : InventoryTransaction() {
    override val name: String get() = "Creating stack $stack at #$slotId"
    private var confirming = false

    init {
    	listen<TickEvent.Pre> {
            if (confirming) return@listen

            interaction.clickCreativeStack(stack, slotId)
            confirming = true
        }

        listen<InventoryEvent.SlotUpdate> {
            if (it.slot != slotId) return@listen
            finish()
        }
    }
}