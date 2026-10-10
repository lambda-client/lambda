package com.lambda.pathing.actions

import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.Medium

fun interface CellPredicate {
	fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean

	val extraReads: List<VoxelPos> get() = emptyList()

	val neighborReads: List<VoxelPos> get() = emptyList()

	companion object {

		val SUPPORT = below { view, x, y, z ->
			view.standingSurface(x, y, z) != null && !view.voxel(x, y, z).intrudesAbove
		}

		private val ENTERING = Array(CoarseVoxel.ALL_SIDES + 1) { sides ->
			below { view, x, y, z ->
				view.voxel(x, y, z).let { it.centerPassable && it.opensToward(sides) } && !view.voxel(x, y - 1, z).intrudesAbove
			}
		}

		private val ENTERING_HEAD = Array(CoarseVoxel.ALL_SIDES + 1) { sides ->
			CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { it.centerPassable && it.opensToward(sides) } }
		}

		private val LEAVING = Array(CoarseVoxel.ALL_SIDES + 1) { sides ->
			object : CellPredicate {
				override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
					val feet = view.voxel(x, y, z)
					val head = view.voxel(x, y + 1, z)
					return feet.centerPassable && head.centerPassable && feet.opensToward(sides) && head.opensToward(sides)
				}
				override val extraReads = listOf(VoxelPos(0, 1, 0))
			}
		}

		private val WALKING_IN = Array(3) { rise -> Array(CoarseVoxel.ALL_SIDES + 1) { side -> walking(side, rise - 1, inbound = true) } }

		private val WALKING_OUT = Array(3) { rise -> Array(CoarseVoxel.ALL_SIDES + 1) { side -> walking(side, rise - 1, inbound = false) } }

		private fun walking(side: Int, dy: Int, inbound: Boolean) = object : CellPredicate {
			override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
				if (Footing.involvesPartial(view, x, y, z)) {
					val other = Stance(x + CoarseVoxel.dx(side), if (inbound) y - dy else y + dy, z + CoarseVoxel.dz(side))
					val here = Stance(x, y, z)
					val maxRise = if (dy > 0) Footing.JUMP_RISE else Footing.WALK_RISE
					return if (inbound) Footing.walkable(view, other, here, maxRise) else Footing.walkable(view, here, other, maxRise)
				}
				if (inbound) {
					val feet = view.voxel(x, y, z)
					val head = view.voxel(x, y + 1, z)
					return feet.centerPassable && head.centerPassable && feet.opensToward(side) && head.opensToward(side) && !view.voxel(x, y - 1, z).intrudesAbove
				}
				val crossing = if (dy > 0) y + 1 else y
				return view.voxel(x, crossing, z).opensToward(side) && view.voxel(x, crossing + 1, z).opensToward(side)
			}
			override val extraReads = listOf(VoxelPos(0, -2, 0), VoxelPos(0, -1, 0), VoxelPos(0, 1, 0)) + if (!inbound && dy > 0) listOf(VoxelPos(0, 2, 0)) else emptyList()
			override val neighborReads = listOf(0 to 0, CoarseVoxel.dx(side) to CoarseVoxel.dz(side))
				.flatMap { (cx, cz) -> (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (-3..3).map { VoxelPos(cx + dx, it, cz + dz) } } } }.distinct()
		}

		val WALK_SUPPORT = object : CellPredicate {
			override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
				view.voxel(x, y, z).partial || view.voxel(x, y - 1, z).partial || view.standingSurface(x, y, z) != null && !view.voxel(x, y, z).intrudesAbove
			override val extraReads = listOf(VoxelPos(0, -1, 0))
		}

		val CLIMBING = object : CellPredicate {
			override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
				Footing.involvesPartial(view, x, y, z) && !view.voxel(x, y, z).centerPassable && Footing.climbable(view, Stance(x, y, z), Stance(x, y + 1, z))
			override val extraReads = listOf(VoxelPos(0, -2, 0), VoxelPos(0, -1, 0), VoxelPos(0, 1, 0))
			override val neighborReads = (-1..1).flatMap { dx -> (-1..1).flatMap { dz -> (-3..4).map { VoxelPos(dx, it, dz) } } }
		}

		fun entering(sides: Int): CellPredicate = ENTERING[sides]

		fun enteringHead(sides: Int): CellPredicate = ENTERING_HEAD[sides]

		fun leaving(sides: Int): CellPredicate = LEAVING[sides]

		fun walkingIn(side: Int, dy: Int): CellPredicate = WALKING_IN[dy + 1][side]

		fun walkingOut(side: Int, dy: Int): CellPredicate = WALKING_OUT[dy + 1][side]

		val CENTER_SLICE = entering(0)

		val FULL_SLICE = below { view, x, y, z ->
			view.voxel(x, y, z).fullyPassable && !view.voxel(x, y - 1, z).intrudesAbove
		}

		val CENTER_HEAD = enteringHead(0)

		val FULL_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

		val CLIMBABLE = CellPredicate { view, x, y, z -> view.medium(x, y, z) == Medium.CLIMBABLE }

		val WATER = CellPredicate { view, x, y, z -> view.medium(x, y, z) == Medium.WATER }

		val DRY = CellPredicate { view, x, y, z -> view.medium(x, y, z) != Medium.WATER }

		val LAUNCHABLE = CellPredicate { view, x, y, z -> view.medium(x, y, z).let { it != Medium.WATER && it != Medium.CLIMBABLE } }

		val SWIMMABLE = CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { it.fullyPassable || it.medium == Medium.WATER } }

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

	fun neighborReads(): List<VoxelPos> = predicate.neighborReads.map { VoxelPos(dx + it.x, dy + it.y, dz + it.z) }
}
