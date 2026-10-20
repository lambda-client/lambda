package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.edit.FlippedView
import com.lambda.pathing.world.edit.ToggleTarget
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.BlockPos

internal fun ValueField.toggleTargets(chain: List<Stance>, world: CoarseVoxelView, changed: Set<VoxelPos>, holds: Boolean): List<ToggleTarget> {
	var targets: ArrayList<ToggleTarget>? = null
	val touched = HashSet(changed)
	val flipped = Long2ObjectOpenHashMap<SnapshotBlockPhysics>()
	val edges = arrayOfNulls<CoarseEdge>(chain.size)
	val sweeps = arrayOfNulls<List<VoxelPos>>(chain.size)
	fun view(): CoarseVoxelView = if (flipped.isEmpty()) world else FlippedView(world, flipped)
	fun add(cell: VoxelPos, open: Boolean, segment: Int, after: Int = 0) {
		(targets ?: ArrayList<ToggleTarget>().also { targets = it }) += ToggleTarget(BlockPos(cell.x, cell.y, cell.z), open, segment, after)
	}
	fun after(cell: VoxelPos, segment: Int): Int {
		val view = view()
		for (index in segment - 1 downTo 0) {
			val earlier = edges[index] ?: continue
			if (sweeps[index]?.contains(cell) != true) continue
			val checked = replanned(earlier, view, touched)
			if (checked == null || checked.flips.any { (flip, open) -> view.toggleAt(flip.x, flip.y, flip.z)?.open != open }) return index + 1
		}
		return 0
	}
	for (index in 0 until chain.lastIndex) {
		val coarse = edge(chain[index], chain[index + 1]) ?: continue
		val edge = if (touched.isEmpty()) coarse else replanned(coarse, view(), touched) ?: break
		val sweep = sweep(edge)
		edges[index] = edge
		sweeps[index] = sweep
		if (holds) for (cell in sweep) {
			if (cell !in changed || cell in edge.flips) continue
			val toggle = view().toggleAt(cell.x, cell.y, cell.z) ?: continue
			add(cell, toggle.open, index)
		}
		for ((cell, open) in edge.flips) {
			val toggle = view().toggleAt(cell.x, cell.y, cell.z) ?: continue
			if (toggle.open == open) {
				if (holds) add(cell, open, index)
				continue
			}
			flipped.put(BlockPos.asLong(cell.x, cell.y, cell.z), toggle.physics(open))
			touched += cell
			add(cell, open, index, after(cell, index))
		}
	}
	return targets ?: emptyList()
}
