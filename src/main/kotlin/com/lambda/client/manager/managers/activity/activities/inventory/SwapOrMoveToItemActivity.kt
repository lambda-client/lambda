package com.lambda.client.manager.managers.activity.activities.inventory

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.InstantActivity
import com.lambda.client.manager.managers.activity.activities.interaction.OpenContainerActivity
import com.lambda.client.manager.managers.activity.activities.interaction.PlaceBlockActivity
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
    private val predicateSlot: (ItemStack) -> Boolean = { true }
) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.allSlots.firstOrNull { slot ->
            slot.stack.item == item && predicateItem(slot.stack)
        }?.let { slotFrom ->
            slotFrom.toHotbarSlotOrNull()?.let { hotbarSlot ->
                subActivities.add(SwitchToHotbarSlotActivity(hotbarSlot.hotbarSlot))
            } ?: run {
                val hotbarSlots = player.hotbarSlots
                val slotTo = hotbarSlots.firstEmpty()
                    ?: hotbarSlots.firstOrNull { predicateSlot(it.stack) } ?: return

                subActivities.add(SwapWithSlotActivity(slotFrom, slotTo))
                subActivities.add(SwitchToHotbarSlotActivity(slotTo.hotbarSlot))
            }
        } ?: run {
            val candidates = mutableMapOf<Slot, Int>()

            player.allSlots.forEach { slot ->
                getShulkerInventory(slot.stack)?.let { inventory ->
                    val count = inventory.count { it.item == item && predicateItem(it) }

                    if (count > 0) candidates[slot] = count
                }
            }

            if (candidates.isEmpty()) return

            candidates.minBy { it.value }.key.let { slot ->
                slot.toHotbarSlotOrNull()?.let {
                    subActivities.add(SwitchToHotbarSlotActivity(it.hotbarSlot))
                } ?: run {
                    val hotbarSlots = player.hotbarSlots
                    val slotTo = hotbarSlots.firstEmpty()
                        ?: hotbarSlots.firstOrNull { predicateSlot(it.stack) } ?: return

                    subActivities.add(SwapWithSlotActivity(slot, slotTo))
                    subActivities.add(SwitchToHotbarSlotActivity(slotTo.hotbarSlot))
                }
                getRemotePos()?.let { remotePos ->
                    subActivities.add(PlaceBlockActivity(remotePos, slot.stack.item.block))
                    subActivities.add(OpenContainerActivity(remotePos))
//                    subActivities.add(MoveItemActivity(slot, slot.stack.item, predicateItem))
                }
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

    private fun SafeClientEvent.getRemotePos(): BlockPos? {
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