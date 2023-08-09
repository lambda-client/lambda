package com.lambda.client.activity.activities.storage.types

import com.lambda.client.activity.getShulkerInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class ItemOrder(override val action: ContainerAction, val item: Item, override val amount: Int) : ContainerOrder {
    override val filter = { slot: Slot ->
        slot.stack.item == item
    }
}

class ShulkerOrder(override val action: ContainerAction, val item: Item, override val amount: Int) : ContainerOrder {
    override val filter = { slot: Slot ->
        getShulkerInventory(slot.stack)?.any { it.item == item } == true
    }
    val findShulkerToPush = { slot: Slot ->
        getShulkerInventory(slot.stack)?.let { inventory ->
            if (inventory.all { it.item == item || it.isEmpty }) {
                val storableItems = inventory.sumOf {
//                    if (it.isEmpty) item.itemStackLimit else it.maxStackSize - it.count
                    if (it.isEmpty) item.itemStackLimit else 0
                }

                if (storableItems > 0) slot to storableItems else null
            } else null
        }
    }
    val findShulkerToPull = { slot: Slot ->
        getShulkerInventory(slot.stack)?.let { inventory ->
            val usableItems = inventory.sumOf { if (it.item == item) it.count else 0 }

            if (usableItems > 0) slot to usableItems else null
        }
    }
}

class SelectionOrder(override val action: ContainerAction, selection: (Slot) -> Boolean, override val amount: Int) : ContainerOrder {
    override val filter = selection
}

class StackOrder(override val action: ContainerAction, val itemStack: ItemStack, override val amount: Int) : ContainerOrder {
    override val filter = { slot: Slot ->
        ItemStack.areItemStacksEqual(itemStack, slot.stack)
    }
}

class GeneralOrder(override val action: ContainerAction, selection: Selection, override val amount: Int) : ContainerOrder {
    override val filter = selection.slotFilter

    enum class Selection(val slotFilter: (Slot) -> Boolean) {
        FULL_SHULKERS({ slot ->
            getShulkerInventory(slot.stack)?.none { it.isEmpty } == true
        }),
        EMPTY_SHULKERS({ slot ->
            getShulkerInventory(slot.stack)?.all { it.isEmpty } == true
        }),
        EVERYTHING({ true })
    }
}

enum class ContainerAction {
    PUSH, PULL
}

/**
 * Implemented by [ItemOrder], [ShulkerOrder], [SelectionOrder], [StackOrder], [GeneralOrder]
 */
interface ContainerOrder {
    val action: ContainerAction
    val amount: Int
    val filter: (Slot) -> Boolean
}