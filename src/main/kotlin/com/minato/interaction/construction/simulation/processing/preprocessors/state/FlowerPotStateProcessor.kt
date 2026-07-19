
package com.minato.interaction.construction.simulation.processing.preprocessors.state

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.StateProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.FlowerPotBlock
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object FlowerPotStateProcessor : StateProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		(state.isReplaceable || state.block == Blocks.FLOWER_POT) &&
				(targetState.block is FlowerPotBlock && targetState.block != Blocks.FLOWER_POT)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		if (state.block != Blocks.FLOWER_POT) {
			setExpectedState(Blocks.FLOWER_POT.defaultState)
			setItem(Items.FLOWER_POT)
			return
		}
		setPlacing(false)
	}
}