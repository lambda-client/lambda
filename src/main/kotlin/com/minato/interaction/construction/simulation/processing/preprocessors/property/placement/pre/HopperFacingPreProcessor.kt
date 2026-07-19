
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object HopperFacingPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.HOPPER_FACING in targetState

    context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val facing = targetState.get(Properties.HOPPER_FACING) ?: return
        when {
            facing.axis == Direction.Axis.Y -> retainSides { it.axis == Direction.Axis.Y }
            else -> retainSides(facing, facing.opposite)
        }
    }
}