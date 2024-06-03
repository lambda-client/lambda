package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.util.player.SlotUtils.combined
import net.minecraft.item.ItemStack

object InventoryContainer : MaterialContainer(Rank.INVENTORY) {
    override var stacks: List<ItemStack>
        get() = mc.player?.combined ?: emptyList()
        set(_) {}
    override val name = "Inventory"

    override fun withdraw(selection: StackSelection) = Task.emptyTask()

    override fun deposit(selection: StackSelection) = Task.emptyTask()
}