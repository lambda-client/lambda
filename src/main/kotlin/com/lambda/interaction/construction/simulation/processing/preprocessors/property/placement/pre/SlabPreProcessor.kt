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

package com.lambda.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.PropertyPreProcessor
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.SlabBlock
import net.minecraft.block.enums.SlabType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@Suppress("unused")
object SlabPreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) = targetState.block is SlabBlock

    context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
        val slab = targetState.get(Properties.SLAB_TYPE) ?: return

        val surfaceScan = when (slab) {
            SlabType.BOTTOM -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
            SlabType.TOP -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
            SlabType.DOUBLE -> {
                if (state.block !is SlabBlock) {
	                addIgnores(Properties.SLAB_TYPE)
					SurfaceScan.DEFAULT
                } else when (state.get(Properties.SLAB_TYPE)) {
                    SlabType.BOTTOM -> SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Y)
                    else -> SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Y)
                }
            }
        }

        offerSurfaceScan(surfaceScan)
    }
}