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
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.item
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object BambooStateProcessor : StateProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		(state.isReplaceable || state.block == Blocks.BAMBOO_SAPLING) && targetState.block == Blocks.BAMBOO

	context(safeContext: SafeContext)
	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		if (state.block == Blocks.BAMBOO_SAPLING) {
			omitInteraction()
			return
		}
		noCaching()
		if (safeContext.blockState(pos.down()).block.item != Items.BAMBOO) {
			setExpectedState(Blocks.BAMBOO_SAPLING.defaultState)
		}
	}
}