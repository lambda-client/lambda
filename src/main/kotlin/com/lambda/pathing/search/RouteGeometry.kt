package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.Medium
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

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

internal fun groundJumps(
	entry: MovementSimulationState,
	frames: List<SimulatedTrajectoryFrame>,
): Int {
	var previous = entry
	var jumps = 0
	for ((_, input, state) in frames) {
		if (input.jump && previous.onGround && !previous.touchingWater && !state.onGround && state.velocity.y > 0.0) jumps++
		previous = state
	}
	return jumps
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
	if (!state.onGround && view.medium(floored.x, floored.y, floored.z) != Medium.WATER &&
		view.medium(floored.x, floored.y + 1, floored.z) == Medium.WATER
	) return floored.offset(0, 1, 0)
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

internal fun pressesCell(state: MovementSimulationState, cell: Stance): Boolean {
	if (!state.onGround) return true
	val reach = PLATE_HALF_EXTENT + Vanilla.PLAYER_HALF_WIDTH - PLATE_MARGIN
	return abs(state.position.x - cell.x - 0.5) < reach && abs(state.position.z - cell.z - 0.5) < reach && state.position.y < cell.y + PLATE_HEIGHT
}

private const val PLATE_HALF_EXTENT = 0.4375

private const val PLATE_HEIGHT = 0.25

private const val PLATE_MARGIN = 0.05

internal fun footprintStances(state: MovementSimulationState): List<Stance> {
	val base = stanceOf(state)
	val x = state.position.x
	val z = state.position.z
	val half = Vanilla.PLAYER_HALF_WIDTH.toDouble()
	val cells = ArrayList<Stance>(3)
	for (cx in floor(x - half).toInt()..floor(x + half).toInt()) for (cz in floor(z - half).toInt()..floor(z + half).toInt()) {
		if (cx != base.x || cz != base.z) cells += Stance(cx, base.y, cz)
	}
	return cells.sortedBy { hypot(it.x + 0.5 - x, it.z + 0.5 - z) }
}

internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
	if (!state.onGround) return null
	val support = state.supportingBlockPos ?: return null
	val base = Stance.of(state.position, true)
	if (support.x == base.x && support.z == base.z) return null
	return Stance(support.x, support.y + 1, support.z)
}
