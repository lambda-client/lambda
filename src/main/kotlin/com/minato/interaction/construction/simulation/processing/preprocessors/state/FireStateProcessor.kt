
package com.minato.interaction.construction.simulation.processing.preprocessors.state

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.StateProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object FireStateProcessor : StateProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.isReplaceable && targetState.block == Blocks.FIRE

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		setItem(Items.FLINT_AND_STEEL)
		setPlacing(false)
	}
}