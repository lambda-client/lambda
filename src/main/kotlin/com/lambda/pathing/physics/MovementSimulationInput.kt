package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation

data class MovementSimulationInput(
	val forward: Double = 0.0,
	val strafe: Double = 0.0,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false,
	val useItemSlowdown: Boolean = false,
	val rotation: Rotation? = null,
)
