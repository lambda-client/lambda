package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.interaction.OpenContainer
import com.lambda.client.activity.activities.interaction.PlaceBlock
import com.lambda.client.activity.activities.inventory.AcquireItemInActiveHand
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.utils.getContainerPos
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.items.block
import net.minecraft.block.state.IBlockState
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
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
            AcquireItemInActiveHand(targetStack.item, { onlyItem || ItemStack.areItemStacksEqual(it, targetStack) }),
            PlaceBlock(containerPos, targetStack.item.block.defaultState, ignoreProperties = true)
        )

        if (open) addSubActivities(OpenContainer(containerPos))
    }

    class NoContainerPlacePositionFoundException : Exception("No position to place a container was found")
}