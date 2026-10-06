package com.lambda.pathing.physics

data class UnsupportedPhysics(
	val kind: UnsupportedPhysicsKind,
	val blockId: String? = null,
)

enum class UnsupportedPhysicsKind {
	FLUID,
	COBWEB,
	POWDER_SNOW,
	HONEY_SIDE_EFFECTS,
}
