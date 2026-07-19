
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPostProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.ButtonBlock
import net.minecraft.block.LeverBlock
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object PoweredPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.block is ButtonBlock || state.block is LeverBlock && state.get(Properties.POWERED) != targetState.get(Properties.POWERED)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) =
		with(StandardInteractPostProcessor) { preProcess(state, targetState, pos) }
}