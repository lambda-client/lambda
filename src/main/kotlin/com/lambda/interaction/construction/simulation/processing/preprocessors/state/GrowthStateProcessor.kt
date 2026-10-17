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

package com.lambda.interaction.construction.simulation.processing.preprocessors.state

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.StateProcessor
import net.minecraft.block.BlockState
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object GrowthStateProcessor : StateProcessor {
	private val growthProperties = setOf(
		Properties.AGE_1,
		Properties.AGE_2,
		Properties.AGE_3,
		Properties.AGE_4,
		Properties.AGE_5,
		Properties.AGE_7,
		Properties.AGE_15,
		Properties.AGE_25,
		Properties.STAGE
	)

	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.block == targetState.block && state != targetState &&
				state.entries.all { (property, value) -> property in growthProperties || targetState.entries[property] == value }

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		omitInteraction()
	}
}
