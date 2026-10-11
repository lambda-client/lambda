package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing

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

internal fun heldStanceOf(view: CoarseVoxelView, state: MovementSimulationState): Stance {
	val floored = stanceOf(state)
	if (!state.onGround || !Footing.involvesPartial(view, floored.x, floored.y, floored.z)) return floored
	val x = state.position.x
	val z = state.position.z
	if (Footing.holds(view, floored).at(x, z) != null) return floored
	for ((dx, dz) in NEIGHBOR_OFFSETS) {
		val neighbor = floored.offset(dx, 0, dz)
		if (Footing.holds(view, neighbor).at(x, z) != null) return neighbor
	}
	return floored
}

private val NEIGHBOR_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

internal fun ValueField.floatsAt(state: MovementSimulationState, level: Int): Boolean {
	val stance = stanceOf(state)
	return state.touchingWater && stance.y == level && isStance(stance) && isMapped(stance)
}

internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
	if (!state.onGround) return null
	val support = state.supportingBlockPos ?: return null
	val base = Stance.of(state.position, true)
	if (support.x == base.x && support.z == base.z) return null
	return Stance(support.x, support.y + 1, support.z)
}
