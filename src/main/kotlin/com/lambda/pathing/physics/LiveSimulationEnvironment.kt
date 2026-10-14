package com.lambda.pathing.physics

import com.lambda.util.BlockUtils.isFenceLike
import net.minecraft.block.Blocks
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class LiveSimulationEnvironment(
	private val world: World,
	private val player: ClientPlayerEntity,
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

	override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
		closestSupportingBlock(box, entityPos) { pos ->
			val shape = world.getBlockState(pos).getCollisionShape(world, pos)
			!shape.isEmpty && shape.offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
				.boundingBoxes
				.any { it.intersects(box) }
		}

	override fun isSpaceEmpty(box: Box): Boolean = world.isSpaceEmpty(player, box)

	override fun isFenceLike(pos: BlockPos): Boolean = world.getBlockState(pos).isFenceLike()

	override fun isClimbable(pos: BlockPos): Boolean =
		world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)

	override fun bounceFactor(pos: BlockPos): Double = world.getBlockState(pos).bounceFactor()

	override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
		world.getBlockState(pos).isOf(Blocks.SLIME_BLOCK)

	override fun honey(pos: BlockPos): Boolean = world.getBlockState(pos).isOf(Blocks.HONEY_BLOCK)

	override fun hotFloor(pos: BlockPos): Boolean = world.getBlockState(pos).isOf(Blocks.MAGMA_BLOCK)

	override fun fallRule(pos: BlockPos): FallRule = world.getBlockState(pos).fallRule()

	override fun water(pos: BlockPos): WaterPhysics? = world.getFluidState(pos).takeIf { it.isIn(FluidTags.WATER) }?.let {
		WaterPhysics(it.getHeight(world, pos), it.getVelocity(world, pos), world.getBlockState(pos).bubbles())
	}

	override fun containsFluid(box: Box): Boolean = world.containsFluid(box)
}
