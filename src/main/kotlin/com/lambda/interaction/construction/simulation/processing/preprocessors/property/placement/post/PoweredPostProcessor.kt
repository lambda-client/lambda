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
import net.minecraft.block.BlockState
import net.minecraft.block.ButtonBlock
import net.minecraft.block.LeverBlock
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object PoweredPostProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.block is ButtonBlock || state.block is LeverBlock && state.get(Properties.POWERED) != targetState.get(Properties.POWERED)

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) =
		with(StandardInteractPostProcessor) { preProcess(state, targetState, pos) }
}