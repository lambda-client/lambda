package com.lambda.client.activity.activities.utils

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.world.getVisibleSides
import com.lambda.client.util.world.isPlaceable
import com.lambda.client.util.world.isReplaceable
import net.minecraft.block.state.IBlockState
import net.minecraft.inventory.ItemStackHelper
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.NonNullList
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

fun getShulkerInventory(stack: ItemStack): NonNullList<ItemStack>? {
    if (stack.item !is ItemShulkerBox) return null

    val shulkerInventory = NonNullList.withSize(27, ItemStack.EMPTY)

    stack.tagCompound?.getCompoundTag("BlockEntityTag")?.let {
        if (it.hasKey("Items", 9)) {
            ItemStackHelper.loadAllItems(it, shulkerInventory)
            return shulkerInventory
        }
    }

    return shulkerInventory
}

fun SafeClientEvent.getContainerPos(targetState: IBlockState): BlockPos? {
    return VectorUtils.getBlockPosInSphere(player.positionVector, 4.25f).asSequence()
        .filter { pos ->
//            world.isPlaceable(pos, targetState.getSelectedBoundingBox(world, pos)) // TODO: Calculate correct resulting state of placed block to enable rotation checks
            world.isPlaceable(pos, AxisAlignedBB(pos))
                && !world.getBlockState(pos.down()).isReplaceable
                && world.isAirBlock(pos.up())
                && getVisibleSides(pos.down()).contains(EnumFacing.UP)
                && pos.y >= player.flooredPosition.y
        }.sortedWith(
            compareByDescending<BlockPos> {
                secureScore(it)
            }.thenBy {
                player.positionVector.distanceTo(it.toVec3dCenter())
            }
        ).firstOrNull()
}

fun SafeClientEvent.secureScore(pos: BlockPos): Int {
    var safe = 0
    if (!world.getBlockState(pos.down().north()).isReplaceable) safe++
    if (!world.getBlockState(pos.down().east()).isReplaceable) safe++
    if (!world.getBlockState(pos.down().south()).isReplaceable) safe++
    if (!world.getBlockState(pos.down().west()).isReplaceable) safe++
    return safe
}