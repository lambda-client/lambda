/*
 * Copyright 2026 Lambda
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

    val ClientPlayerEntity.inventorySlots: List<Slot>
        get() =
            if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) emptyList()
            else if (currentScreenHandler === playerScreenHandler) allSlots.subList(4, 31)
            else allSlots.subList(0, 27)

    val ClientPlayerEntity.hotbarSlots: List<Slot>
        get() =
            if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) allSlots
            else if (currentScreenHandler === playerScreenHandler) allSlots.subList(31, 40)
            else allSlots.subList(27, 36)

    val ClientPlayerEntity.hotbarAndInventorySlots: List<Slot>
        get() =
            if (currentScreenHandler is CreativeInventoryScreen.CreativeScreenHandler) allSlots
            else if (currentScreenHandler === playerScreenHandler) allSlots.subList(4, 40)
            else allSlots.subList(0, 36)

    val ClientPlayerEntity.mainHandSlots: List<Slot> get() = listOf(hotbarSlots[inventory.selectedSlot])
    val ClientPlayerEntity.offHandSlots: List<Slot> get() = if (currentScreenHandler === playerScreenHandler) listOf(allSlots.last()) else emptyList()

    val ClientPlayerEntity.allStacks: List<ItemStack> get() = hotbarAndInventoryStacks + equipmentStacks
    val ClientPlayerEntity.armorStacks: List<ItemStack> get() = inventory.equipment.map.filter { it.key.index in 1..4 }.map { it.value }
    val ClientPlayerEntity.equipmentStacks: List<ItemStack> get() = inventory.equipment.map.map { it.value }
    val ClientPlayerEntity.hotbarStacks: List<ItemStack> get() = inventory.mainStacks.slice(0..8)
    val ClientPlayerEntity.inventoryStacks: List<ItemStack> get() = inventory.mainStacks.slice(9..35)
    val ClientPlayerEntity.hotbarAndInventoryStacks: List<ItemStack> get() = inventory.mainStacks

    fun SafeContext.clickSlot(slotId: Int, button: Int, actionType: SlotActionType) {
        val syncId = player.currentScreenHandler?.syncId ?: return
        interaction.clickSlot(syncId, slotId, button, actionType, player)
    }

    infix fun Slot.matches(slot: Slot) =
        index == slot.index &&
                inventory::class == slot.inventory::class &&
                id == slot.id &&
                x == slot.x &&
                y == slot.y
}
