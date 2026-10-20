package com.lambda.pathing.world.snapshot

import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

class BlockPhysicsSampler(private val view: BlockView, hazards: HazardRules = HazardRules.AVOID_ALL) {
	private val table = BlockPhysicsTable.of(hazards)
	private val mutablePos = BlockPos.Mutable()

	fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
		val pos = mutablePos.set(x, y, z)
		return table.physics(view.getBlockState(pos), view, pos)
	}
}
