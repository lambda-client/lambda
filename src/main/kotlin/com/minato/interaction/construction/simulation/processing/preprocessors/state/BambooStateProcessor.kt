
package com.minato.interaction.construction.simulation.processing.preprocessors.state

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.StateProcessor
import com.minato.util.BlockUtils.blockState
import com.minato.util.BlockUtils.item
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object BambooStateProcessor : StateProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		(state.isReplaceable || state.block == Blocks.BAMBOO_SAPLING) && targetState.block == Blocks.BAMBOO

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		if (state.block == Blocks.BAMBOO_SAPLING) {
			omitInteraction()
			return
		}
		noCaching()
		if (safeContext.blockState(pos.down()).block.item != Items.BAMBOO) {
			setExpectedState(Blocks.BAMBOO_SAPLING.defaultState)
		}
	}
}