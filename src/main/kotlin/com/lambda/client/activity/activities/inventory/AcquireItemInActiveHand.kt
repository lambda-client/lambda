package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.CreativeInventoryAction
import com.lambda.client.activity.activities.inventory.core.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.core.SwitchToHotbarSlot
import com.lambda.client.activity.activities.storage.BreakDownEnderChests
import com.lambda.client.activity.activities.storage.ShulkerTransaction
import com.lambda.client.activity.activities.storage.types.ContainerAction
import com.lambda.client.activity.activities.storage.types.ItemInfo
import com.lambda.client.activity.activities.storage.types.ShulkerOrder
import com.lambda.client.activity.getShulkerInventory
import com.lambda.client.activity.types.AttemptActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.pickBlock
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.item
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack

/**
 * [AcquireItemInActiveHand] is an [Activity] that attempts to acquire an [Item] in the player's active hand.
 * It will attempt to acquire the item in the following order:
 * 1. Switch to the hotbar slot that contains the item
 * 2. Swap the item with an item in the hotbar
 */
class AcquireItemInActiveHand(
    private val itemInfo: ItemInfo,
    private val searchShulkerBoxes: Boolean = true,
    private val searchEnderChest: Boolean = true,
) : AttemptActivity, Activity() {
    override val maxAttempts = 3
    override var usedAttempts = 0

    override fun SafeClientEvent.onInitialize() {
        // If the item is already in the player's hand, we're done
        if (itemInfo.stackFilter(player.heldItemMainhand)) {
            success()
            return
        }

        // If the item is in the hotbar, switch to it
        player.hotbarSlots.firstOrNull(itemInfo.slotFilter)?.let {
            addSubActivities(SwitchToHotbarSlot(it))
            return
        }

        // If we are in game mode creative, we can just use the creative inventory (if item not yet in hotbar)
        if (pickBlock && player.capabilities.isCreativeMode) {
            addSubActivities(CreativeInventoryAction(itemInfo.optimalStack))
            return
        }

        // If the item is in the inventory, swap it with next best slot in hotbar
        player.allSlots.firstOrNull(itemInfo.slotFilter)?.let { slotFrom ->
            addSubActivities(SwapOrSwitchToSlot(slotFrom))
            return
        }

        // If the item is in a shulker box, extract it
        if (searchShulkerBoxes && itemInfo.item !is ItemShulkerBox) {
            addSubActivities(ShulkerTransaction(ShulkerOrder(ContainerAction.PULL, itemInfo.item, itemInfo.number)))
            return
        }

        // If the item is obsidian, break down ender chests
        if (BuildTools.breakDownEnderChests && itemInfo.item == Blocks.OBSIDIAN.item) {
            addSubActivities(BreakDownEnderChests(maximumRepeats = BuildTools.breakDownCycles))
            return
        }

//        // If the item is contained in the ender chest, extract it
//        if (searchEnderChest) {
//            // TODO: Check cached ender chest inventory if item is in there directly or in a shulker box
//            player.allSlots.firstOrNull { it.stack.item == Blocks.ENDER_CHEST.item }?.let { slot ->
//                addSubActivities(ExtractItemFromContainerStack(slot.stack, itemInfo))
//                return
//            }
//
//            addSubActivities(AcquireItemInActiveHand(ItemInfo(Blocks.ENDER_CHEST.item), searchEnderChest = false))
//            return
//        }

        failedWith(NoItemFoundException(itemInfo))
    }

    override fun SafeClientEvent.onSuccess() {
        val currentItemStack = player.heldItemMainhand

        if (!itemInfo.stackFilter(currentItemStack)) {
            failedWith(FailedToMoveItemException(itemInfo, currentItemStack))
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is BreakDownEnderChests) return

        status = Status.UNINITIALIZED
    }

    private fun SafeClientEvent.selectOptimalShulker() = player.allSlots.mapNotNull { slot ->
        getShulkerInventory(slot.stack)?.let { inventory ->
            val count = inventory.count(itemInfo.stackFilter)

            if (count > 0) slot to count else null
        }
    }.minByOrNull { it.second }?.first

    class NoItemFoundException(itemInfo: ItemInfo) : Exception("No $itemInfo found in inventory")
    class FailedToMoveItemException(itemInfo: ItemInfo, currentStack: ItemStack) : Exception("Failed to move $itemInfo} to hotbar  (current item: ${currentStack.item.registryName})")
}