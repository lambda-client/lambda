package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.interaction.BreakBlockActivity
import com.lambda.client.manager.managers.activity.activities.interaction.CloseContainerActivity
import com.lambda.client.manager.managers.activity.activities.interaction.OpenContainerActivity
import com.lambda.client.manager.managers.activity.activities.interaction.PlaceBlockActivity
import com.lambda.client.manager.managers.activity.activities.storage.ExtractItemFromShulkerBoxActivity
import com.lambda.client.manager.managers.activity.activities.storage.PullItemFromContainerActivity
import com.lambda.client.util.items.*
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.world.getVisibleSides
import com.lambda.client.util.world.isPlaceable
import com.lambda.client.util.world.isReplaceable
import net.minecraft.inventory.ItemStackHelper
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.NonNullList
import net.minecraft.util.math.BlockPos

class SwapOrMoveToItemActivity(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true },
    private val useShulkerBoxes: Boolean = false,
    private val useEnderChest: Boolean = false
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots.firstOrNull { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }?.let { slotFrom ->
            subActivities.add(SwapOrSwitchToSlotActivity(slotFrom, predicateSlot))
        } ?: run {
            subActivities.add(ExtractItemFromShulkerBoxActivity(item, predicateItem, predicateSlot))
        }
    }
}