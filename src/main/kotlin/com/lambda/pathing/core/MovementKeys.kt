package com.lambda.pathing.core

data class MovementKeys(val forward: Double, val strafe: Double) {
	companion object {
		val FORWARD = MovementKeys(1.0, 0.0)
		val FORWARD_LEFT = MovementKeys(1.0, -1.0)
		val FORWARD_RIGHT = MovementKeys(1.0, 1.0)
	}
}
