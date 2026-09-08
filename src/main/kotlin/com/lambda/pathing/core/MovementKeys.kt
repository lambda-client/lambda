package com.lambda.pathing.core

data class MovementKeys(val forward: Double, val strafe: Double) {
	val sustainsSprint: Boolean get() = forward > 0.0

	companion object {
		val FORWARD = MovementKeys(1.0, 0.0)
		val FORWARD_LEFT = MovementKeys(1.0, -1.0)
		val FORWARD_RIGHT = MovementKeys(1.0, 1.0)
		val LEFT = MovementKeys(0.0, -1.0)
		val RIGHT = MovementKeys(0.0, 1.0)
	}
}
