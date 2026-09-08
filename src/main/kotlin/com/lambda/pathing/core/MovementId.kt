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

		/** A jump or fall whose landing is a grab into a climbable cell, not a floor. */
		val LADDER_CATCH = MovementId("ladder_catch")

		val BOUNCE = MovementId("bounce")
	}
}
