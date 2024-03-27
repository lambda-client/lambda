package com.lambda.interaction.building.material.source.sources

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack

data object CreativeSource : MaterialSource() {
    override var source = Source.CREATIVE

    override fun available(selection: StackSelection): Int =
        if (selection.optimalStack != null) Int.MAX_VALUE else 0

    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        when (destination) {
            is MaterialDestination.MainHand -> {
                if (ItemStack.areEqual(player.mainHandStack, selection.optimalStack)) {
                    return
                }

                if (!player.isCreative) {
                    throw Exception("Cannot move from creative to main hand: not in creative mode")
                }

                selection.optimalStack?.let {
                    createStack(it)
                    return
                }

                throw Exception("Cannot move from creative to main hand: no optimal stack")
            }

            else -> {
                throw Exception("Cannot move from creative to $destination")
            }
        }
    }

    private suspend fun SafeContext.createStack(stack: ItemStack) {
        mc.executeSync {
            interaction.clickCreativeStack(stack, 36 + player.inventory.selectedSlot)
        }
        delay(TaskFlow.itemMoveDelay)
    }
}