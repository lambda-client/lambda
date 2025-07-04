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
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.SlabType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object SlabPreProcessor : PlacementProcessor() {
    override fun acceptsState(state: BlockState) = state.block is SlabBlock

    override fun preProcess(state: BlockState, accumulator: PreProcessingInfoAccumulator) {
        val slab = state.get(Properties.SLAB_TYPE) ?: return

        val surfaceScan = when (slab) {
             SlabType.BOTTOM -> SurfaceScan(ScanMode.LESSER_BLOCK_HALF, Direction.Axis.Y)
             SlabType.TOP -> SurfaceScan(ScanMode.GREATER_BLOCK_HALF, Direction.Axis.Y)
             SlabType.DOUBLE -> {
                 accumulator.addIgnores(Properties.SLAB_TYPE)
                 SurfaceScan(ScanMode.FULL, Direction.Axis.Y)
             }
        }

        accumulator.offerSurfaceScan(surfaceScan)
    }
}