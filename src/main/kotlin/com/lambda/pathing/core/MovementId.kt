package com.lambda.pathing.core

@JvmInline
value class MovementId(val key: String) {
	override fun toString(): String = key

	companion object {
		val WALK = MovementId("walk")
		val STEP_UP = MovementId("step_up")
		val WALK_OFF = MovementId("walk_off")
		val DROP = MovementId("drop")
		val JUMP = MovementId("jump")
		val CLIMB = MovementId("climb")
		val LADDER_CATCH = MovementId("ladder_catch")
		val BOUNCE = MovementId("bounce")
		val SWIM = MovementId("swim")
		val ENTER_WATER = MovementId("enter_water")
		val EXIT_WATER = MovementId("exit_water")

		val AQUATIC = setOf(SWIM, ENTER_WATER, EXIT_WATER)
	}
}
