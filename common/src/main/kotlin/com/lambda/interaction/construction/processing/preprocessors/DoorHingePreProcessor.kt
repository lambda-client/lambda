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
import com.lambda.threading.runSafe
import net.minecraft.block.BlockState
import net.minecraft.block.enums.DoorHinge
import net.minecraft.state.property.Properties
import net.minecraft.util.math.Direction

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object DoorHingePreProcessor : PlacementProcessor() {
    override fun acceptsState(state: BlockState) =
        state.properties.contains(Properties.DOOR_HINGE)

    override fun preProcess(state: BlockState, accumulator: PreProcessingInfoAccumulator) =
        runSafe {
            val side = state.get(Properties.DOOR_HINGE) ?: return@runSafe
            val scanner = when (state.get(Properties.HORIZONTAL_FACING) ?: return@runSafe) {
                Direction.NORTH ->
                    if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.LESSER_BLOCK_HALF, Direction.Axis.X)
                    else SurfaceScan(ScanMode.GREATER_BLOCK_HALF, Direction.Axis.X)
                Direction.EAST ->
                    if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.LESSER_BLOCK_HALF, Direction.Axis.Z)
                    else SurfaceScan(ScanMode.GREATER_BLOCK_HALF, Direction.Axis.Z)
                Direction.SOUTH ->
                    if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.GREATER_BLOCK_HALF, Direction.Axis.X)
                    else SurfaceScan(ScanMode.LESSER_BLOCK_HALF, Direction.Axis.X)
                Direction.DOWN,
                Direction.UP,
                Direction.WEST ->
                    if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.GREATER_BLOCK_HALF, Direction.Axis.Z)
                    else SurfaceScan(ScanMode.LESSER_BLOCK_HALF, Direction.Axis.Z)
            }
            accumulator.offerSurfaceScan(scanner)
        } ?: Unit
}