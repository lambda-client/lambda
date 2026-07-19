
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object ChestPreProcessor : PropertyPreProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		targetState.block is ChestBlock && Properties.CHEST_TYPE in targetState && Properties.HORIZONTAL_FACING in targetState

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		noCaching()
		val chestBlock = targetState.block as? ChestBlock ?: return
		val targetType = targetState.get(Properties.CHEST_TYPE)
		val targetFacing = targetState.get(Properties.HORIZONTAL_FACING)
		val placeType = chestBlock.getChestType(safeContext.world, pos, targetFacing)
		if (placeType == targetType) return
		if (targetType != ChestType.SINGLE) {
			if (placeType == ChestType.SINGLE) addIgnores(Properties.CHEST_TYPE)
			else {
				val canPlaceWithTypeRight = targetFacing == chestBlock.getNeighborChestDirection(safeContext.world, pos, targetFacing.rotateYCounterclockwise())
				if (targetType != ChestType.RIGHT || !canPlaceWithTypeRight) {
					setExpectedState(targetState.with(Properties.CHEST_TYPE, ChestType.SINGLE))
					setSneak(true)
				}
			}
		} else setSneak(true)
	}
}