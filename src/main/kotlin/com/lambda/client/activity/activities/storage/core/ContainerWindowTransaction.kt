package com.lambda.client.activity.activities.storage.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.QuickMoveSlot
import com.lambda.client.activity.activities.inventory.core.SwapWithSlot
import com.lambda.client.activity.activities.storage.types.*
import com.lambda.client.activity.seperatedSlots
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.inventory.Container

class ContainerWindowTransaction(
    private val action: ContainerAction,
    private val order: StackSelection
) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val seperatedSlots = player.openContainer.seperatedSlots

        val (fromSlots, toSlots) = if (action == ContainerAction.PULL) {
            seperatedSlots.let { (first, second) -> Pair(first, second) }
        } else {
            seperatedSlots.let { (first, second) -> Pair(second, first) }
        }

        val toMoveSlots = fromSlots.filter(order.filter)

        if (toMoveSlots.isEmpty()) {
            if (order.count == 0) {
                success()
                return
            }

            failedWith(NoItemFoundException())
            return
        }

        if (toMoveSlots.size < order.count) {
            failedWith(NotEnoughSlotsException())
            return
        }

        val remainingSlots = if (order.count == 0) {
            toMoveSlots
        } else {
            toMoveSlots.take(order.count)
        }

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

    override fun SafeClientEvent.onChildFailure(childActivities: ArrayDeque<Activity>, childException: Exception): Boolean {
        if (childException !is QuickMoveSlot.ExceptionSlotNotEmpty) return false

        MessageSendHelper.sendWarningMessage("Quick move failed, container full")
        success()
        return true
    }

    class NoSpaceLeftInInventoryException : Exception("No space left in inventory")
    class NoItemFoundException : Exception("No item to move found")
    class NotEnoughSlotsException : Exception("Not enough slots")
    class ContainerNotKnownException(val container: Container) : Exception("Container ${container::class.simpleName} not known")
}