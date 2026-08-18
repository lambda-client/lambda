/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationEnvironment
import com.lambda.util.player.prediction.SimulationEnvironmentException

data class SimulatedTrajectoryFrame(
    val index: Int,
    val input: MovementSimulationInput,
    val state: MovementSimulationState,
)

sealed interface TrajectoryRolloutTermination {
    data object Completed : TrajectoryRolloutTermination

    data class Rejected(
        val frame: Int,
        val failure: SimulationEnvironmentException,
    ) : TrajectoryRolloutTermination
}

/** Immutable result of running one control program against one environment. */
data class TrajectoryRollout(
    val initialState: MovementSimulationState,
    val frames: List<SimulatedTrajectoryFrame>,
    val termination: TrajectoryRolloutTermination,
) {
    val certifiedThrough: Int get() = frames.lastIndex
    val finalState: MovementSimulationState get() = frames.lastOrNull()?.state ?: initialState
    val completed: Boolean get() = termination === TrajectoryRolloutTermination.Completed
}

/**
 * Watches a rollout frame by frame and may end it early.
 *
 * The frame budget answers "how long may this run"; the observer answers "is there
 * anything left to learn". A transition between two motion anchors is decided within a
 * handful of ticks, and simulating past that decision is the super-linear work the
 * anchor search exists to remove.
 */
fun interface RolloutObserver {
    /** True stops the rollout with [frame] as its last recorded frame. */
    fun onFrame(frame: SimulatedTrajectoryFrame): Boolean
}

/** Stateless rollout entry point; safe on workers when [environment] is. */
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
                    termination = TrajectoryRolloutTermination.Rejected(frame, result.failure),
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
