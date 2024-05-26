package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object MainHandContainer : MaterialContainer(Rank.MAIN_HAND) {
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override fun withdraw(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }
//        InventoryContainer.stacks.filter(selection.selector).take(selection.count).forEach { stack ->
//            if (ItemStack.areEqual(stack, player.mainHandStack)) {
//                return@forEach
//            }
//
//            if (ItemStack.areEqual(stack, player.offHandStack)) {
//                connection.sendPacket(
//                    PlayerActionC2SPacket(
//                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
//                        BlockPos.ORIGIN,
//                        Direction.DOWN,
//                    ),
//                )
//                delay(TaskFlow.itemMoveDelay)
//                return@forEach
//            }
//
//            if (stack in player.hotbar) {
//                player.inventory.selectedSlot = player.hotbar.indexOf(stack)
//                delay(TaskFlow.itemMoveDelay)
//                return@forEach
//            }
//
//            interaction.pickFromInventory(player.combined.indexOf(stack))
//            delay(TaskFlow.itemMoveDelay)
//        }
//    }

    override fun deposit(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }
}