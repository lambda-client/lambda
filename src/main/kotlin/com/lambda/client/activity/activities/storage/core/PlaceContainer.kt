package com.lambda.client.activity.activities.storage.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.PlaceBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.storage.ItemInfo
import com.lambda.client.activity.types.AttemptActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.items.block
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.world.getVisibleSides
import com.lambda.client.util.world.isPlaceable
import com.lambda.client.util.world.isReplaceable
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

class PlaceContainer(
    private val targetStack: ItemStack,
    private val onlyItem: Boolean = false,
    private val open: Boolean = false,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0
) : AttemptActivity, Activity() {
    var containerPos: BlockPos = BlockPos.ORIGIN

    override fun SafeClientEvent.onInitialize() {
        containerPos = getContainerPos() ?: run {
            failedWith(NoContainerPlacePositionFoundException())
            return
        }

        addSubActivities(
            AcquireItemInActiveHand(ItemInfo(
                targetStack.item, predicate = { onlyItem || ItemStack.areItemStacksEqual(it, targetStack) }
            )),
            PlaceBlock(containerPos, targetStack.item.block.defaultState, ignoreProperties = true)
        )

        if (open) addSubActivities(OpenContainer(containerPos))
    }

    private fun SafeClientEvent.getContainerPos(): BlockPos? {
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

    private fun SafeClientEvent.secureScore(pos: BlockPos): Int {
        var safe = 0
        if (!world.getBlockState(pos.down().north()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().east()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().south()).isReplaceable) safe++
        if (!world.getBlockState(pos.down().west()).isReplaceable) safe++
        return safe
    }

    class NoContainerPlacePositionFoundException : Exception("No position to place a container was found")
}