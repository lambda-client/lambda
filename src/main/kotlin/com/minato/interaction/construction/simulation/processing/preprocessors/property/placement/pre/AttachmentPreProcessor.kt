
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.enums.Attachment
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object AttachmentPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.ATTACHMENT in targetState

	context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val attachment = targetState.get(Properties.ATTACHMENT) ?: return
	    when (attachment) {
			Attachment.FLOOR -> retainSides(Direction.DOWN)
		    Attachment.CEILING -> retainSides(Direction.UP)
		    else -> retainSides { it in Direction.Type.HORIZONTAL }
		}
    }
}