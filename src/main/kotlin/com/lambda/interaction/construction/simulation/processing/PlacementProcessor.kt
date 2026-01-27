/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.construction.simulation.processing

import com.lambda.context.SafeContext
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