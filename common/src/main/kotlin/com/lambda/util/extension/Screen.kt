package com.lambda.util.extension

import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

val ScreenHandler.containerSlots: List<Slot> get() = slots.filter { it.inventory !is PlayerInventory }
val ScreenHandler.inventorySlots: List<Slot> get() = slots.filter { it.inventory is PlayerInventory }

val ScreenHandler.containerStacks: List<ItemStack> get() = containerSlots.map { it.stack }
val ScreenHandler.inventoryStacks: List<ItemStack> get() = inventorySlots.map { it.stack }
