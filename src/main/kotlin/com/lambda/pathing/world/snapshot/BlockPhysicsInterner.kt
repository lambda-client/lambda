package com.lambda.pathing.world.snapshot

import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*

internal class BlockPhysicsInterner(private val shapeContext: ShapeContext) {
	private val byState = IdentityHashMap<BlockState, SnapshotBlockPhysics>()

	fun capture(world: World, pos: BlockPos, state: BlockState): SnapshotBlockPhysics {
		if (state.block.hasDynamicBounds()) {
			return BlockPhysicsCapture.capture(state, world, pos, shapeContext)
		}
		return byState.getOrPut(state) {
			BlockPhysicsCapture.capture(state, world, pos, shapeContext)
		}
	}
}
