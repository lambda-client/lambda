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

package com.lambda.interaction.construction.processing.preprocessors.property.placement.pre

import com.lambda.interaction.construction.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.processing.PropertyPreProcessor
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.enums.BlockHalf
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object BlockHalfPreProcessor : PropertyPreProcessor {
    override fun acceptsState(targetState: BlockState) =
        Properties.BLOCK_HALF in targetState

    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState) {
        val slab = targetState.get(Properties.BLOCK_HALF) ?: return

        val surfaceScan = when (slab) {
            BlockHalf.BOTTOM -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
            BlockHalf.TOP -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
        }

        offerSurfaceScan(surfaceScan)
    }
}
