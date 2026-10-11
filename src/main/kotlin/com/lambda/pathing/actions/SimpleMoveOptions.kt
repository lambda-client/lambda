package com.lambda.pathing.actions

import com.lambda.pathing.physics.FallLimits

data class SimpleMoveOptions(
	val allowDiagonal: Boolean = true,
	val allowStepUp: Boolean = true,
	val maxWalkOffDepth: Int = 3,
	val maxDropSpan: Int = 2,
	val allowClimbing: Boolean = false,
	val allowSwimming: Boolean = true,
	val allowJumpCandidates: Boolean = true,
	val maxJumpSpan: Int = 5,
	val maxJumpDrop: Int = 1,
	val allowOffAxisJumps: Boolean = true,
	val allowDeepDropJumps: Boolean = true,
	val allowSlimeBounces: Boolean = false,
	val maxBounceDrop: Int = 8,
	val allowDeepFalls: Boolean = true,
	val fallLimits: FallLimits = FallLimits(),
) {
	init {
		require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
		require(maxDropSpan >= 1) { "maxDropSpan must reach at least the adjacent stance" }
		require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
		require(maxBounceDrop >= 3) { "a bounce needs a fall deep enough to rebound from" }
		require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
	}
}
