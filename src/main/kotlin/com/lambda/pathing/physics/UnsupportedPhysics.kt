package com.lambda.pathing.physics

data class UnsupportedPhysics(
	val kind: UnsupportedPhysicsKind,
	val blockId: String? = null,
)

enum class UnsupportedPhysicsKind {
	FLUID,
	COBWEB,
	POWDER_SNOW,
	SWEET_BERRY_BUSH,
	HONEY_SIDE_EFFECTS,
	FIRE,
	CACTUS,
	ENTITY,
}
