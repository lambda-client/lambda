package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulationStepResult
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.SimulationEnvironment
import com.lambda.pathing.prediction.SimulationEnvironmentException
import com.lambda.pathing.prediction.SnapshotSectionUnavailableException

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

        for (frame in 0 until frameCount) {
            val input = program.input(frame, simulator.state)
            when (val result = simulator.tryTickMovement(input)) {
                is MovementSimulationStepResult.Advanced -> {
                    val simulated = SimulatedTrajectoryFrame(
                        index = frame,
                        input = input,
                        state = result.tick.simulator.state,
                    )
                    frames += simulated
                    if (observer?.onFrame(simulated) == true) return TrajectoryRollout(
                        initialState = initialState,
                        frames = frames.toList(),
                        termination = TrajectoryRolloutTermination.Completed,
                    )
                }

                is MovementSimulationStepResult.Rejected -> return TrajectoryRollout(
                    initialState = initialState,
                    frames = frames.toList(),
                    termination = when (val failure = result.failure) {
                        is SnapshotSectionUnavailableException -> TrajectoryRolloutTermination.Blocked(
                            frame, failure.sectionX, failure.sectionY, failure.sectionZ,
                        )
                        else -> TrajectoryRolloutTermination.Rejected(frame, failure)
                    },
                )
            }
        }

        return TrajectoryRollout(
            initialState = initialState,
            frames = frames.toList(),
            termination = TrajectoryRolloutTermination.Completed,
        )
    }
}
