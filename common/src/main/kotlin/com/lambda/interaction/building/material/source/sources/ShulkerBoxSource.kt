package com.lambda.interaction.building.material.source.sources

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.interaction.building.verify.TargetState
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.SlotActionType

data class ShulkerBoxSource(
    override val stacks: List<ItemStack>,
    val containedIn: Source,
    val shulkerStack: ItemStack,
) : MaterialSource() {
    override var source = Source.SHULKER_BOX

    override fun available(selection: StackSelection): Int = stacks.filter(selection.selector).sumOf { it.count }
    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        when (destination) {
            is MaterialDestination.MainHand, MaterialDestination.Inventory -> {
                Lambda.LOG.info("Pulling from ${shulkerStack.name.string} to main hand any stack that matches $selection")
                PlaceContainer(shulkerStack).onSuccess { placePos ->
                    OpenContainer(placePos, waitForSlotLoad = true).onSuccess { screenHandler ->
                        // ToDo: proper stack scope
                        screenHandler.stacks.filter(selection.selector).take(selection.count).forEach {
                            interaction.clickSlot(
                                screenHandler.syncId,
                                screenHandler.stacks.indexOf(it),
                                0,
                                SlotActionType.QUICK_MOVE,
                                player,
                            )
                        }
                    }.execute()
                    delay(TaskFlow.itemMoveDelay)
                    mc.executeSync {
                        player.closeHandledScreen()
                    }
                    BuildStructure(placePos.fromBlockPos(TargetState.Air), collectDrops = true).execute()
                }.execute()
            }

            else -> {
                throw Exception("Cannot move from shulker box to $destination")
            }
        }
    }
}