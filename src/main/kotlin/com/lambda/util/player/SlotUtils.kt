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

package com.lambda.util.player

import com.lambda.context.SafeContext
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.ArmorSlot
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

object SlotUtils {
    val ClientPlayerEntity.allSlots get() = currentScreenHandler.slots.filter { it.inventory is PlayerInventory }
    val ClientPlayerEntity.armorSlots get() = allSlots.filterIsInstance<ArmorSlot>()
    val ClientPlayerEntity.inventorySlots: List<Slot> get() {
        return if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) emptyList()
        else if (currentScreenHandler === playerScreenHandler) allSlots.subList(4, 31)
        else allSlots.subList(0, 27)
    }
    val ClientPlayerEntity.hotbarSlots: List<Slot> get() {
        return if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) allSlots
        else if (currentScreenHandler === playerScreenHandler) allSlots.subList(30, 39)
        else allSlots.subList(26, 35)
    }
    val ClientPlayerEntity.hotbarAndInventorySlots: List<Slot> get() {
        return if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) allSlots
        else if (currentScreenHandler === playerScreenHandler) allSlots.subList(4, 39)
        else allSlots.subList(0, 35)
    }

    val ClientPlayerEntity.allStacks: List<ItemStack> get() = hotbarAndInventoryStacks + equipmentStacks
    val ClientPlayerEntity.equipmentStacks: List<ItemStack> get() = inventory.equipment.map.map { it.value }
    val ClientPlayerEntity.hotbarStacks: List<ItemStack> get() = inventory.mainStacks.slice(0..8)
    val ClientPlayerEntity.inventoryStacks: List<ItemStack> get() = inventory.mainStacks.slice(9..35)
    val ClientPlayerEntity.hotbarAndInventoryStacks: List<ItemStack> get() = inventory.mainStacks

    fun SafeContext.clickSlot(slotId: Int, button: Int, actionType: SlotActionType) {
        val syncId = player.currentScreenHandler?.syncId ?: return
        interaction.clickSlot(syncId, slotId, button, actionType, player)
    }
}
