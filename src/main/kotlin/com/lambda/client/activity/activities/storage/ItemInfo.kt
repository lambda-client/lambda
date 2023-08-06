package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.getShulkerInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack

data class ItemInfo(
    val item: Item,
    val number: Int = 1, // 0 = all
    val predicate: (ItemStack) -> Boolean = { true },
    val metadata: Int? = null,
    val containedInShulker: Boolean = false
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
        item == stack.item
            && predicate(stack)
            && (metadata == null || metadata == stack.metadata)
    }

    override fun toString() = "ItemInfo(item=$item, number=$number, metadata=$metadata, containedInShulker=$containedInShulker)"
}