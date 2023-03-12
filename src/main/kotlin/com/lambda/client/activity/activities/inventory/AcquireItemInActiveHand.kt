package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.CreativeInventoryAction
import com.lambda.client.activity.activities.inventory.core.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.core.SwitchToHotbarSlot
import com.lambda.client.activity.activities.storage.BreakDownEnderChests
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.activity.types.AttemptActivity
import com.lambda.client.activity.getShulkerInventory
import com.lambda.client.activity.slotFilterFunction
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.pickBlock
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.item
import net.minecraft.init.Blocks
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack

class AcquireItemInActiveHand(
    private val item: Item,
    private val predicateStack: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private var metadata: Int? = null,
    private val amount: Int = 1,
    private val useShulkerBoxes: Boolean = true,
    private val useEnderChest: Boolean = false,
    override val maxAttempts: Int = 3,
    override var usedAttempts: Int = 0
) : AttemptActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.hotbarSlots.firstOrNull(slotFilterFunction(item, metadata, predicateStack))?.let {
            addSubActivities(SwitchToHotbarSlot(it))
            return
        }

        if (pickBlock && player.capabilities.isCreativeMode) {
            addSubActivities(CreativeInventoryAction(
                ItemStack(item, 1, metadata ?: 0)
            ))
            return
        }

        player.allSlots.firstOrNull(slotFilterFunction(item, metadata, predicateStack))?.let { slotFrom ->
            addSubActivities(SwapOrSwitchToSlot(slotFrom, predicateSlot))
            return
        }

        if (useShulkerBoxes && item !is ItemShulkerBox) {
            val candidates = mutableMapOf<Slot, Int>()

            player.allSlots.forEach { slot ->
                getShulkerInventory(slot.stack)?.let { inventory ->
                    val count = inventory.count {
                        item == it.item
                            && predicateStack(it)
                            && (metadata == null || metadata == it.metadata)
                    }

                    if (count > 0) candidates[slot] = count
                }
            }

            candidates.minByOrNull { it.value }?.key?.let { slot ->
                addSubActivities(ExtractItemFromShulkerBox(
                    slot.stack, item, predicateStack, predicateSlot, metadata, 1
                ))
                return
            }
        }

        if (item == Blocks.OBSIDIAN.item) {
            addSubActivities(BreakDownEnderChests(maximumRepeats = BuildTools.breakDownCycles))
            return
        }

//        if (useEnderChest) {
//            addSubActivities(ExtractItemFromEnderChest(item, 1, predicateItem, predicateSlot)) // ToDo: Add this
//            return
//        }

        failedWith(NoItemFoundException(item, metadata))
    }

    override fun SafeClientEvent.onSuccess() {
        val currentItemStack = player.heldItemMainhand

        if (currentItemStack.item != item) {
            failedWith(FailedToMoveItemException(item, metadata, currentItemStack))
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is BreakDownEnderChests) return

        status = Status.UNINITIALIZED
    }

    class NoItemFoundException(item: Item, metadata: Int?) : Exception("No ${item.registryName}${metadata?.let { ":$it" } ?: ""} found in inventory")
    class FailedToMoveItemException(item: Item, metadata: Int?, currentStack: ItemStack) : Exception("Failed to move ${item.registryName}${metadata?.let { ":$it" } ?: ""} to hotbar  (current item: ${currentStack.item.registryName})")
}