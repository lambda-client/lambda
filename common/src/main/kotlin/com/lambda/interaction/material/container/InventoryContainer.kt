package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.emptyChain
import com.lambda.util.player.SlotUtils.combined
import net.minecraft.item.ItemStack

object InventoryContainer : MaterialContainer(Rank.INVENTORY) {
    override var stacks: List<ItemStack>
        get() = mc.player?.combined ?: emptyList()
        set(_) {}

    override fun withdraw(selection: StackSelection) = emptyChain()

    override fun deposit(selection: StackSelection) = emptyChain()

//    Lambda.LOG.info("Moving $selection from inventory to slot ${destination.slot}")
//    stacks.filter(selection.selector).take(selection.count).forEach { stack ->
//        player.currentScreenHandler?.let { screenHandler ->
//            if (screenHandler.stacks[destination.slot].item == stack.item) {
//                return@forEach
//            }
//            val currentStackSlot = screenHandler.stacks.indexOf(stack)
//            if (currentStackSlot == destination.slot) {
//                return@forEach
//            }
//            interaction.clickSlot(
//                player.currentScreenHandler?.syncId ?: 0,
//                currentStackSlot,
//                0,
//                SlotActionType.PICKUP,
//                player,
//            )
//            delay(TaskFlow.itemMoveDelay)
//            interaction.clickSlot(
//                player.currentScreenHandler?.syncId ?: 0,
//                destination.slot,
//                0,
//                SlotActionType.PICKUP,
//                player,
//            )
//            delay(TaskFlow.itemMoveDelay)
//        }
//    }
}