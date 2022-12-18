package com.lambda.client.manager.managers.activity.activities.storage

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.interaction.BreakBlockActivity
import com.lambda.client.manager.managers.activity.activities.interaction.CloseContainerActivity
import com.lambda.client.manager.managers.activity.activities.interaction.OpenContainerActivity
import com.lambda.client.manager.managers.activity.activities.interaction.PlaceBlockActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrMoveToItemActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrSwitchToSlotActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapToBestToolActivity
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.block
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

class ExtractItemFromShulkerBoxActivity(
    private val item: Item,
    private val predicateItem: (ItemStack) -> Boolean = { true },
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
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
                subActivities.addAll(listOf(
                    SwapOrSwitchToSlotActivity(slot, predicateSlot),
                    PlaceBlockActivity(remotePos, slot.stack.item.block),
                    OpenContainerActivity(remotePos),
                    PullItemFromContainerActivity(item, predicateItem),
                    CloseContainerActivity(),
                    SwapToBestToolActivity(remotePos),
                    BreakBlockActivity(
                        remotePos,
                        pickUpDrop = true,
                        mode = BreakBlockActivity.Mode.PLAYER_CONTROLLER
                    ),
                    SwapOrMoveToItemActivity(item, predicateItem, predicateSlot)
                ))
            }
        }
    }

    private fun getShulkerInventory(stack: ItemStack): NonNullList<ItemStack>? {
        if (stack.item !is ItemShulkerBox) return null

        stack.tagCompound?.getCompoundTag("BlockEntityTag")?.let {
            if (it.hasKey("Items", 9)) {
                val shulkerInventory = NonNullList.withSize(27, ItemStack.EMPTY)
                ItemStackHelper.loadAllItems(it, shulkerInventory)
                return shulkerInventory
            }
        }

        return null
    }

    private fun SafeClientEvent.getContainerPos(): BlockPos? {
        return VectorUtils.getBlockPosInSphere(player.positionVector, 4.25f).asSequence()
            .filter { pos ->
                world.isPlaceable(pos)
                    && !world.getBlockState(pos.down()).isReplaceable
                    && world.isAirBlock(pos.up())
                    && getVisibleSides(pos.down()).contains(EnumFacing.UP)
                    && pos.y >= player.positionVector.y
            }.sortedWith(
                compareByDescending<BlockPos> {
                    secureScore(it)
                }.thenBy {
                    player.positionVector.distanceTo(it.toVec3dCenter())
                }
            ).firstOrNull()
    }

    private fun SafeClientEvent.secureScore(pos: BlockPos): Int {
        var safe = 0
        if (!world.getBlockState(pos.down().north()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().east()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().south()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().west()).isReplaceable) safe++
        return safe
    }
}