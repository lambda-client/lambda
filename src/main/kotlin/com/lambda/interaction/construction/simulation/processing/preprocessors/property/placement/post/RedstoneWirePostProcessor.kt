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
import net.minecraft.block.RedstoneWireBlock
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

/**
 * Clicking redstone wire turns a cross into a dot and back, when nothing around it connects.
 */
@Suppress("unused")
object RedstoneWirePostProcessor : PropertyPostProcessor {
	private val connections = listOf(
		Properties.NORTH_WIRE_CONNECTION,
		Properties.EAST_WIRE_CONNECTION,
		Properties.SOUTH_WIRE_CONNECTION,
		Properties.WEST_WIRE_CONNECTION
	)

	override fun acceptsState(state: BlockState, targetState: BlockState) =
		state.block is RedstoneWireBlock && state.block == targetState.block &&
				(state.isCross() && targetState.isDot() || state.isDot() && targetState.isCross())

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		noCaching()
		val clicked = (state.block as RedstoneWireBlock).getPlacementState(safeContext.world, targetState, pos)
		if (connections.any { clicked.get(it) != targetState.get(it) }) return
		with(StandardInteractPostProcessor) { preProcess(state, targetState, pos) }
	}

	private fun BlockState.isCross() = connections.all { get(it).isConnected }

	private fun BlockState.isDot() = connections.none { get(it).isConnected }
}
