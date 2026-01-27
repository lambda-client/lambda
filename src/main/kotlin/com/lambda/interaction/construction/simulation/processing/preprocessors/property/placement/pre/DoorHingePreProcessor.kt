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

package com.lambda.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.lambda.interaction.construction.simulation.processing.PropertyPreProcessor
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.enums.DoorHinge
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

// Collected using reflections and then accessed from a collection in ProcessorRegistry
@Suppress("unused")
object DoorHingePreProcessor : PropertyPreProcessor {
    override fun acceptsState(state: BlockState, targetState: BlockState) =
        Properties.DOOR_HINGE in targetState

	context(safeContext: SafeContext)
    override fun PreProcessingInfoAccumulator.preProcess(state: BlockState, targetState: BlockState, pos: BlockPos) {
		val side = targetState.get(Properties.DOOR_HINGE) ?: return
		val scanner = when (targetState.get(Properties.HORIZONTAL_FACING) ?: return) {
			Direction.NORTH ->
				if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.X)
				else SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.X)
			Direction.EAST ->
				if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Z)
				else SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Z)
			Direction.SOUTH ->
				if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.X)
				else SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.X)
			Direction.DOWN,
			Direction.UP,
			Direction.WEST ->
				if (side == DoorHinge.LEFT) SurfaceScan(ScanMode.GreaterBlockHalf, Direction.Axis.Z)
				else SurfaceScan(ScanMode.LesserBlockHalf, Direction.Axis.Z)
		}
		return offerSurfaceScan(scanner)
    }
}