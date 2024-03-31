package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.TaskChain
import com.lambda.task.buildTask
import com.lambda.task.emptyChain
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack

object HotbarContainer : MaterialContainer(Rank.HOTBAR) {
    override var stacks: List<ItemStack>
        get() = mc.player?.hotbar ?: emptyList()
        set(_) {}

    override fun prepare() = emptyChain()

    override fun withdraw(selection: StackSelection) = buildTask {
//        gameThreadBlocking {
//            stacks.filter(selection.selector).forEach { stack ->
//                player.inventory.selectedSlot = player.inventory.main.indexOf(stack)
//                delay(TaskFlow.itemMoveDelay)
//            }
//        }
    }

    override fun deposit(selection: StackSelection): TaskChain {
        TODO("Not yet implemented")
    }
}