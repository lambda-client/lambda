package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.Wait
import com.lambda.client.activity.activities.getContainerPos
import com.lambda.client.activity.activities.getShulkerInventory
import com.lambda.client.activity.activities.interaction.BreakBlock
import com.lambda.client.activity.activities.interaction.CloseContainer
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.SwapOrSwitchToSlot
import com.lambda.client.activity.activities.inventory.SwapToBestTool
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.block
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

class StoreItemToShulkerBox(
    private val item: Item,
    private val amount: Int = 0, // 0 = all
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val candidates = mutableMapOf<Slot, Int>()

        player.allSlots.forEach { slot ->
            getShulkerInventory(slot.stack)?.let { inventory ->
                if (inventory.all { (it.item == item && predicateItem(it)) || it.isEmpty }) {
                    val count = inventory.count { it.item == item && predicateItem(it) }

                    if (count < 27) candidates[slot] = count
                }
            }
        }

        if (candidates.isEmpty()) return

        candidates.maxBy { it.value }.key.let { slot ->
            getContainerPos()?.let { remotePos ->
                addSubActivities(
                    SwapOrSwitchToSlot(slot, predicateSlot),
                    PlaceBlock(remotePos, slot.stack.item.block),
                    OpenContainer(remotePos),
                    Wait(50L),
                    PushItemsToContainer(item, amount, predicateItem),
                    CloseContainer(),
                    SwapToBestTool(remotePos),
                    BreakBlock(
                        remotePos,
                        pickUpDrop = true,
                        mode = BreakBlock.Mode.PLAYER_CONTROLLER
                    )
                )
            }
        }
    }
}