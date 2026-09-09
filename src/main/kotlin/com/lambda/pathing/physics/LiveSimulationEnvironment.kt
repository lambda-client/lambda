package com.lambda.pathing.physics

import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.util.BlockUtils.isFenceLike
import net.minecraft.block.Blocks
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class LiveSimulationEnvironment(
	private val world: World,
	private val player: ClientPlayerEntity,
	private val entityCollisions: Boolean = false,
) : SimulationEnvironment {

	override fun slipperiness(pos: BlockPos): Double =
		world.getBlockState(pos).block.slipperiness.toDouble()

	override fun velocityMultiplier(pos: BlockPos): Double =
		world.getBlockState(pos).block.velocityMultiplier.toDouble()

	override fun jumpVelocityMultiplier(pos: BlockPos): Double =
		world.getBlockState(pos).block.jumpVelocityMultiplier.toDouble()

	override fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
	): Vec3d {
		if (!entityCollisions) return VanillaBlockCollisionResolver.adjust(
			movement = movement,
			boundingBox = boundingBox,
			onGround = onGround,
			stepHeight = stepHeight,
			collisionShapes = { box -> world.getBlockCollisions(player, box).toList() },
		)
		val prevBox = player.boundingBox
		val prevOnGround = player.isOnGround
		player.boundingBox = boundingBox
		player.isOnGround = onGround
		return try {
			player.adjustMovementForCollisions(movement)
		} finally {
			player.boundingBox = prevBox
			player.isOnGround = prevOnGround
		}
	}

	override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? {
		val minX = MathHelper.floor(box.minX - 1.0E-7) - 1
		val maxX = MathHelper.floor(box.maxX + 1.0E-7) + 1
		val minY = MathHelper.floor(box.minY - 1.0E-7) - 1
		val maxY = MathHelper.floor(box.maxY + 1.0E-7) + 1
		val minZ = MathHelper.floor(box.minZ - 1.0E-7) - 1
		val maxZ = MathHelper.floor(box.maxZ + 1.0E-7) + 1

		var best: BlockPos? = null
		var bestDistance = Double.MAX_VALUE
		val mutable = BlockPos.Mutable()
		for (y in minY..maxY) {
			for (z in minZ..maxZ) {
				for (x in minX..maxX) {
					val pos = mutable.set(x, y, z)
					val shape = world.getBlockState(pos).getCollisionShape(world, pos)
					if (shape.isEmpty) continue
					val collides = shape
						.offset(x.toDouble(), y.toDouble(), z.toDouble())
						.boundingBoxes
						.any { it.intersects(box) }
					if (!collides) continue

					val candidate = pos.toImmutable()
					val distance = candidate.getSquaredDistance(entityPos)
					if (distance < bestDistance ||
						(distance == bestDistance && (best == null || best < candidate))
					) {
						best = candidate
						bestDistance = distance
					}
				}
			}
		}
		return best
	}

	override fun isSpaceEmpty(box: Box): Boolean = world.isSpaceEmpty(player, box)

	override fun isFenceLike(pos: BlockPos): Boolean = world.getBlockState(pos).isFenceLike()

	override fun isClimbable(pos: BlockPos): Boolean =
		world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)

	override fun bounceFactor(pos: BlockPos): Double {
		val state = world.getBlockState(pos)
		return when {
			state.isOf(Blocks.SLIME_BLOCK) -> SLIME_BOUNCE_FACTOR
			state.block is net.minecraft.block.BedBlock ->
				BlockPhysicsCapture.BED_BOUNCE_FACTOR
			else -> 0.0
		}
	}

	override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
		world.getBlockState(pos).isOf(Blocks.SLIME_BLOCK)

	companion object {

		const val SLIME_BOUNCE_FACTOR = 1.0
	}
}
