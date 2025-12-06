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

package com.lambda.interaction.construction.processing.preprocessors.property.placement.post

import com.lambda.interaction.construction.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.processing.ProcessorRegistry.standardInteractProperties
import com.lambda.interaction.construction.processing.PropertyPostProcessor
import net.minecraft.block.BlockState

object StandardInteractPreProcessor : PropertyPostProcessor {
	override fun acceptsState(state: BlockState, targetState: BlockState) =
		standardInteractProperties.any {
			it in targetState && state.get(it) != targetState.get(it)
		}

	override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState) {
		setItem(null)
		setPlacing(false)
	}
}