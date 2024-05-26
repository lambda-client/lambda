package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack

object HotbarContainer : MaterialContainer(Rank.HOTBAR) {
    override var stacks: List<ItemStack>
        get() = mc.player?.hotbar ?: emptyList()
        set(_) {}

    override fun withdraw(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }

    override fun deposit(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }
}