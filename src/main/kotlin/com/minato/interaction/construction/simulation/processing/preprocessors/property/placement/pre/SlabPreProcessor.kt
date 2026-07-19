
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import com.minato.interaction.construction.verify.ScanMode
import com.minato.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.SlabType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object SlabPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) = targetState.block is SlabBlock

    context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val slab = targetState.get(Properties.SLAB_TYPE) ?: return

        val surfaceScan = when (slab) {
            SlabType.BOTTOM -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
            SlabType.TOP -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
            SlabType.DOUBLE -> {
                if (state.block !is SlabBlock) {
	                addIgnores(Properties.SLAB_TYPE)
					SurfaceScan.DEFAULT
                } else when (state.get(Properties.SLAB_TYPE)) {
                    SlabType.BOTTOM -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
                    else -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
                }
            }
        }

        offerSurfaceScan(surfaceScan)
    }
}