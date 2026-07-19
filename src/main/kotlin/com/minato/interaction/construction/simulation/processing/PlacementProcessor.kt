
package com.minato.interaction.construction.simulation.processing

import com.minato.context.SafeContext
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

/**
 * The class all pre-processors must extend to provide the structure. Preprocessors are used to
 * optimize how blocks are simulated. Some blocks might only be placeable on certain sides, so it is
 * unnecessary to scan all of them, for example.
 */
interface StateProcessor {
	fun acceptsState(state: BlockState, targetState: BlockState): Boolean

	context(safeContext: SafeContext)
	fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos)
}

interface PropertyPreProcessor {
	fun acceptsState(state: BlockState, targetState: BlockState): Boolean

	context(safeContext: SafeContext)
	fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos)
}

interface PropertyPostProcessor {
	fun acceptsState(state: BlockState, targetState: BlockState): Boolean

	context(safeContext: SafeContext)
	fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos)
}