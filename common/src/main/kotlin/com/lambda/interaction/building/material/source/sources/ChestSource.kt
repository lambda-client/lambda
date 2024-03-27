package com.lambda.interaction.building.material.source.sources

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

data class ChestSource(
    override val stacks: List<ItemStack>,
    val blockPos: BlockPos,
) : MaterialSource() {
    override var source = Source.CHEST

    override fun available(selection: StackSelection): Int = stacks.filter(selection.selector).sumOf { it.count }
    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        TODO("Not yet implemented")
    }
}