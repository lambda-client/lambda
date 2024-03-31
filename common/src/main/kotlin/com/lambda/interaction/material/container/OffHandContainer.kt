package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.TaskChain
import net.minecraft.item.ItemStack

object OffHandContainer : MaterialContainer(Rank.OFF_HAND) {
    override var stacks: List<ItemStack>
        get() = mc.player?.offHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override fun withdraw(selection: StackSelection): TaskChain {
        TODO("Not yet implemented")
    }

    override fun deposit(selection: StackSelection): TaskChain {
        TODO("Not yet implemented")
    }
}