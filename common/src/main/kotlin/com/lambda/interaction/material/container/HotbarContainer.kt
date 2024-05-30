package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider
import net.minecraft.item.ItemStack

object HotbarContainer : MaterialContainer(Rank.HOTBAR) {
    override var stacks: List<ItemStack>
        get() = mc.player?.hotbar ?: emptyList()
        set(_) {}

    override fun withdraw(selection: StackSelection) = emptyTask("HotbarWithdraw")

    override fun deposit(selection: StackSelection): Task<*> {
        val handledScreen = mc.currentScreen as? ScreenHandlerProvider<*> ?: return emptyTask()
        return deposit(handledScreen.screenHandler, selection)
    }
}