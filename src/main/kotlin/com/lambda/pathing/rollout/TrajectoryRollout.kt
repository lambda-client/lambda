package com.lambda.pathing.rollout

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.SimulationEnvironment
import com.lambda.pathing.physics.SimulationEnvironmentException
import com.lambda.pathing.physics.SnapshotSectionUnavailableException

data class SimulatedTrajectoryFrame(
	val index: Int,
	val input: MovementSimulationInput,
	val state: MovementSimulationState,
)

sealed interface TrajectoryRolloutTermination {
	data object Completed : TrajectoryRolloutTermination

	data class Blocked(val frame: Int, val section: PathingSection) : TrajectoryRolloutTermination

	data class Rejected(
		val frame: Int,
		val failure: SimulationEnvironmentException,
	) : TrajectoryRolloutTermination
}

data class TrajectoryRollout(
	val initialState: MovementSimulationState,
	val frames: List<SimulatedTrajectoryFrame>,
	val termination: TrajectoryRolloutTermination,
) {
	val finalState: MovementSimulationState get() = frames.lastOrNull()?.state ?: initialState
	val completed: Boolean get() = termination === TrajectoryRolloutTermination.Completed
}

object TrajectoryRolloutEngine {
	fun rollout(
		initialState: MovementSimulationState,
		profile: PlayerPhysicsProfile,
		environment: SimulationEnvironment,
		program: ControlProgram,
		frameCount: Int,
		siblings: List<List<SimulatedTrajectoryFrame>> = emptyList(),
		observer: ((SimulatedTrajectoryFrame) -> Boolean)? = null,
	): TrajectoryRollout {
		require(frameCount >= 0) { "frameCount must be non-negative" }

		var simulator: MovementSimulator? = null
		var shared = siblings
		val frames = ArrayList<SimulatedTrajectoryFrame>(frameCount)
		var current = initialState

		for (frame in 0 until frameCount) {
			var input = program.input(frame, current)
			if (shared.isNotEmpty()) {
				shared = shared.filter { sibling -> frame < sibling.size && physicallyEqual(sibling[frame].input, input) }
				val match = shared.firstOrNull()?.get(frame)
				if (match != null) {
					val reused = if (match.input == input) match
					else {
						val turned = profile.turn(current.rotation, input.rotation)
						SimulatedTrajectoryFrame(frame, input, match.state.copy(rotation = turned, turn = profile.turnBetween(current.rotation, turned)))
					}
					current = reused.state
					frames += reused
					if (observer?.invoke(reused) == true) break
					continue
				}
			}
			val next = try {
				val active = simulator ?: MovementSimulator(profile, environment, current).also { simulator = it }
				active.tickMovement(input).let { moved ->
					if (!active.burningFeet && !active.burnsNextTick(input)) return@let moved
					val retry = MovementSimulator(profile, environment, current).also { simulator = it }
					input = input.copy(sneak = true)
					retry.tickMovement(input)
				}
			} catch (failure: SimulationEnvironmentException) {
				return TrajectoryRollout(
					initialState = initialState,
					frames = frames,
					termination = when (failure) {
						is SnapshotSectionUnavailableException -> TrajectoryRolloutTermination.Blocked(
							frame, PathingSection(failure.sectionX, failure.sectionY, failure.sectionZ),
						)
						else -> TrajectoryRolloutTermination.Rejected(frame, failure)
					},
				)
			}
			current = next
			val simulated = SimulatedTrajectoryFrame(index = frame, input = input, state = next)
			frames += simulated
			if (observer?.invoke(simulated) == true) break
		}

		return TrajectoryRollout(
			initialState = initialState,
			frames = frames,
			termination = TrajectoryRolloutTermination.Completed,
		)
	}

	private fun physicallyEqual(a: MovementSimulationInput, b: MovementSimulationInput): Boolean {
		if (a.forward.toRawBits() != b.forward.toRawBits() || a.strafe.toRawBits() != b.strafe.toRawBits()) return false
		if (a.jump != b.jump || a.sneak != b.sneak || a.sprint != b.sprint || a.useItemSlowdown != b.useItemSlowdown) return false
		val first = a.rotation
		val second = b.rotation
		if (first == null || second == null) return first == null && second == null
		return first.yawF.toRawBits() == second.yawF.toRawBits() && first.pitchF.toRawBits() == second.pitchF.toRawBits()
	}
}
