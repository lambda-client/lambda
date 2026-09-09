package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.util.BlockUtils.isFenceLike
import net.minecraft.block.BedBlock
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
import net.minecraft.world.World

object BlockPhysicsCapture {

	const val BED_BOUNCE_FACTOR = 0.6600000262260437

	const val SUPPORT_COLUMN_CEILING = 2.0

	private const val COLLISION_EPSILON = 1.0E-7

	private val CENTERED_PLAYER_COLUMN = VoxelShapes.cuboid(
		0.2 + COLLISION_EPSILON,
		COLLISION_EPSILON,
		0.2 + COLLISION_EPSILON,
		0.8 - COLLISION_EPSILON,
		1.0 - COLLISION_EPSILON,
		0.8 - COLLISION_EPSILON,
	)

	private val CENTERED_SUPPORT_COLUMN = VoxelShapes.cuboid(
		0.2 + COLLISION_EPSILON,
		0.0,
		0.2 + COLLISION_EPSILON,
		0.8 - COLLISION_EPSILON,
		SUPPORT_COLUMN_CEILING,
		0.8 - COLLISION_EPSILON,
	)

	fun coarseVoxelOf(shape: VoxelShape, bouncy: Boolean = false): CoarseVoxel {
		if (shape.isEmpty) return CoarseVoxel.AIR
		val underBody = VoxelShapes.combineAndSimplify(shape, CENTERED_SUPPORT_COLUMN, BooleanBiFunction.AND)
		val top = if (underBody.isEmpty) 0.0 else underBody.getMax(Direction.Axis.Y)
		val standsProud = top > 1.0 + COLLISION_EPSILON
		return CoarseVoxel(
			fullyPassable = false,
			centerPassable = !VoxelShapes.matchesAnywhere(shape, CENTERED_PLAYER_COLUMN, BooleanBiFunction.AND),
			standingSurface = top.takeIf { !standsProud && it > 0.0 }?.coerceAtMost(1.0),
			intrusionHeight = (top - 1.0).coerceAtLeast(0.0),
			bouncy = bouncy,
		)
	}

	fun capture(state: BlockState, world: World, pos: BlockPos, shapeContext: ShapeContext): SnapshotBlockPhysics {
		val shape = state.getCollisionShape(world, pos, shapeContext)
		val unsupported = unsupportedPhysics(state)
		val medium = mediumOf(state)
		val slime = state.isOf(Blocks.SLIME_BLOCK)
		val bed = state.block is BedBlock
		val coarseVoxel = if (medium == Medium.CLIMBABLE) {
			CoarseVoxel.of(Medium.CLIMBABLE)
		} else if (unsupported != null) {
			CoarseVoxel.UNKNOWN
		} else {

			coarseVoxelOf(shape, bouncy = slime)
		}
		return SnapshotBlockPhysics(
			collisionShape = shape,
			slipperiness = state.block.slipperiness.toDouble(),
			velocityMultiplier = state.block.velocityMultiplier.toDouble(),
			jumpVelocityMultiplier = state.block.jumpVelocityMultiplier.toDouble(),
			unsupportedPhysics = unsupported,
			coarseVoxel = coarseVoxel,
			fenceLike = state.isFenceLike(),
			bounceFactor = if (slime) 1.0 else if (bed) BED_BOUNCE_FACTOR else 0.0,
			dampensSteppingSpeed = slime,
		)
	}

	fun mediumOf(state: BlockState): Medium = when {
		!state.fluidState.isEmpty ->
			if (state.fluidState.isIn(FluidTags.LAVA)) Medium.LAVA else Medium.WATER
		state.isIn(BlockTags.CLIMBABLE) -> Medium.CLIMBABLE
		state.isOf(Blocks.COBWEB) -> Medium.COBWEB
		state.isOf(Blocks.POWDER_SNOW) -> Medium.POWDER_SNOW
		else -> Medium.SOLID
	}

	private fun unsupportedPhysics(state: BlockState): UnsupportedPhysics? = when {
		!state.fluidState.isEmpty -> UnsupportedPhysicsKind.FLUID
		state.isOf(Blocks.COBWEB) -> UnsupportedPhysicsKind.COBWEB
		state.isOf(Blocks.POWDER_SNOW) -> UnsupportedPhysicsKind.POWDER_SNOW
		state.isOf(Blocks.HONEY_BLOCK) -> UnsupportedPhysicsKind.HONEY_SIDE_EFFECTS
		else -> null
	}?.let { kind ->
		UnsupportedPhysics(
			kind = kind,
			blockId = Registries.BLOCK.getId(state.block).toString(),
		)
	}
}
