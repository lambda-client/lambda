
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPostProcessor
import net.minecraft.block.BlockState
import net.minecraft.item.Items
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object CandlePostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		Properties.CANDLES in targetState && Properties.LIT in targetState &&
				state.block == targetState.block &&
				state.get(Properties.CANDLES) == targetState.get(Properties.CANDLES) &&
				state.get(Properties.LIT) != targetState.get(Properties.LIT)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		setPlacing(false)
		setSneak(false)
		if (state.get(Properties.LIT)) setItem(Items.AIR)
		else setItem(Items.FLINT_AND_STEEL)
	}
}