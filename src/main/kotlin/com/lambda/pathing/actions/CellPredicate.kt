package com.lambda.pathing.actions

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium

fun interface CellPredicate {
	fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean

	val extraReads: List<VoxelPos> get() = emptyList()

	companion object {

		val SUPPORT = below { view, x, y, z ->
			view.standingSurface(x, y, z) != null && !view.voxel(x, y, z).intrudesAbove
		}

		val CENTER_SLICE = below { view, x, y, z ->
			view.voxel(x, y, z).centerPassable && !view.voxel(x, y - 1, z).intrudesAbove
		}

		val FULL_SLICE = below { view, x, y, z ->
			view.voxel(x, y, z).fullyPassable && !view.voxel(x, y - 1, z).intrudesAbove
		}

		val CENTER_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).centerPassable }

		val FULL_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

		val CLIMBABLE = CellPredicate { view, x, y, z -> view.medium(x, y, z) == Medium.CLIMBABLE }

		val OPEN_COLUMN = column { view, x, y, z -> FULL_SLICE.matches(view, x, y, z) && FULL_HEAD.matches(view, x, y + 1, z) }

		val BLOCKED_COLUMN = column { view, x, y, z -> !OPEN_COLUMN.matches(view, x, y, z) }

		private fun column(test: CellPredicate) = object : CellPredicate {
			override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int) = test.matches(view, x, y, z)
			override val extraReads = listOf(VoxelPos(0, -1, 0), VoxelPos(0, 1, 0))
		}

		private fun below(test: CellPredicate) = object : CellPredicate {
			override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int) = test.matches(view, x, y, z)
			override val extraReads = listOf(VoxelPos(0, -1, 0))
		}
	}
}

data class CellCondition(val dx: Int, val dy: Int, val dz: Int, val predicate: CellPredicate) {
	fun matches(view: CoarseVoxelView, originX: Int, originY: Int, originZ: Int): Boolean =
		predicate.matches(view, originX + dx, originY + dy, originZ + dz)

	fun reads(): List<VoxelPos> = buildList {
		add(VoxelPos(dx, dy, dz))
		predicate.extraReads.forEach {
			add(VoxelPos(dx + it.x, dy + it.y, dz + it.z))
		}
	}
}
