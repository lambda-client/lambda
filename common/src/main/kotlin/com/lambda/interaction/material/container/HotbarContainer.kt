package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack

object HotbarContainer : MaterialContainer(Rank.HOTBAR) {
    override var stacks: List<ItemStack>
        get() = mc.player?.hotbar ?: emptyList()
        set(_) {}
    override val name = "Hotbar"

    override fun withdraw(selection: StackSelection) = emptyTask("WithdrawFromHotbar")

    override fun deposit(selection: StackSelection): Task<*> {
        val handler = mc.player?.currentScreenHandler ?: return emptyTask("NoScreenHandler")
        return deposit(handler, selection)
    }
}