package com.lambda.interaction.building.material.source.sources

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Box

data class StashSource(
    val chests: Set<ChestSource>,
    val pos: Box,
) : MaterialSource() {
    override var source = Source.STASH

    override val stacks: List<ItemStack>
        get() = chests.flatMap { it.stacks }

    override fun available(selection: StackSelection): Int = chests.sumOf {
        with(it) {
            available(selection)
        }
    }

    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        TODO("Not yet implemented")
    }
}