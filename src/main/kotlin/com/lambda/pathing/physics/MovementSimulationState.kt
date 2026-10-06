package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.mixin.entity.ClientPlayerEntityAccessor
import com.lambda.util.math.DOWN
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.player.MovementUtils.moveYaw
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull

data class MovementSimulationState(
	val position: Vec3d,
	val rotation: Rotation,
	val velocity: Vec3d,
	val boundingBox: Box,
	val onGround: Boolean,
	val isJumping: Boolean,
	val isSprinting: Boolean,
	val isSneaking: Boolean,
	val jumpingCooldown: Int,
	val velocityAffectingPos: BlockPos,
	val horizontalCollision: Boolean,
	val collidedSoftly: Boolean,
	val verticalCollision: Boolean,
	val supportingBlockPos: BlockPos? = null,
	val doubleTapSprintTicks: Int = 0,
	val hadForwardMovement: Boolean = false,
) {
	companion object {
		fun from(player: ClientPlayerEntity, isJumping: Boolean = false) = MovementSimulationState(
			position = player.pos,
			rotation = Rotation(player.moveYaw, player.pitch),
			velocity = player.velocity,
			boundingBox = player.boundingBox,
			onGround = player.isOnGround,
			isJumping = isJumping,
			isSprinting = player.isSprinting,
			isSneaking = player.isSneaking,
			jumpingCooldown = player.jumpingCooldown,
			velocityAffectingPos = player.velocityAffectingPos,
			horizontalCollision = player.horizontalCollision,
			collidedSoftly = player.collidedSoftly,
			verticalCollision = player.verticalCollision,
			supportingBlockPos = player.supportingBlockPos.getOrNull(),
			doubleTapSprintTicks = (player as ClientPlayerEntityAccessor).`lambda$getTicksLeftToDoubleTapSprint`(),
			hadForwardMovement = player.input.hasForwardMovement(),
		)

		fun synthetic(
			profile: PlayerPhysicsProfile,
			position: Vec3d,
			rotation: Rotation,
			velocity: Vec3d = Vec3d.ZERO,
			onGround: Boolean = true,
			isSprinting: Boolean = false,
			isSneaking: Boolean = false,
		): MovementSimulationState {
			val halfWidth = profile.width * 0.5
			return MovementSimulationState(
				position = position,
				rotation = rotation,
				velocity = velocity,
				boundingBox = Box(
					position.x - halfWidth, position.y, position.z - halfWidth,
					position.x + halfWidth, position.y + profile.height, position.z + halfWidth,
				),
				onGround = onGround,
				isJumping = false,
				isSprinting = isSprinting,
				isSneaking = isSneaking,
				jumpingCooldown = 0,
				velocityAffectingPos = (position + DOWN * Vanilla.VELOCITY_AFFECTING_POS_OFFSET.toDouble()).flooredBlockPos,
				horizontalCollision = false,
				collidedSoftly = false,
				verticalCollision = false,
			)
		}
	}
}
