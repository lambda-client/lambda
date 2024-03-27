package com.lambda.interaction.building.material.source.sources

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack

data class HotbarSource(
    override val stacks: List<ItemStack>,
) : MaterialSource() {
    override var source = Source.HOTBAR

    override fun available(selection: StackSelection): Int =
        stacks.filter(selection.selector).sumOf { it.count }

    override suspend fun SafeContext.receive(
        selection: StackSelection,
        destination: MaterialDestination
    ) {
        when (destination) {
            is MaterialDestination.MainHand -> {
                stacks.filter(selection.selector).forEach { stack ->
                    player.inventory.selectedSlot = player.inventory.main.indexOf(stack)
                    delay(TaskFlow.itemMoveDelay)
                }
            }

            else -> {
                throw IllegalStateException("Cannot move from hotbar to $destination")
            }
        }
    }
}