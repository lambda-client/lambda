package com.lambda.pathing.physics

import net.minecraft.util.math.BlockPos

sealed class SimulationEnvironmentException(message: String) : IllegalStateException(message)

class SimulationSnapshotOutOfBoundsException(val pos: BlockPos) :
	SimulationEnvironmentException("Movement simulation read outside its snapshot at $pos")

class UnsupportedBlockPhysicsException(val pos: BlockPos, val physics: UnsupportedPhysics) :
	SimulationEnvironmentException("Unsupported movement physics at $pos: $physics")

class SnapshotSectionUnavailableException(val sectionX: Int, val sectionY: Int, val sectionZ: Int) :
	SimulationEnvironmentException(
		"Movement simulation read unloaded terrain in section ($sectionX, $sectionY, $sectionZ)"
	)
