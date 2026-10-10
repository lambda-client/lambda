package com.lambda.pathing.actions

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import net.minecraft.util.shape.VoxelShape

internal class StampProgram(
	val clauses: List<Clause>,
	val after: Set<VoxelPos>,
	val candidates: List<Candidate>?,
	val behind: Clause?,
	val bendColumns: IntArray,
	val bendGroups: List<List<Candidate>>,
) {
	class Candidate(
		val solution: LaunchSolution,
		val cells: List<VoxelPos>,
		val clipCells: List<VoxelPos>,
		val ticks: Double,
		val standingStart: Boolean,
		val standingStartBehind: Boolean,
		val bonkCells: List<VoxelPos>,
	) {
		fun standingStartOn(behindStands: Boolean): Boolean = if (behindStands) standingStartBehind else standingStart
	}

	class Clause(val cells: List<VoxelPos>, val truth: BooleanArray) {
		val classSets: IntArray? = run {
			val sets = IntArray(cells.size)
			var holds = 0
			for (assignment in truth.indices) {
				if (!truth[assignment]) continue
				holds++
				for (index in cells.indices) sets[index] = sets[index] or (1 shl classOf(assignment, index))
			}
			sets.takeIf { holds == 0 || it.fold(1) { product, set -> product * Integer.bitCount(set) } == holds }
		}

		companion object {
			fun of(cells: List<VoxelPos>, test: (CoarseVoxelView) -> Boolean): Clause? {
				val truth = BooleanArray(1 shl (CLASS_BITS * cells.size))
				for (assignment in truth.indices) {
					if (cells.indices.any { classOf(assignment, it) >= VOXELS.size }) continue
					val view = AssignedCells(cells, assignment)
					truth[assignment] = test(view)
					if (view.strayRead) return null
				}
				return Clause(cells, truth)
			}

			fun classOf(assignment: Int, cell: Int): Int = (assignment ushr (CLASS_BITS * cell)) and CLASS_MASK
		}
	}

	private class AssignedCells(private val cells: List<VoxelPos>, private val assignment: Int) : CoarseVoxelView {
		var strayRead = false

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
			val index = cells.indexOf(VoxelPos(x, y, z))
			if (index < 0) {
				strayRead = true
				return CoarseVoxel.AIR
			}
			return VOXELS[Clause.classOf(assignment, index)]
		}

		override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? {
			strayRead = true
			return null
		}
	}

	companion object {
		const val CLASS_BITS = 3
		const val CLASS_MASK = 7

		private val VOXELS = listOf(CoarseVoxel.AIR, CoarseVoxel.FULL_BLOCK, CoarseVoxel.HAZARD, CoarseVoxel.CLIMBABLE, CoarseVoxel.WATER)
	}
}
