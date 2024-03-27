package com.lambda.interaction.building.material.source.sources

import com.lambda.Lambda
import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

data class InventorySource(
    override val stacks: List<ItemStack>,
) : MaterialSource() {
    override var source = Source.INVENTORY

    override fun available(selection: StackSelection): Int = stacks.filter(selection.selector).sumOf { it.count }
    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        when (destination) {
            is MaterialDestination.MainHand -> {
                stacks.filter(selection.selector).take(selection.count).forEach { stack ->
                    if (ItemStack.areEqual(stack, player.mainHandStack)) {
                        return@forEach
                    }

                    if (ItemStack.areEqual(stack, player.offHandStack)) {
                        connection.sendPacket(
                            PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                BlockPos.ORIGIN,
                                Direction.DOWN,
                            ),
                        )
                        delay(TaskFlow.itemMoveDelay)
                        return@forEach
                    }

                    if (stack in player.hotbar) {
                        player.inventory.selectedSlot = player.hotbar.indexOf(stack)
                        delay(TaskFlow.itemMoveDelay)
                        return@forEach
                    }

                    interaction.pickFromInventory(player.combined.indexOf(stack))
                    delay(TaskFlow.itemMoveDelay)
                }
            }

            is MaterialDestination.InventorySlot -> {
                Lambda.LOG.info("Moving $selection from inventory to slot ${destination.slot}")
                stacks.filter(selection.selector).take(selection.count).forEach { stack ->
                    player.currentScreenHandler?.let { screenHandler ->
                        if (screenHandler.stacks[destination.slot].item == stack.item) {
                            return@forEach
                        }
                        val currentStackSlot = screenHandler.stacks.indexOf(stack)
                        if (currentStackSlot == destination.slot) {
                            return@forEach
                        }
                        interaction.clickSlot(
                            player.currentScreenHandler?.syncId ?: 0,
                            currentStackSlot,
                            0,
                            SlotActionType.PICKUP,
                            player,
                        )
                        delay(TaskFlow.itemMoveDelay)
                        interaction.clickSlot(
                            player.currentScreenHandler?.syncId ?: 0,
                            destination.slot,
                            0,
                            SlotActionType.PICKUP,
                            player,
                        )
                        delay(TaskFlow.itemMoveDelay)
                    }
                }
            }

            is MaterialDestination.ShulkerBox -> {
                Lambda.LOG.info("Pushing $selection from inventory to shulker box ${destination.shulkerStack.name.string}")
                PlaceContainer(destination.shulkerStack).onSuccess { placePos ->
                    OpenContainer(placePos).onSuccess {
                        stacks.firstOrNull(selection.selector)?.let {
//                                interaction.clickSlot()
                            Lambda.LOG.info("Moving $it to shulker box")
                        }
                    }.execute()
                }.execute()
            }

            is MaterialDestination.Inventory -> {}

            else -> {
                Lambda.LOG.warn("Cannot move from inventory to $destination")
            }
        }
    }
}