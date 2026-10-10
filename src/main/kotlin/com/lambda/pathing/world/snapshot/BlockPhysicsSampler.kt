package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.WaterPhysics
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import java.util.IdentityHashMap

class BlockPhysicsSampler(
	private val view: BlockView,
	private val shapeContext: ShapeContext,
) {
	private val mutablePos = BlockPos.Mutable()
	private val physicsByState = IdentityHashMap<BlockState, SnapshotBlockPhysics>()
	private val physicsByWater = HashMap<Pair<BlockState, WaterPhysics>, SnapshotBlockPhysics>()

	fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
		val pos = mutablePos.set(x, y, z)
		val state = view.getBlockState(pos)
		if (state.block.hasDynamicBounds()) return BlockPhysicsCapture.capture(state, view, pos, shapeContext)
		val fluid = state.fluidState
		if (!fluid.isIn(FluidTags.WATER)) return physicsByState.getOrPut(state) { BlockPhysicsCapture.capture(state, view, pos, shapeContext) }
		val water = WaterPhysics(fluid.getHeight(view, pos), fluid.getVelocity(view, pos))
		return physicsByWater.getOrPut(state to water) { BlockPhysicsCapture.capture(state, view, pos, shapeContext) }
	}
}
