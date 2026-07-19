
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import com.minato.interaction.construction.verify.ScanMode
import com.minato.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.enums.BlockHalf
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object BlockHalfPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.BLOCK_HALF in targetState

    context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val slab = targetState.get(Properties.BLOCK_HALF) ?: return

        val surfaceScan = when (slab) {
            BlockHalf.BOTTOM -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
            BlockHalf.TOP -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
        }

        offerSurfaceScan(surfaceScan)
    }
}
