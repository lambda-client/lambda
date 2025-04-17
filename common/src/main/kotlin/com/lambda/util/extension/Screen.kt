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

package com.lambda.util.extension

import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.CraftingInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

val ScreenHandler.containerSlots: List<Slot> get() = slots.filter { it.inventory is SimpleInventory }
val ScreenHandler.inventorySlots: List<Slot> get() = slots.filter { it.inventory is PlayerInventory }
val ScreenHandler.craftingSlots: List<Slot> get() = slots.filter { it.inventory is CraftingInventory }

val ScreenHandler.containerStacks: List<ItemStack> get() = containerSlots.map { it.stack }
val ScreenHandler.inventoryStacks: List<ItemStack> get() = inventorySlots.map { it.stack }
