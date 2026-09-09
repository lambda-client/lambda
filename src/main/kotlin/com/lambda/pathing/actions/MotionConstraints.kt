package com.lambda.pathing.actions

data class MotionConstraints(
	val maxFrames: Int = 160,
	val maxYawDegreesPerFrame: Double = 30.0,
	val goalRadius: Double = 0.20,
	val stoppedSpeed: Double = DEFAULT_STOPPED_SPEED,
	val stableStopFrames: Int = 3,
	val maxSafeFallDistance: Double = 3.0,
	val brakeDistances: List<Double> = listOf(0.25, 0.35, 0.45, 0.55, 0.70, 0.90, 1.15),
	val stepUpJumpLeadDistances: List<Double> = listOf(0.30, 0.55, 0.80, 1.05),
	val sprintModes: List<Boolean> = listOf(true, false),

	val touchArrival: Boolean = false,
	val touchRestRadius: Double = 1.6,
) {
	init {
		require(maxFrames > 0)
		require(maxYawDegreesPerFrame > 0.0 && maxYawDegreesPerFrame.isFinite())
		require(goalRadius > 0.0 && goalRadius.isFinite())
		require(stoppedSpeed >= 0.0 && stoppedSpeed.isFinite())
		require(stableStopFrames > 0)
		require(maxSafeFallDistance >= 0.0 && maxSafeFallDistance.isFinite())
		require(brakeDistances.isNotEmpty() && brakeDistances.all { it > 0.0 && it.isFinite() })
		require(stepUpJumpLeadDistances.isNotEmpty() && stepUpJumpLeadDistances.all { it > 0.0 && it.isFinite() })
		require(sprintModes.isNotEmpty())
		require(touchRestRadius > 0.0 && touchRestRadius.isFinite())
	}

	companion object {
		const val DEFAULT_STOPPED_SPEED = 0.012
	}
}

data class TerminalApproach(
	val sprint: Boolean,
	val lookAheadNodes: Int,
	val brakeDistance: Double,
	val stepUpJumpLeadDistance: Double?,
)
