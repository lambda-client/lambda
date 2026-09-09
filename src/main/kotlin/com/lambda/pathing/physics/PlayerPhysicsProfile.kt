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
	val eyeHeight: Double,
	val crouchHeight: Double = VANILLA_CROUCH_HEIGHT,
	val crouchEyeHeight: Double = VANILLA_CROUCH_EYE_HEIGHT,
	val sprintWindowTicks: Int = DEFAULT_SPRINT_WINDOW_TICKS,
) {
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
				eyeHeight.closeTo(other.eyeHeight) &&
				crouchHeight.closeTo(other.crouchHeight) &&
				crouchEyeHeight.closeTo(other.crouchEyeHeight) &&
				sprintWindowTicks == other.sprintWindowTicks

	private fun Double.closeTo(other: Double): Boolean =
		abs(this - other) <= PROFILE_EPSILON * max(1.0, max(abs(this), abs(other)))

	companion object {
		const val SPRINT_SPEED_MULTIPLIER = 1.3

		private const val PROFILE_EPSILON = 1e-7

		const val DEFAULT_SPRINT_WINDOW_TICKS = 7

		const val VANILLA_CROUCH_HEIGHT = 1.5
		const val VANILLA_CROUCH_EYE_HEIGHT = 1.27

		private val SPRINTING_SPEED_MODIFIER_ID = Identifier.ofVanilla("sprinting")

		fun capture(player: ClientPlayerEntity): PlayerPhysicsProfile {
			val liveSpeed = player.movementSpeed.toDouble()
			val sprintBoosted = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
				?.hasModifier(SPRINTING_SPEED_MODIFIER_ID) == true
			val standingDimensions = player.getDimensions(EntityPose.STANDING)
			return PlayerPhysicsProfile(
				movementSpeed = if (sprintBoosted) liveSpeed / SPRINT_SPEED_MULTIPLIER else liveSpeed,
				sneakSpeedModifier = player.getAttributeValue(EntityAttributes.SNEAKING_SPEED),
				gravity = player.getAttributeValue(EntityAttributes.GRAVITY),
				jumpStrength = player.getAttributeValue(EntityAttributes.JUMP_STRENGTH),
				stepHeight = player.stepHeight.toDouble(),
				jumpBoostVelocityModifier = player.jumpBoostVelocityModifier.toDouble(),
				slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING),
				width = standingDimensions.width.toDouble(),
				height = standingDimensions.height.toDouble(),
				eyeHeight = player.getEyeHeight(EntityPose.STANDING).toDouble(),
				crouchHeight = player.getDimensions(EntityPose.CROUCHING).height.toDouble(),
				crouchEyeHeight = player.getEyeHeight(EntityPose.CROUCHING).toDouble(),
				sprintWindowTicks = MinecraftClient.getInstance().options.sprintWindow.value,
			)
		}
	}
}
