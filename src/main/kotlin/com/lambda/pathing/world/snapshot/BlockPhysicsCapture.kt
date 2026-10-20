package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.BubbleColumn
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.physics.bounceFactor
import com.lambda.pathing.physics.bubbles
import com.lambda.pathing.physics.fallRule
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CollisionClass
import com.lambda.util.BlockUtils.isFenceLike
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CampfireBlock
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

	private val SIDE_PASSAGES = listOf(
		VoxelShapes.cuboid(BODY_MAX, Vanilla.COLLISION_EPSILON, BODY_MIN, 1.0, 1.0 - Vanilla.COLLISION_EPSILON, BODY_MAX),
		VoxelShapes.cuboid(0.0, Vanilla.COLLISION_EPSILON, BODY_MIN, BODY_MIN, 1.0 - Vanilla.COLLISION_EPSILON, BODY_MAX),
		VoxelShapes.cuboid(BODY_MIN, Vanilla.COLLISION_EPSILON, BODY_MAX, BODY_MAX, 1.0 - Vanilla.COLLISION_EPSILON, 1.0),
		VoxelShapes.cuboid(BODY_MIN, Vanilla.COLLISION_EPSILON, 0.0, BODY_MAX, 1.0 - Vanilla.COLLISION_EPSILON, BODY_MIN),
	)

	fun coarseVoxelOf(shape: VoxelShape, bouncy: Boolean = false, velocityMultiplier: Double = 1.0): CoarseVoxel {
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
			openSides = SIDE_PASSAGES.foldIndexed(0) { index, open, passage ->
				if (VoxelShapes.matchesAnywhere(shape, passage, BooleanBiFunction.AND)) open else open or (1 shl index)
			},
			partial = CollisionClass.of(shape) == CollisionClass.PARTIAL,
			velocityMultiplier = velocityMultiplier,
		)
	}

	fun capture(state: BlockState, world: BlockView, pos: BlockPos, shapeContext: ShapeContext, hazards: HazardRules = HazardRules.AVOID_ALL): SnapshotBlockPhysics {
		val shape = if (state.isOf(Blocks.POWDER_SNOW)) VoxelShapes.empty() else state.getCollisionShape(world, pos, shapeContext)
		val unsupported = unsupportedPhysics(state, hazards)
		val slime = state.isOf(Blocks.SLIME_BLOCK)
		val coarseVoxel = when {
			unsupported != null -> CoarseVoxel.UNKNOWN
			state.isIn(BlockTags.CLIMBABLE) -> CoarseVoxel.CLIMBABLE
			state.bubbles() == BubbleColumn.DRAG -> CoarseVoxel.HAZARD
			!state.fluidState.isEmpty && shape.isEmpty -> CoarseVoxel.WATER
			else -> coarseVoxelOf(shape, bouncy = slime, velocityMultiplier = state.block.velocityMultiplier.toDouble())
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
				WaterPhysics(it.getHeight(world, pos), it.getVelocity(world, pos), state.bubbles())
			},
			hotFloor = hazards.sneakOnMagma && state.isOf(Blocks.MAGMA_BLOCK),
			fallRule = if (cushionedByWater(state, shape, world, pos)) FallRule.NONE else state.fallRule(),
			honey = state.isOf(Blocks.HONEY_BLOCK),
			exceedsCube = state.exceedsCube(),
		)
	}

	private fun cushionedByWater(state: BlockState, shape: VoxelShape, world: BlockView, pos: BlockPos): Boolean {
		val fluid = state.fluidState
		return !(fluid.isEmpty || !fluid.isIn(FluidTags.WATER) || shape.isEmpty) && shape.getMax(Direction.Axis.Y) < fluid.getHeight(world, pos)
	}

	private fun unsupportedPhysics(state: BlockState, hazards: HazardRules): UnsupportedPhysics? = when {
		!state.fluidState.isEmpty && (!state.fluidState.isIn(FluidTags.WATER) || state.isIn(BlockTags.CLIMBABLE)) -> UnsupportedPhysicsKind.FLUID
		state.isOf(Blocks.COBWEB) -> UnsupportedPhysicsKind.COBWEB
		state.isOf(Blocks.POWDER_SNOW) -> UnsupportedPhysicsKind.POWDER_SNOW
		state.isOf(Blocks.SWEET_BERRY_BUSH) -> UnsupportedPhysicsKind.SWEET_BERRY_BUSH
		hazards.avoidFire && (state.isIn(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state)) -> UnsupportedPhysicsKind.FIRE
		hazards.avoidCacti && state.isOf(Blocks.CACTUS) -> UnsupportedPhysicsKind.CACTUS
		else -> null
	}?.let { kind ->
		UnsupportedPhysics(
			kind = kind,
			blockId = Registries.BLOCK.getId(state.block).toString(),
		)
	}
}
