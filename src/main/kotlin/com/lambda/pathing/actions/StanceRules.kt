package com.lambda.pathing.actions

import com.lambda.pathing.world.CoarseVoxel
import kotlin.math.abs

object StanceRules {

	fun stanceConditions(dx: Int, dy: Int, dz: Int, entering: Int = 0) = listOf(
		CellCondition(dx, dy - 1, dz, CellPredicate.SUPPORT),
		CellCondition(dx, dy, dz, CellPredicate.entering(entering)),
		CellCondition(dx, dy + 1, dz, CellPredicate.enteringHead(entering)),
	)

	fun leaving(sides: Int, dy: Int) = CellCondition(0, dy, 0, CellPredicate.leaving(sides))

	fun walkConditions(dx: Int, dy: Int, dz: Int) = listOf(
		CellCondition(dx, dy - 1, dz, CellPredicate.WALK_SUPPORT),
		CellCondition(dx, dy, dz, CellPredicate.walkingIn(CoarseVoxel.sides(-dx, -dz), dy)),
		CellCondition(0, 0, 0, CellPredicate.walkingOut(CoarseVoxel.sides(dx, dz), dy)),
	)

	fun targetCorner(dx: Int, level: Int, dz: Int, hop: Boolean): List<CellCondition> {
		if (abs(dx) != abs(dz)) return emptyList()
		val stepX = Integer.signum(dx)
		val stepZ = Integer.signum(dz)
		return listOf(CellCondition(dx - stepX, level, dz, CellPredicate.openCorner(stepX, -stepZ, hop)))
	}

	val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

	val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
}
