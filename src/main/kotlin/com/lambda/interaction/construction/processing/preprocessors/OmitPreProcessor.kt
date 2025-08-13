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

package com.lambda.interaction.construction.processing.preprocessors

import com.lambda.interaction.construction.processing.PlacementProcessor
import com.lambda.interaction.construction.processing.PreProcessingInfoAccumulator
import net.minecraft.block.BlockState
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object OmitPreProcessor : PlacementProcessor() {
    override fun acceptsState(state: BlockState) = true

    override fun preProcess(state: BlockState, pos: BlockPos, accumulator: PreProcessingInfoAccumulator) {
        if (Properties.DOUBLE_BLOCK_HALF in state.properties) {
            if (state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                accumulator.omitPlacement()
            }
        }
    }
}
