package com.lambda.pathing.physics

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

interface SimulationEnvironment {
	fun slipperiness(pos: BlockPos): Double
	fun velocityMultiplier(pos: BlockPos): Double
	fun jumpVelocityMultiplier(pos: BlockPos): Double
	fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
	): Vec3d

	fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos?

	fun isFenceLike(pos: BlockPos): Boolean

	fun isClimbable(pos: BlockPos): Boolean = false

	fun isSpaceEmpty(box: Box): Boolean? = null

	fun bounceFactor(pos: BlockPos): Double = 0.0

	fun dampensSteppingSpeed(pos: BlockPos): Boolean = false
}
