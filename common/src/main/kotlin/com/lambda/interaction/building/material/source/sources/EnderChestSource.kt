package com.lambda.interaction.building.material.source.sources

import com.lambda.context.SafeContext
import com.lambda.interaction.building.material.source.MaterialSource
import com.lambda.interaction.building.material.source.Source
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import net.minecraft.item.ItemStack

data class EnderChestSource(
    override val stacks: List<ItemStack>,
) : MaterialSource() {
    override var source = Source.ENDER_CHEST

    override fun available(selection: StackSelection): Int = stacks.filter(selection.selector).sumOf { it.count }
    override suspend fun SafeContext.receive(selection: StackSelection, destination: MaterialDestination) {
        TODO("Not yet implemented")
    }
}