
package com.minato.interaction.construction.simulation.processing.preprocessors.property.placement.pre

import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.processing.PreProcessingInfoAccumulator
import com.minato.interaction.construction.simulation.processing.PropertyPreProcessor
import com.minato.interaction.construction.verify.ScanMode
import com.minato.interaction.construction.verify.SurfaceScan
import net.minecraft.block.BlockState
import net.minecraft.block.enums.DoorHinge
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

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