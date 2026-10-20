package com.lambda.pathing.actions

import com.lambda.pathing.physics.FallLimits
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.snapshot.HazardRules

data class SimpleMoveOptions(
	val allowDiagonal: Boolean = true,
	val allowStepUp: Boolean = true,
	val maxWalkOffDepth: Int = 3,
	val maxDropSpan: Int = 2,
	val allowClimbing: Boolean = false,
	val allowSwimming: Boolean = true,
	val allowDiving: Boolean = true,
	val allowJumpCandidates: Boolean = true,
	val maxJumpSpan: Int = 5,
	val maxJumpDrop: Int = 1,
	val allowOffAxisJumps: Boolean = true,
	val allowDeepDropJumps: Boolean = true,
	val allowSlimeBounces: Boolean = false,
	val maxBounceDrop: Int = 8,
	val allowDeepFalls: Boolean = true,
	val allowNeoJumps: Boolean = false,
	val fallLimits: FallLimits = FallLimits(),
	val hazards: HazardRules = HazardRules.AVOID_ALL,
	val avoidMobs: Boolean = false,
	val airReserve: Int = DEFAULT_AIR_RESERVE,
	val jumpPenaltyTicks: Double = 0.0,
	val frontierTicksPerBlock: Double = DEFAULT_FRONTIER_TICKS_PER_BLOCK,
) {
	init {
		require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
		require(maxDropSpan >= 1) { "maxDropSpan must reach at least the adjacent stance" }
		require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
		require(maxBounceDrop >= 3) { "a bounce needs a fall deep enough to rebound from" }
		require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
		require(frontierTicksPerBlock >= 0.0) { "frontierTicksPerBlock must be non-negative" }
		require(airReserve in 0 until Vanilla.MAX_AIR) { "airReserve must leave air to spend" }
	}

	companion object {
		val DEFAULT_FRONTIER_TICKS_PER_BLOCK = 2.0 / CoarseMoveRates.SWIM_BLOCKS_PER_TICK

		const val DEFAULT_AIR_RESERVE = 60
	}
}
