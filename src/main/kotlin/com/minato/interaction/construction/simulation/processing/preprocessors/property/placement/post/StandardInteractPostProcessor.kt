
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.ProcessorRegistry.standardInteractProperties
import com.minato.interaction.construction.simulation.processing.PropertyPostProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.DoorBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object StandardInteractPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.canBeModifiedByHand() && standardInteractProperties.any {
			it in targetState && state.get(it) != targetState.get(it)
		}

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		setItem(null)
		setPlacing(false)
		setSneak(false)
	}

	fun BlockState.canBeModifiedByHand() =
		when (val block = block) {
			is DoorBlock -> block.blockSetType.canOpenByHand
			is TrapdoorBlock -> block.blockSetType.canOpenByHand
			else -> true
		}
}