package com.lambda.client.activity.activities.storage.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.QuickMoveSlot
import com.lambda.client.activity.activities.inventory.core.SwapWithSlot
import com.lambda.client.activity.seperatedSlots
import com.lambda.client.activity.slotFilterFunction
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.hotbarSlots
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class ContainerTransaction(
    private val order: Order
) : Activity() {

    enum class Action {
        PULL, PUSH
    }

    data class Order(
        val action: Action,
        val item: Item,
        val slotAmount: Int = 0, // 0 = all
        val predicateStack: (ItemStack) -> Boolean = { true },
        val metadata: Int? = null,
        val predicateSlot: (ItemStack) -> Boolean = { true },
        val containedInShulker: Boolean = false
    )

    override fun SafeClientEvent.onInitialize() {
        val seperatedSlots = player.openContainer.seperatedSlots

        val (fromSlots, toSlots) = if (order.action == Action.PULL) {
            seperatedSlots.let { (first, second) -> Pair(first, second) }
        } else {
            seperatedSlots.let { (first, second) -> Pair(second, first) }
        }

        val toMoveSlots = fromSlots.filter(slotFilterFunction(order))

        if (toMoveSlots.isEmpty()) {
            failedWith(NoItemFoundException())
            return
        }

        if (toMoveSlots.size < order.slotAmount) {
            failedWith(NotEnoughSlotsException())
            return
        }

        val remainingSlots = if (order.slotAmount == 0) toMoveSlots else toMoveSlots.take(order.slotAmount)

        remainingSlots.forEach { fromSlot ->
            if (toSlots.countEmpty() > 0) {
                addSubActivities(QuickMoveSlot(fromSlot))
                return@forEach
            }

            val ejectableSlots = toSlots.filter { slot ->
                BuildTools.ejectList.contains(slot.stack.item.registryName.toString())
            }

            if (ejectableSlots.isEmpty()) {
                failedWith(NoSpaceLeftInInventoryException())
                return@forEach
            }

            ejectableSlots.firstOrNull()?.let {
                val firstHotbarSlot = player.hotbarSlots.first()

                addSubActivities(
                    SwapWithSlot(it, firstHotbarSlot.hotbarSlot),
                    SwapWithSlot(fromSlot, firstHotbarSlot.hotbarSlot)
                )
            }
        }
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
    class NoItemFoundException : Exception("No item found")
    class NotEnoughSlotsException : Exception("Not enough slots")
    class ContainerNotKnownException(val container: Container) : Exception("Container ${container::class.simpleName} not known")
}