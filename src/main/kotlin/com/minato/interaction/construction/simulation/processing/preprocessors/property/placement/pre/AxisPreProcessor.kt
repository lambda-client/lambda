
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object AxisPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.AXIS in targetState

    context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val axis = targetState.get(Properties.AXIS)
        retainSides { side -> side.axis == axis }
    }
}
