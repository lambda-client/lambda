
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPostProcessor
import com.minato.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object ChestPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		targetState.block is ChestBlock &&
				Properties.CHEST_TYPE in state && Properties.HORIZONTAL_FACING in state &&
				state.get(Properties.HORIZONTAL_FACING) == targetState.get(Properties.HORIZONTAL_FACING) &&
				state.get(Properties.CHEST_TYPE) != targetState.get(Properties.CHEST_TYPE)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		noCaching()
		val targetType = targetState.get(Properties.CHEST_TYPE)
		val currentType = state.get(Properties.CHEST_TYPE)
		if (currentType != ChestType.SINGLE) return
		val currentFacing = state.get(Properties.HORIZONTAL_FACING)
		val otherChestDirection = when(targetType) {
			ChestType.LEFT -> currentFacing.rotateYClockwise()
			else -> currentFacing.rotateYCounterclockwise()
		}
		val otherChest = safeContext.blockState(pos.offset(otherChestDirection))
		if (otherChest.block !is ChestBlock || otherChest.get(Properties.CHEST_TYPE) != ChestType.SINGLE)
			addIgnores(Properties.CHEST_TYPE)
	}
}