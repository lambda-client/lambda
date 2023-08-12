package com.lambda.client.activity.activities.storage.types

import com.lambda.client.activity.getShulkerInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack

data class ItemInfo(
    val item: Item,
    val number: Int = 1, // 0 = all
    val itemStack: ItemStack? = null,
    val predicate: (ItemStack) -> Boolean = { true },
    val metadata: Int? = null,
    var containedInShulker: Boolean = false
) {
    val optimalStack: ItemStack
        get() = ItemStack(item, number, metadata ?: 0)

    val slotFilter = { slot: Slot ->
        if (containedInShulker) {
            slot.stack.item is ItemShulkerBox && getShulkerInventory(slot.stack)?.any {
                stackFilter(it)
            } == true
        } else stackFilter(slot.stack)
    }

    val stackFilter = { stack: ItemStack ->
        itemStack == null
            && item == stack.item
            && predicate(stack)
            && (metadata == null || metadata == stack.metadata)
            || (itemStack != null && ItemStack.areItemStacksEqual(itemStack, stack))
    }

    override fun toString() = "ItemInfo(item=$item, number=$number, itemStack=$itemStack, metadata=$metadata, containedInShulker=$containedInShulker)"
}