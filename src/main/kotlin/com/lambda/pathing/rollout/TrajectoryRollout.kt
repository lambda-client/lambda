package com.lambda.pathing.rollout

import com.lambda.pathing.actions.ControlProgram
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

	data class Blocked(
		val frame: Int,
		val sectionX: Int,
		val sectionY: Int,
		val sectionZ: Int,
	) : TrajectoryRolloutTermination

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
	val certifiedThrough: Int get() = frames.lastIndex
	val finalState: MovementSimulationState get() = frames.lastOrNull()?.state ?: initialState
	val completed: Boolean get() = termination === TrajectoryRolloutTermination.Completed
}

fun interface RolloutObserver {
	fun onFrame(frame: SimulatedTrajectoryFrame): Boolean
}

object TrajectoryRolloutEngine {
	fun rollout(
		initialState: MovementSimulationState,
		profile: PlayerPhysicsProfile,
		environment: SimulationEnvironment,
		program: ControlProgram,
		frameCount: Int,
		observer: RolloutObserver? = null,
	): TrajectoryRollout {
		require(frameCount >= 0) { "frameCount must be non-negative" }

		val simulator = MovementSimulator(
			profile = profile,
			environment = environment,
			initialState = initialState,
		)
		val frames = ArrayList<SimulatedTrajectoryFrame>(frameCount)
		var current = initialState

		for (frame in 0 until frameCount) {
			val input = program.input(frame, current)
			val next = simulator.stepFrom(current, input) ?: return TrajectoryRollout(
					initialState = initialState,
					frames = frames,
					termination = when (val failure = checkNotNull(simulator.lastFailure)) {
						is SnapshotSectionUnavailableException -> TrajectoryRolloutTermination.Blocked(
							frame, failure.sectionX, failure.sectionY, failure.sectionZ,
						)
						else -> TrajectoryRolloutTermination.Rejected(frame, failure)
					},
				)
			current = next
			val simulated = SimulatedTrajectoryFrame(index = frame, input = input, state = next)
			frames += simulated
			if (observer?.onFrame(simulated) == true) break
		}

		return TrajectoryRollout(
			initialState = initialState,
			frames = frames,
			termination = TrajectoryRolloutTermination.Completed,
		)
	}
}
