package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.SimulationEnvironment

interface EditableEnvironment : SimulationEnvironment {
	fun readBlock(x: Int, y: Int, z: Int): SnapshotBlockPhysics?

	fun edited(edits: Collection<CellEdit>): EditableEnvironment
}
