package com.lambda.pathing.actions

import com.lambda.interaction.managers.rotating.MouseHand
import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.pathing.physics.FallLimits
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.physics.Turning

data class MotionConstraints(
	val maxFrames: Int = DEFAULT_MAX_FRAMES,
	val maxYawDegreesPerFrame: Double = 30.0,
	val goalRadius: Double = 0.20,
	val stableStopFrames: Int = 3,
	val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
	val sprintModes: List<Boolean> = listOf(true, false),
	val touchArrival: Boolean = false,
	val touchRestRadius: Double = DEFAULT_TOUCH_REST_RADIUS,
	val fallLimits: FallLimits = FallLimits(),
	val jumpPenaltyTicks: Double = 0.0,
	val avoidLavaEdges: Boolean = false,
	val airReserve: Int = SimpleMoveOptions.DEFAULT_AIR_RESERVE,
	val look: MouseLook? = null,
	val hand: MouseHand.Limits? = null,
) {
	val turning: Turning = Turning(maxYawDegreesPerFrame, look, hand)

	init {
		require(maxFrames > 0)
		require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
		require(goalRadius > 0.0 && goalRadius.isFinite())
		require(stableStopFrames > 0)
		require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
		require(sprintModes.isNotEmpty())
		require(touchRestRadius > 0.0 && touchRestRadius.isFinite())
		require(airReserve >= 0)
	}

	val maxSafeFallDistance: Double = fallLimits.maxSafeFallDistance

	fun maxFallOnto(rule: FallRule): Double = fallLimits.maxFallOnto(rule)

	companion object {
		const val STOPPED_SPEED = 0.012

		const val DEFAULT_TOUCH_REST_RADIUS = 1.6

		const val DEFAULT_MAX_FRAMES = 160
	}
}

