package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.QuickMoveSlot
import com.lambda.client.activity.activities.inventory.SwapWithSlot
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.getSlots
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PullItemsFromContainer( // ToDo: fix take for full inv
    private val item: Item,
    private val amount: Int, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true }
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val toMoveSlots = player
            .openContainer
            .inventorySlots
            .filter { slot ->
                slot.stack.item == item && predicateItem(slot.stack)
            }

        if (toMoveSlots.isEmpty()) {
            success()
            return
        }

        val remainingSlots = if (amount == 0) toMoveSlots else toMoveSlots.take(amount)

        val playerInventory = player.openContainer.getSlots(27..62)

        remainingSlots.forEach { fromSlot ->
            if (playerInventory.countEmpty() > 0) {
                addSubActivities(QuickMoveSlot(fromSlot))
                return@forEach
            }

            playerInventory.firstOrNull { slot ->
                BuildTools.ejectList.contains(slot.stack.item.registryName.toString())
            }?.let {
                val firstHotbarSlot = player.openContainer.inventorySlots[54].slotNumber

                addSubActivities(
                    SwapWithSlot(it, firstHotbarSlot),
                    SwapWithSlot(fromSlot, firstHotbarSlot)
                )
            } ?: run {
                failedWith(NoSpaceLeftInInventoryException())
            }
        }
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
}