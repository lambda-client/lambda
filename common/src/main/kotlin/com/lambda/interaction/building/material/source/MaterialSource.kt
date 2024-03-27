package com.lambda.interaction.building.material.source

import com.lambda.context.SafeContext
import com.lambda.task.flow.building.material.MaterialDestination
import com.lambda.interaction.building.material.StackSelection
import net.minecraft.item.ItemStack

abstract class MaterialSource : Comparable<MaterialSource> {
    abstract var source: Source
    abstract suspend fun SafeContext.receive(
        selection: StackSelection,
        destination: MaterialDestination
    )
    abstract fun available(selection: StackSelection): Int
    open val stacks get() = emptyList<ItemStack>()

    override fun compareTo(other: MaterialSource): Int {
        return compareValuesBy(this, other) { it.source }
    }
}