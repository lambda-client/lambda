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

package com.lambda.module.modules.player

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

object EasyTrash : Module(
    "EasyTrash",
    "Automatically trashes unwanted items",
    ModuleTag.PLAYER
) {

    private val trashNetherrack by setting("Trash Netherrack", true)

    /**
     * Called when a slot is clicked in a screen handler.
     * Returns false if the click was handled and should be canceled.
     */
    @JvmStatic
    fun onClick(slotIndex: Int, button: Int, actionType: SlotActionType): Boolean =
        runSafe {
            if (!isEnabled) return true
            if (actionType != SlotActionType.QUICK_MOVE || button != 0) return true
            var screenHandler = player.currentScreenHandler

            if (!trashNetherrack) return true
            if (screenHandler is GenericContainerScreenHandler) {
                val slot = screenHandler.getSlot(slotIndex)
                val rows = screenHandler.rows

                if (slotIndex >= rows * 9) return true // Not in the container

                val inventorySlots = screenHandler.slots.subList(rows * 9, screenHandler.slots.size)
                val freeInventorySlot = inventorySlots.any { !it.hasStack() }
                if (freeInventorySlot) return true

                val netherrackSlot: Slot? = inventorySlots.firstOrNull { it.stack.item == Items.NETHERRACK }
                if (netherrackSlot == null) return true

                mc.interactionManager?.clickSlot(screenHandler.syncId, slot.id, 0, SlotActionType.PICKUP, player)
                mc.interactionManager?.clickSlot(screenHandler.syncId, netherrackSlot.id, 0, SlotActionType.PICKUP, player)
                mc.interactionManager?.clickSlot(screenHandler.syncId, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP, player)

                return false
            }
            return true
        } ?: true
}