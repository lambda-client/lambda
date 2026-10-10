package com.lambda.pathing.physics

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EntityPose
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.Identifier
import kotlin.math.abs
import kotlin.math.max

data class PlayerPhysicsProfile(
	val movementSpeed: Double,
	val sneakSpeedModifier: Double,
	val gravity: Double,
	val jumpStrength: Double,
	val stepHeight: Double,
	val jumpBoostVelocityModifier: Double,
	val slowFalling: Boolean,
	val width: Double,
	val height: Double,
	val crouchHeight: Double = Vanilla.CROUCH_HEIGHT.toDouble(),
	val sprintWindowTicks: Int = Vanilla.SPRINT_WINDOW_TICKS,
	val swimmingHeight: Double = Vanilla.SWIMMING_HEIGHT.toDouble(),
	val standingEyeHeight: Double = Vanilla.STANDING_EYE_HEIGHT.toDouble(),
	val crouchingEyeHeight: Double = Vanilla.CROUCHING_EYE_HEIGHT.toDouble(),
	val swimmingEyeHeight: Double = Vanilla.SWIMMING_EYE_HEIGHT.toDouble(),
	val waterMovementEfficiency: Double = 0.0,
	val dolphinsGrace: Boolean = false,
) {
	val swimHeight: Double get() = if (standingEyeHeight < Vanilla.SWIM_HEIGHT) 0.0 else Vanilla.SWIM_HEIGHT

	fun isCompatibleWith(other: PlayerPhysicsProfile): Boolean =
		movementSpeed.closeTo(other.movementSpeed) &&
				sneakSpeedModifier.closeTo(other.sneakSpeedModifier) &&
				gravity.closeTo(other.gravity) &&
				jumpStrength.closeTo(other.jumpStrength) &&
				stepHeight.closeTo(other.stepHeight) &&
				jumpBoostVelocityModifier.closeTo(other.jumpBoostVelocityModifier) &&
				slowFalling == other.slowFalling &&
				width.closeTo(other.width) &&
				height.closeTo(other.height) &&
				crouchHeight.closeTo(other.crouchHeight) &&
				sprintWindowTicks == other.sprintWindowTicks &&
				swimmingHeight.closeTo(other.swimmingHeight) &&
				standingEyeHeight.closeTo(other.standingEyeHeight) &&
				crouchingEyeHeight.closeTo(other.crouchingEyeHeight) &&
				swimmingEyeHeight.closeTo(other.swimmingEyeHeight) &&
				waterMovementEfficiency.closeTo(other.waterMovementEfficiency) &&
				dolphinsGrace == other.dolphinsGrace

	private fun Double.closeTo(other: Double): Boolean =
		abs(this - other) <= PROFILE_EPSILON * max(1.0, max(abs(this), abs(other)))

	companion object {
		private const val PROFILE_EPSILON = 1e-7

		private val SPRINTING_SPEED_MODIFIER_ID = Identifier.ofVanilla("sprinting")

		val VANILLA = PlayerPhysicsProfile(
			movementSpeed = Vanilla.MOVEMENT_SPEED.toDouble(),
			sneakSpeedModifier = Vanilla.SNEAKING_SPEED,
			gravity = Vanilla.GRAVITY,
			jumpStrength = Vanilla.JUMP_STRENGTH.toDouble(),
			stepHeight = Vanilla.STEP_HEIGHT.toFloat().toDouble(),
			jumpBoostVelocityModifier = 0.0,
			slowFalling = false,
			width = Vanilla.PLAYER_WIDTH.toDouble(),
			height = Vanilla.PLAYER_HEIGHT.toDouble(),
		)

		fun capture(player: ClientPlayerEntity): PlayerPhysicsProfile {
			val liveSpeed = player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)
			val sprintBoosted = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
				?.hasModifier(SPRINTING_SPEED_MODIFIER_ID) == true
			val standingDimensions = player.getDimensions(EntityPose.STANDING)
			val crouchingDimensions = player.getDimensions(EntityPose.CROUCHING)
			val swimmingDimensions = player.getDimensions(EntityPose.SWIMMING)
			return PlayerPhysicsProfile(
				movementSpeed = if (sprintBoosted) liveSpeed / Vanilla.SPRINT_SPEED_MULTIPLIER else liveSpeed,
				sneakSpeedModifier = player.getAttributeValue(EntityAttributes.SNEAKING_SPEED),
				gravity = player.getAttributeValue(EntityAttributes.GRAVITY),
				jumpStrength = player.getAttributeValue(EntityAttributes.JUMP_STRENGTH),
				stepHeight = player.stepHeight.toDouble(),
				jumpBoostVelocityModifier = player.jumpBoostVelocityModifier.toDouble(),
				slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING),
				width = standingDimensions.width.toDouble(),
				height = standingDimensions.height.toDouble(),
				crouchHeight = crouchingDimensions.height.toDouble(),
				sprintWindowTicks = MinecraftClient.getInstance().options.sprintWindow.value,
				swimmingHeight = swimmingDimensions.height.toDouble(),
				standingEyeHeight = standingDimensions.eyeHeight.toDouble(),
				crouchingEyeHeight = crouchingDimensions.eyeHeight.toDouble(),
				swimmingEyeHeight = swimmingDimensions.eyeHeight.toDouble(),
				waterMovementEfficiency = player.getAttributeValue(EntityAttributes.WATER_MOVEMENT_EFFICIENCY),
				dolphinsGrace = player.hasStatusEffect(StatusEffects.DOLPHINS_GRACE),
			)
		}
	}
}
