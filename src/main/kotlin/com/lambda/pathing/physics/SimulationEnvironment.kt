package com.lambda.pathing.physics

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
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

	fun isSpaceEmpty(box: Box): Boolean

	fun bounceFactor(pos: BlockPos): Double = 0.0

	fun dampensSteppingSpeed(pos: BlockPos): Boolean = false

	fun hotFloor(pos: BlockPos): Boolean = false

	fun fallRule(pos: BlockPos): FallRule = FallRule.NORMAL

	fun mayContainHotFloor(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	fun water(pos: BlockPos): WaterPhysics? = null

	fun mayContainWater(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	fun containsFluid(box: Box): Boolean = false
}

internal inline fun <T> collisionCandidateRange(box: Box, within: (minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) -> T): T = within(
	MathHelper.floor(box.minX - Vanilla.COLLISION_EPSILON) - 1,
	MathHelper.floor(box.minY - Vanilla.COLLISION_EPSILON) - 1,
	MathHelper.floor(box.minZ - Vanilla.COLLISION_EPSILON) - 1,
	MathHelper.floor(box.maxX + Vanilla.COLLISION_EPSILON) + 1,
	MathHelper.floor(box.maxY + Vanilla.COLLISION_EPSILON) + 1,
	MathHelper.floor(box.maxZ + Vanilla.COLLISION_EPSILON) + 1,
)

internal inline fun forEachCollisionCandidate(box: Box, action: (BlockPos) -> Unit) = collisionCandidateRange(box) { minX, minY, minZ, maxX, maxY, maxZ ->
	val mutable = BlockPos.Mutable()
	for (y in minY..maxY) {
		for (z in minZ..maxZ) {
			for (x in minX..maxX) action(mutable.set(x, y, z))
		}
	}
}

internal inline fun closestSupportingBlock(box: Box, entityPos: Vec3d, supports: (BlockPos) -> Boolean): BlockPos? {
	var best: BlockPos? = null
	var bestDistance = Double.MAX_VALUE
	forEachCollisionCandidate(box) { pos ->
		if (!supports(pos)) return@forEachCollisionCandidate
		val candidate = pos.toImmutable()
		val distance = candidate.getSquaredDistance(entityPos)
		if (distance < bestDistance || (distance == bestDistance && (best == null || best < candidate))) {
			best = candidate
			bestDistance = distance
		}
	}
	return best
}
