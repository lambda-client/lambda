package com.lambda.client.activity.activities.storage

import baritone.api.pathing.goals.GoalNear
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.utils.Wait
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.activity.activities.utils.getShulkerInventory
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.activity.activities.travel.CustomGoal
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class ExtractItemFromShulkerBox(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
//        if (player.inventorySlots.item)

        val candidates = mutableMapOf<Slot, Int>()

        player.allSlots.forEach { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                val count = inventory.count { it.item == item && predicateItem(it) }

                if (count > 0) candidates[slot] = count
            }
        }

        if (candidates.isEmpty()) return

        candidates.minBy { it.value }.key.let { slot ->
            getContainerPos()?.let { remotePos ->
                addSubActivities(
                    CustomGoal(GoalNear(remotePos, 3)),
                    SwapOrSwitchToSlot(slot, predicateSlot),
                    PlaceBlock(remotePos, slot.stack.item.block),
                    OpenContainer(remotePos),
                    Wait(50L),
                    PullItemsFromContainer(item, amount, predicateItem),
                    CloseContainer(),
                    SwapToBestTool(remotePos),
                    BreakBlock(
                        remotePos,
                        pickUpDrop = true,
                        mode = BreakBlock.Mode.PLAYER_CONTROLLER
                    ),
                    SwapOrMoveToItem(item, predicateItem, predicateSlot)
                )
            }
        }
    }
}