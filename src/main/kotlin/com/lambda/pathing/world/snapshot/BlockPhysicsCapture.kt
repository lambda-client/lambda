package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.physics.bounceFactor
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.util.BlockUtils.isFenceLike
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView

object BlockPhysicsCapture {

	const val SUPPORT_COLUMN_CEILING = 2.0

	private const val BODY_MIN = 0.5 - Vanilla.PLAYER_HALF_WIDTH + Vanilla.COLLISION_EPSILON

	private const val BODY_MAX = 0.5 + Vanilla.PLAYER_HALF_WIDTH - Vanilla.COLLISION_EPSILON

	private val CENTERED_PLAYER_COLUMN = VoxelShapes.cuboid(
		BODY_MIN, Vanilla.COLLISION_EPSILON, BODY_MIN,
		BODY_MAX, 1.0 - Vanilla.COLLISION_EPSILON, BODY_MAX,
	)

	private val CENTERED_SUPPORT_COLUMN = VoxelShapes.cuboid(
		BODY_MIN, 0.0, BODY_MIN,
		BODY_MAX, SUPPORT_COLUMN_CEILING, BODY_MAX,
	)

	fun coarseVoxelOf(shape: VoxelShape, bouncy: Boolean = false): CoarseVoxel {
		if (shape.isEmpty) return CoarseVoxel.AIR
		val underBody = VoxelShapes.combineAndSimplify(shape, CENTERED_SUPPORT_COLUMN, BooleanBiFunction.AND)
		val top = if (underBody.isEmpty) 0.0 else underBody.getMax(Direction.Axis.Y)
		val standsProud = top > 1.0 + Vanilla.COLLISION_EPSILON
		return CoarseVoxel(
			fullyPassable = false,
			centerPassable = !VoxelShapes.matchesAnywhere(shape, CENTERED_PLAYER_COLUMN, BooleanBiFunction.AND),
			standingSurface = top.takeIf { !standsProud && it > 0.0 }?.coerceAtMost(1.0),
			intrusionHeight = (top - 1.0).coerceAtLeast(0.0),
			bouncy = bouncy,
		)
	}

	fun capture(state: BlockState, world: BlockView, pos: BlockPos, shapeContext: ShapeContext): SnapshotBlockPhysics {
		val shape = state.getCollisionShape(world, pos, shapeContext)
		val unsupported = unsupportedPhysics(state)
		val slime = state.isOf(Blocks.SLIME_BLOCK)
		val coarseVoxel = when {
			unsupported != null -> CoarseVoxel.UNKNOWN
			state.isIn(BlockTags.CLIMBABLE) -> CoarseVoxel.CLIMBABLE
			!state.fluidState.isEmpty && shape.isEmpty -> CoarseVoxel.WATER
			else -> coarseVoxelOf(shape, bouncy = slime)
		}
		return SnapshotBlockPhysics(
			collisionShape = shape,
			slipperiness = state.block.slipperiness.toDouble(),
			velocityMultiplier = state.block.velocityMultiplier.toDouble(),
			jumpVelocityMultiplier = state.block.jumpVelocityMultiplier.toDouble(),
			unsupportedPhysics = unsupported,
			coarseVoxel = coarseVoxel,
			fenceLike = state.isFenceLike(),
			bounceFactor = state.bounceFactor(),
			dampensSteppingSpeed = slime,
			water = state.fluidState.takeIf { it.isIn(FluidTags.WATER) }?.let {
				WaterPhysics(it.getHeight(world, pos), it.getVelocity(world, pos))
			},
			exceedsCube = state.exceedsCube(),
		)
	}

	private fun unsupportedPhysics(state: BlockState): UnsupportedPhysics? = when {
		!state.fluidState.isEmpty && (!state.fluidState.isIn(FluidTags.WATER) || state.isIn(BlockTags.CLIMBABLE)) -> UnsupportedPhysicsKind.FLUID
		state.isOf(Blocks.BUBBLE_COLUMN) -> UnsupportedPhysicsKind.FLUID
		state.isOf(Blocks.COBWEB) -> UnsupportedPhysicsKind.COBWEB
		state.isOf(Blocks.POWDER_SNOW) -> UnsupportedPhysicsKind.POWDER_SNOW
		state.isOf(Blocks.SWEET_BERRY_BUSH) -> UnsupportedPhysicsKind.SWEET_BERRY_BUSH
		state.isOf(Blocks.HONEY_BLOCK) -> UnsupportedPhysicsKind.HONEY_SIDE_EFFECTS
		else -> null
	}?.let { kind ->
		UnsupportedPhysics(
			kind = kind,
			blockId = Registries.BLOCK.getId(state.block).toString(),
		)
	}
}
