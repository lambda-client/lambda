package com.lambda.pathing.launch

object Kinematics {

	const val GRAVITY = 0.08

	const val VERTICAL_DRAG = 0.98

	const val HORIZONTAL_DRAG = 0.91

	const val INPUT_DAMPING = 0.98

	const val DEFAULT_SLIPPERINESS = 0.6

	const val DEFAULT_GROUND_FRICTION = HORIZONTAL_DRAG * DEFAULT_SLIPPERINESS

	const val DEFAULT_MOVEMENT_SPEED = 0.1

	const val JUMP_VELOCITY = 0.42

	const val SPRINT_JUMP_BOOST = 0.2

	const val BODY_HALF_WIDTH = 0.3
	const val BODY_HEIGHT = 1.8

	fun fallStep(verticalVelocity: Double): Double = (verticalVelocity + GRAVITY) * VERTICAL_DRAG
}
