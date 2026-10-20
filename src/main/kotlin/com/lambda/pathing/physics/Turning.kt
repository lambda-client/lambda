package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.MouseHand
import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.interaction.managers.rotating.Rotation
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class Turning(
	private val maxDegreesPerTick: Double,
	private val look: MouseLook? = null,
	private val hand: MouseHand.Limits? = null,
) {
	val unlimited: Boolean get() = hand == null || hand.noise == 0.0 && hand.acceleration >= 2.0 * hand.speed

	fun turn(observed: MovementSimulationState, desired: Rotation, cap: Double = Double.POSITIVE_INFINITY): Rotation {
		val from = observed.rotation
		val look = look
		val hand = hand
		if (look == null || hand == null) {
			val limit = minOf(maxDegreesPerTick, cap)
			val pitchChange = (desired.pitch - from.pitch).coerceIn(-limit, limit)
			val clamped = Rotation(
				from.yaw + Rotation.wrap(desired.yaw - from.yaw).coerceIn(-limit, limit),
				if (pitchChange == 0.0) from.pitch else from.pitch + pitchChange,
			)
			return look?.nearest(from, clamped) ?: clamped
		}
		if (look.reaches(from, desired) && hypot(observed.turn.yaw, observed.turn.pitch) <= hand.acceleration) return from
		val speed = minOf(cap, hand.speed + hand.noise * MouseHand.gaussian(noiseKey(observed))).coerceAtLeast(look.degreesPerCount)
		val velocity = MouseHand.velocity(Rotation.wrap(desired.yaw - from.yaw), desired.pitch - from.pitch, observed.turn.yaw, observed.turn.pitch, speed, hand.acceleration)
		return look.turn(from, (velocity.yaw / look.degreesPerCount).roundToInt(), (velocity.pitch / look.degreesPerCount).roundToInt())
	}

	fun yawTo(observed: MovementSimulationState, yaw: Double, cap: Double = Double.POSITIVE_INFINITY): Rotation =
		turn(observed, Rotation(yaw, observed.rotation.pitch), cap)

	fun reached(current: Rotation, target: Rotation): Boolean {
		val tolerance = look?.let { it.degreesPerCount / 2 } ?: ALIGNED_DEGREES
		return abs(Rotation.wrap(target.yaw - current.yaw)) <= tolerance && abs(target.pitch - current.pitch) <= tolerance
	}

	private fun noiseKey(state: MovementSimulationState): Long {
		var key = NOISE_SALT
		for (bits in longArrayOf(
			state.position.x.toRawBits(), state.position.y.toRawBits(), state.position.z.toRawBits(),
			state.rotation.yaw.toRawBits(), state.rotation.pitch.toRawBits(),
		)) key = (key xor bits) * MIX
		return key
	}

	private companion object {
		const val ALIGNED_DEGREES = 1e-6
		const val NOISE_SALT = 0x2545F4914F6CDD1DL
		const val MIX = -0x61c8864680b583ebL
	}
}
