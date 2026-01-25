/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.PropertyPreProcessor
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

// Collected using reflections and then accessed from a collection in ProcessorRegistry
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