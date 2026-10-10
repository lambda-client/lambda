package com.lambda.pathing.actions

import com.lambda.pathing.world.CoarseVoxel

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

	val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

	val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
}
