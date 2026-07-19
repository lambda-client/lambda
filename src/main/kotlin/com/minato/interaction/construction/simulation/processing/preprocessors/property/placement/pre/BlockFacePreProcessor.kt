
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.enums.BlockFace
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object BlockFacePreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.BLOCK_FACE in targetState

	context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val property = targetState.get(Properties.BLOCK_FACE) ?: return
	    when (property) {
			BlockFace.FLOOR -> retainSides(Direction.DOWN)
		    BlockFace.CEILING -> retainSides(Direction.UP)
		    BlockFace.WALL -> retainSides { it in Direction.Type.HORIZONTAL }
		}
    }
}
