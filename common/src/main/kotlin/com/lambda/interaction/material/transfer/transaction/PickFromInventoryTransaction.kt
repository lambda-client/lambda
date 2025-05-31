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

package com.lambda.interaction.material.transfer.transaction

import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.transfer.InventoryTransaction
import net.minecraft.screen.slot.SlotActionType

class PickFromInventoryTransaction @Ta5kBuilder constructor(
    val slot: Int,
) : InventoryTransaction() {
    override val name: String get() = "Picking from slot #$slot"
    private var confirming = false

    init {
        listen<TickEvent.Pre> {
            if (confirming) return@listen

            interaction.clickSlot(0, slot, player.inventory.selectedSlot, SlotActionType.SWAP, player)
            confirming = true
        }

        listen<InventoryEvent.SlotUpdate> {
            if (it.slot != slot) return@listen
            finish()
        }
    }
}
