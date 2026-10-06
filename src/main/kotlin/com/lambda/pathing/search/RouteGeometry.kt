package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal fun collisionEvents(
	entry: MovementSimulationState,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entry.horizontalCollision
	var events = 0
	for ((_, _, state) in frames) {
		if (state.horizontalCollision && !previous) events++
		previous = state.horizontalCollision
	}
	return events
}

internal fun inputSwitches(
	entryInput: MovementSimulationInput?,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entryInput
	var switches = 0
	for ((_, input) in frames) {
		if (previous != null && previous != input) switches++
		previous = input
	}
	return switches
}

internal fun launchSeedFrame(diagnostic: TrajectoryDiagnostic): Int? = when (diagnostic) {
	is TrajectoryDiagnostic.FellBelowRoute,
	is TrajectoryDiagnostic.HorizontalCollision,
	is TrajectoryDiagnostic.UnsupportedPhysics -> diagnostic.frame

	else -> null
}

internal fun stanceOf(state: MovementSimulationState): Stance = Stance.of(state.position, state.onGround)

internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
	if (!state.onGround) return null
	val support = state.supportingBlockPos ?: return null
	val base = Stance.of(state.position, true)
	if (support.x == base.x && support.z == base.z) return null
	return Stance(support.x, support.y + 1, support.z)
}
