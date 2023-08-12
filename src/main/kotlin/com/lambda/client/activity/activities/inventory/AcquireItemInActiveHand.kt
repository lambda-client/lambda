package com.lambda.client.activity.activities.inventory

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.core.CreativeInventoryAction
import com.lambda.client.activity.activities.inventory.core.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.core.SwitchToHotbarSlot
import com.lambda.client.activity.activities.storage.BreakDownEnderChests
import com.lambda.client.activity.activities.storage.ShulkerTransaction
import com.lambda.client.activity.activities.storage.types.*
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
 */
class AcquireItemInActiveHand(
    private val order: StackSelection,
    private val searchShulkerBoxes: Boolean = true,
    private val searchEnderChest: Boolean = true,
) : AttemptActivity, Activity() {
    override val maxAttempts = 3
    override var usedAttempts = 0

    override fun SafeClientEvent.onInitialize() {
        // If the item is already in the player's hand, we're done
        if (order.selection(player.heldItemMainhand)) {
            success()
            return
        }

        // If the item is in the hotbar, switch to it
        player.hotbarSlots.firstOrNull(order.filter)?.let {
            addSubActivities(SwitchToHotbarSlot(it))
            return
        }

        // If we are in game mode creative, we can just use the creative inventory (if item not yet in hotbar)
        if (pickBlock && player.capabilities.isCreativeMode) {
            order.optimalStack?.let {
                addSubActivities(CreativeInventoryAction(it))
                return
            }
        }

        // If the item is in the inventory, swap it with the next best slot in hotbar
        player.allSlots.firstOrNull(order.filter)?.let { slotFrom ->
            addSubActivities(SwapOrSwitchToSlot(slotFrom))
            return
        }

        // If the item is in a shulker box, extract it
        if (searchShulkerBoxes && order.item !is ItemShulkerBox) {
            addSubActivities(ShulkerTransaction(ContainerAction.PULL, order))
            return
        }

        // If the item is obsidian, break down ender chests
        if (order.item == Blocks.OBSIDIAN.item) {
            addSubActivities(BreakDownEnderChests(maximumRepeats = BuildTools.breakDownCycles))
            return
        }

//        // If the item is contained in the ender chest, extract it
//        if (searchEnderChest) {
//            // TODO: Check cached ender chest inventory if item is in there directly or in a shulker box
//            player.allSlots.firstOrNull { it.stack.item == Blocks.ENDER_CHEST.item }?.let { slot ->
//                addSubActivities(EnderChestTransaction(ShulkerOrder(ContainerAction.PULL, order.item, order.amount), slot))
//                return
//            }
//
//            addSubActivities(AcquireItemInActiveHand(order, searchEnderChest = false))
//            return
//        }

        failedWith(NoItemFoundException(order))
    }

    override fun SafeClientEvent.onSuccess() {
        val currentItemStack = player.heldItemMainhand

        if (!order.selection(currentItemStack)) {
            failedWith(FailedToMoveItemException(order, currentItemStack))
        }
    }

    override fun SafeClientEvent.onChildSuccess(childActivity: Activity) {
        if (childActivity !is BreakDownEnderChests) return

        status = Status.UNINITIALIZED
    }

    class NoItemFoundException(order: StackSelection) : Exception("No $order found in inventory")
    class FailedToMoveItemException(order: StackSelection, currentStack: ItemStack) : Exception("Failed to move ${order.item?.registryName?.path} to hotbar  (current item: ${currentStack.item.registryName?.path})")
}