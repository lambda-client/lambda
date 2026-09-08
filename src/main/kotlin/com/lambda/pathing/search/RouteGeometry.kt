package com.lambda.pathing.search

import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal fun collisionEvents(
	entry: MovementSimulationState,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entry.horizontalCollision
	var events = 0
	for (frame in frames) {
		if (frame.state.horizontalCollision && !previous) events++
		previous = frame.state.horizontalCollision
	}
	return events
}

/** [collisionEvents] restricted to events that begin while the body is airborne: a mid-flight wall, not a grounded graze. */
internal fun airborneCollisionEvents(
	entry: MovementSimulationState,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entry.horizontalCollision
	var events = 0
	for (frame in frames) {
		if (frame.state.horizontalCollision && !previous && !frame.state.onGround) events++
		previous = frame.state.horizontalCollision
	}
	return events
}

internal fun inputSwitches(
	entryInput: MovementSimulationInput?,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entryInput
	var switches = 0
	for (frame in frames) {
		if (previous != null && previous != frame.input) switches++
		previous = frame.input
	}
	return switches
}

internal fun launchMargin(
	frames: List<SimulatedTrajectoryFrame>,
	hazardFrame: Int?,
	cap: Int = LAUNCH_MARGIN_FRAME_CAP,
): Int {
	val hazard = hazardFrame ?: return 0
	val launch = frames.firstOrNull { it.input.jump }?.index ?: return 0
	return (hazard - launch).coerceIn(0, cap)
}

internal fun launchSeedFrame(diagnostic: TrajectoryDiagnostic): Int? = when (diagnostic) {
	is TrajectoryDiagnostic.FellBelowRoute,
	is TrajectoryDiagnostic.HorizontalCollision,
	is TrajectoryDiagnostic.UnsupportedPhysics -> diagnostic.frame

	else -> null
}

internal const val LAUNCH_MARGIN_FRAME_CAP = 3
