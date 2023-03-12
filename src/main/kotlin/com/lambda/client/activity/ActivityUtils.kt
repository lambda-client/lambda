package com.lambda.client.activity

import net.minecraft.inventory.ItemStackHelper
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList

fun getShulkerInventory(stack: ItemStack): NonNullList<ItemStack>? {
    if (stack.item !is ItemShulkerBox) return null

    val shulkerInventory = NonNullList.withSize(27, ItemStack.EMPTY)

    stack.tagCompound?.getCompoundTag("BlockEntityTag")?.let {
        if (it.hasKey("Items", 9)) {
            ItemStackHelper.loadAllItems(it, shulkerInventory)
            return shulkerInventory
        }
    }

    return shulkerInventory
}

val slotFilterFunction = { item: Item, metadata: Int?, predicateStack: (ItemStack) -> Boolean ->
    { slot: Slot -> item == slot.stack.item && predicateStack(slot.stack) && (metadata == null || metadata == slot.stack.metadata) }
}