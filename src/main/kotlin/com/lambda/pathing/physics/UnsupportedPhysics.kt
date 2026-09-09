package com.lambda.pathing.physics

data class UnsupportedPhysics(
	val kind: UnsupportedPhysicsKind,
	val blockId: String? = null,
)
