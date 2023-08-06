package com.lambda.client.activity

import com.lambda.client.activity.activities.storage.core.ContainerTransaction
import com.lambda.client.util.items.getSlots
import net.minecraft.inventory.*
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

/**
 * Get the slots of a container.
 * The first list contains the container slots, the second list contains the player slots.
 */
val Container.seperatedSlots: Pair<List<Slot>, List<Slot>>
    get() = when(this) {
        is ContainerShulkerBox -> {
            getSlots(0..26) to getSlots(27..62)
        }
        is ContainerChest -> {
            if (inventory.size == 62) {
                getSlots(0..26) to getSlots(27..62)
            } else {
                getSlots(0..53) to getSlots(54..89)
            }
        }
        else -> {
            throw ContainerTransaction.ContainerNotKnownException(this)
        }
    }