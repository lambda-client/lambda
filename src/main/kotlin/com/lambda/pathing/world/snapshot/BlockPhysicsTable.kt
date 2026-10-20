package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.physics.bubbles
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.EmptyBlockView
import java.util.concurrent.ConcurrentHashMap

class BlockPhysicsTable private constructor(val hazards: HazardRules) {
	class Entry internal constructor(val physics: SnapshotBlockPhysics?, val flags: Int, internal val waterKeyed: Boolean)

	private data class WaterKey(val state: BlockState, val water: WaterPhysics)

	private val entries = ConcurrentHashMap<BlockState, Entry>()
	private val waters = ConcurrentHashMap<WaterKey, SnapshotBlockPhysics>()
	private val interned = ConcurrentHashMap<SnapshotBlockPhysics, SnapshotBlockPhysics>()

	fun entry(state: BlockState): Entry = entries[state] ?: entries.computeIfAbsent(state, ::create)

	fun physics(state: BlockState, view: BlockView, pos: BlockPos): SnapshotBlockPhysics {
		val entry = entry(state)
		entry.physics?.let { return it }
		if (!entry.waterKeyed) return intern(capture(state, view, pos))
		val fluid = state.fluidState
		val key = WaterKey(state, WaterPhysics(fluid.getHeight(view, pos), fluid.getVelocity(view, pos), state.bubbles()))
		return waters[key] ?: intern(capture(state, view, pos)).also { waters.putIfAbsent(key, it) }
	}

	fun intern(physics: SnapshotBlockPhysics): SnapshotBlockPhysics = interned.putIfAbsent(physics, physics) ?: physics

	private fun capture(state: BlockState, view: BlockView, pos: BlockPos): SnapshotBlockPhysics =
		BlockPhysicsCapture.capture(state, view, pos, ShapeContext.absent(), hazards)

	private fun create(state: BlockState): Entry {
		val sample = capture(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
		val shaped = state.block.hasDynamicBounds() || state.hasModelOffset() && sample.collides
		val water = state.fluidState.isIn(FluidTags.WATER)
		return Entry(if (shaped || water) null else intern(sample), flagsOf(sample), water && !shaped)
	}

	companion object {
		const val BOUNCY = 1
		const val WATER = 2
		const val BUBBLES = 4
		const val HOT = 8
		const val HONEY = 16
		const val SOFT = 32
		const val ALL = BOUNCY or WATER or BUBBLES or HOT or HONEY or SOFT

		private val tables = ConcurrentHashMap<HazardRules, BlockPhysicsTable>()

		fun of(hazards: HazardRules): BlockPhysicsTable = tables.computeIfAbsent(hazards, ::BlockPhysicsTable)

		fun flagsOf(physics: SnapshotBlockPhysics): Int {
			var flags = 0
			if (physics.coarseVoxel.bouncy) flags = flags or BOUNCY
			if (physics.water != null) flags = flags or WATER or SOFT
			if (physics.water?.bubbles != null) flags = flags or BUBBLES
			if (physics.hotFloor) flags = flags or HOT
			if (physics.honey) flags = flags or HONEY
			if (physics.fallRule.cushions && !physics.coarseVoxel.bouncy) flags = flags or SOFT
			return flags
		}
	}
}
