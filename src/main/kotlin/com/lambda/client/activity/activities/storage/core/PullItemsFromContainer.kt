package com.lambda.client.activity.activities.storage.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.QuickMoveSlot
import com.lambda.client.activity.activities.inventory.core.SwapWithSlot
import com.lambda.client.activity.slotFilterFunction
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.getSlots
import net.minecraft.inventory.Container
import net.minecraft.inventory.ContainerShulkerBox
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class PullItemsFromContainer( // ToDo: fix take for full inv
    private val item: Item,
    private val predicateStack: (ItemStack) -> Boolean = { true },
    private val metadata: Int? = null,
    private val amount: Int, // 0 = all
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val containerInventory: List<Slot>
        val playerInventory: List<Slot>

        when (val container = player.openContainer) {
            is ContainerShulkerBox -> {
                containerInventory = container.getSlots(0..26)
                playerInventory = container.getSlots(27..62)
            }
            else -> {
                failedWith(ContainerNotKnownException(container))
                return
            }
        }

        val toMoveSlots = containerInventory.filter(slotFilterFunction(item, metadata, predicateStack))

        if (toMoveSlots.isEmpty()) {
            failedWith(NoItemFoundException())
            return
        }

        val remainingSlots = if (amount == 0) toMoveSlots else toMoveSlots.take(amount)

        if (remainingSlots.isEmpty()) {
            failedWith(NoSpaceLeftInInventoryException())
            return
        }

        remainingSlots.forEach { fromSlot ->
            if (playerInventory.countEmpty() > 0) {
                addSubActivities(QuickMoveSlot(fromSlot))
                return@forEach
            }

            val ejectableSlots = playerInventory.filter { slot ->
                BuildTools.ejectList.contains(slot.stack.item.registryName.toString())
            }

            if (ejectableSlots.isEmpty()) {
                failedWith(NoSpaceLeftInInventoryException())
                return@forEach
            }

            ejectableSlots.firstOrNull()?.let {
                // ToDo: Use proper slot reference
                val firstHotbarSlot = player.openContainer.inventorySlots[54].slotNumber

                addSubActivities(
                    SwapWithSlot(it, firstHotbarSlot),
                    SwapWithSlot(fromSlot, firstHotbarSlot)
                )
            }
        }
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
    class NoItemFoundException : Exception("No item found")
    class ContainerNotKnownException(val container: Container) : Exception("Container ${container::class.simpleName} not known")
}