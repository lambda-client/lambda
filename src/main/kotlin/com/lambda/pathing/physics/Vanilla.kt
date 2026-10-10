package com.lambda.pathing.physics

import net.minecraft.block.BedBlock
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks

object Vanilla {
	const val GRAVITY = 0.08
	const val SLOW_FALLING_MAX_GRAVITY = 0.01
	const val VERTICAL_DRAG = 0.98F
	const val HORIZONTAL_DRAG = 0.91F
	const val DEFAULT_SLIPPERINESS = 0.6F
	const val INPUT_DAMPING = 0.98F
	const val GROUND_ACCELERATION_SCALE = 0.21600002F
	const val AIR_ACCELERATION = 0.02F
	const val SPRINT_AIR_ACCELERATION = 0.025999999F
	const val USING_ITEM_SPEED_FACTOR = 0.2F
	const val MIN_INPUT_LENGTH_SQUARED = 1.0E-7
	const val FORWARD_INPUT_EPSILON = 1.0E-5F

	const val MOVEMENT_SPEED = 0.1F
	const val SPRINT_SPEED_MODIFIER = 0.3F
	const val SPRINT_SPEED_MULTIPLIER = 1.0 + SPRINT_SPEED_MODIFIER
	const val SNEAKING_SPEED = 0.3
	const val SPRINT_WINDOW_TICKS = 7
	const val STEP_HEIGHT = 0.6

	const val JUMP_STRENGTH = 0.42F
	const val MIN_JUMP_VELOCITY = 1.0E-5F
	const val SPRINT_JUMP_BOOST = 0.2
	const val JUMP_COOLDOWN_TICKS = 10

	const val MIN_PLAYER_HORIZONTAL_SPEED_SQUARED = 9.0E-6
	const val MIN_VERTICAL_SPEED = 0.003

	const val CLIMB_SPEED_CAP = 0.15F
	const val CLIMB_RISE_SPEED = 0.2

	const val VELOCITY_AFFECTING_POS_OFFSET = 0.500001F
	const val LANDING_POS_OFFSET = 0.2F
	const val MIN_POS_OFFSET = 1.0E-5F
	const val SUPPORTING_BLOCK_PROBE_DEPTH = 1.0E-6

	const val COLLISION_EPSILON = 1.0E-7
	const val MIN_MOVEMENT_SQUARED = 1.0E-7
	const val STEP_PROBE_DEPTH = 1.0E-5F
	const val LEDGE_CLIP_STEP = 0.05
	const val SOFT_COLLISION_MIN_SQUARED = 1.0E-5F
	const val SOFT_COLLISION_MAX_ANGLE = 0.13962634F

	const val SLIME_BOUNCE_FACTOR = 1.0
	const val BED_BOUNCE_FACTOR = 0.66F
	const val SLIME_STEP_MAX_VERTICAL_SPEED = 0.1
	const val SLIME_STEP_DRAG = 0.4
	const val SLIME_STEP_DRAG_PER_VERTICAL_SPEED = 0.2

	const val PLAYER_WIDTH = 0.6F
	const val PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0F
	const val PLAYER_HEIGHT = 1.8F
	const val CROUCH_HEIGHT = 1.5F
	const val SWIMMING_HEIGHT = 0.6F
	const val STANDING_EYE_HEIGHT = 1.62F
	const val CROUCHING_EYE_HEIGHT = 1.27F
	const val SWIMMING_EYE_HEIGHT = 0.4F

	const val SWIM_HEIGHT = 0.4
	const val WATER_DRAG = 0.8F
	const val SPRINT_WATER_DRAG = 0.9F
	const val DEPTH_STRIDER_WATER_DRAG = 0.54600006F
	const val DOLPHINS_GRACE_WATER_DRAG = 0.96F
	const val WATER_VERTICAL_DRAG = 0.8F
	const val WATER_ACCELERATION = 0.02F
	const val WATER_GRAVITY_DIVISOR = 16.0
	const val WATER_SINK_SNAP = 0.005
	const val WATER_SINK_SNAP_WINDOW = 0.003
	const val WATER_SINK_SNAP_SPEED = -0.003
	const val WATER_PUSH_SPEED = 0.014
	const val WATER_MIN_PUSH = 0.0045000000000000005
	const val WATER_MIN_PUSH_SPEED = 0.003
	const val SHALLOW_PUSH_DEPTH = 0.4
	const val FLUID_BOX_CONTRACTION = 0.001
	const val SWIM_UP_SPEED = 0.04F
	const val WATER_SINK_SPEED = 0.04F
	const val WATER_EXIT_CLEARANCE = 0.6F
	const val WATER_EXIT_SPEED = 0.3F
	const val WATER_CLIMB_SPEED = 0.2
	const val SWIM_STEER = 0.06
	const val SWIM_STEER_DIVING = 0.085
	const val SWIM_DIVING_LOOK = -0.2
	const val POSE_CLEARANCE = 1.0E-7
}

internal fun BlockState.bounceFactor(): Double = when {
	isOf(Blocks.SLIME_BLOCK) -> Vanilla.SLIME_BOUNCE_FACTOR
	block is BedBlock -> Vanilla.BED_BOUNCE_FACTOR.toDouble()
	else -> 0.0
}
