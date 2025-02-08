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

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.transfer.InventoryChanges
import com.lambda.interaction.material.transfer.InventoryTransaction
import com.lambda.util.player.SlotUtils.clickSlot
import net.minecraft.screen.slot.SlotActionType

class ClickSlotTransaction @Ta5kBuilder constructor(
    private val slotId: Int,
    private val button: Int,
    private val actionType: SlotActionType,
) : InventoryTransaction() {
    override val name: String get() = "Click slot #$slotId with action $actionType and button $button"

    override fun SafeContext.onStart() {
        changes = InventoryChanges(player.currentScreenHandler.slots)
        clickSlot(slotId, button, actionType)
        changes.detectChanges()
        success(changes)
    }
}