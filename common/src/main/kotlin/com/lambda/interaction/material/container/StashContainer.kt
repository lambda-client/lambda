package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.util.math.VecUtils.blockPos
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Box

data class StashContainer(
    val chests: Set<ChestContainer>,
    val pos: Box,
) : MaterialContainer(Rank.STASH) {
    override var stacks: List<ItemStack>
        get() = chests.flatMap { it.stacks }
        set(_) {}
    override val name = "Stash at ${pos.center.blockPos.toShortString()}"

    override fun withdraw(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }

    override fun deposit(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }

    override fun available(selection: StackSelection): Int =
        chests.sumOf {
            it.available(selection)
        }
}