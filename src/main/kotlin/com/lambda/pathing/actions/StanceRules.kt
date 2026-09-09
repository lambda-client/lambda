package com.lambda.pathing.actions

object StanceRules {

	fun stanceConditions(dx: Int, dy: Int, dz: Int) = listOf(
		CellCondition(dx, dy - 1, dz, CellPredicate.SUPPORT),
		CellCondition(dx, dy, dz, CellPredicate.CENTER_SLICE),
		CellCondition(dx, dy + 1, dz, CellPredicate.CENTER_HEAD),
	)

	val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

	val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
}
