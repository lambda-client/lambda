
package com.minato.util.extension

import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.CraftingInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

val ScreenHandler.containerSlots: List<Slot> get() = slots.filter { it.inventory is SimpleInventory }
val ScreenHandler.playerSlots: List<Slot> get() = slots.filter { it.inventory is PlayerInventory }
val ScreenHandler.craftingSlots: List<Slot> get() = slots.filter { it.inventory is CraftingInventory }

val ScreenHandler.containerStacks: List<ItemStack> get() = containerSlots.map { it.stack }
val ScreenHandler.playerStacks: List<ItemStack> get() = playerSlots.map { it.stack }
