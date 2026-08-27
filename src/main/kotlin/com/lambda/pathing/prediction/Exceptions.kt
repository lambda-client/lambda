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

package com.lambda.pathing.prediction

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