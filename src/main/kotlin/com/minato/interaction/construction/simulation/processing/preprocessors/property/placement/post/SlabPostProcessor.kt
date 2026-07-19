
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPostProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.SlabType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object SlabPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		targetState.block is SlabBlock && targetState.get(Properties.SLAB_TYPE) == SlabType.DOUBLE &&
				state.block == targetState.block &&
				state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		addIgnores(Properties.SLAB_TYPE)
	}
}