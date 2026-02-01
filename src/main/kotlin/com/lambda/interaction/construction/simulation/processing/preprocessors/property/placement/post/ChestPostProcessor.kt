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

package com.lambda.interaction.construction.simulation.processing.preprocessors.property.placement.post

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.PropertyPostProcessor
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState
import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object ChestPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.block is ChestBlock && state.block === targetState.block &&
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
		val otherChestDirection = when (targetType) {
			ChestType.LEFT -> currentFacing.rotateYClockwise()
			else -> currentFacing.rotateYCounterclockwise()
		}
		val otherChest = safeContext.blockState(pos.offset(otherChestDirection))
		if (otherChest.block !is ChestBlock || otherChest.get(Properties.CHEST_TYPE) != ChestType.SINGLE)
			addIgnores(Properties.CHEST_TYPE)
	}
}