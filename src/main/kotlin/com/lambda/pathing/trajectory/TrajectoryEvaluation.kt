/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.HorizontalPoint
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.SimulationSnapshotOutOfBoundsException
import kotlin.math.abs
import kotlin.math.hypot

internal class Evaluation(val stopFrame: Int?, val diagnostic: TrajectoryDiagnostic?)

internal sealed interface RolloutVerdict {
    data object Continue : RolloutVerdict

    data class Stopped(val frame: Int) : RolloutVerdict

    data class Failed(val diagnostic: TrajectoryDiagnostic) : RolloutVerdict
}

internal class RolloutEvaluator(
    initialState: MovementSimulationState,
    private val nodes: List<HorizontalPoint>,
    private val goal: HorizontalPoint,
    private val config: MotionConstraints,
    /** Set for a movement whose motion comes from pressing into terrain -- see [Movement.pressesIntoTerrain]. */
    private val allowHorizontalContact: Boolean = false,
) {
    private val floor = nodes.minOf { it.y } - FALL_TOLERANCE

    private var apex = initialState.position.y
    private var stable = 0

    var pendingBlocker: TrajectoryDiagnostic? = null
        private set

    fun observe(index: Int, state: MovementSimulationState, before: MovementSimulationState): RolloutVerdict {
        if (state.onGround) {
            val fallDistance = apex - state.position.y
            if (fallDistance > config.maxSafeFallDistance) {
                return RolloutVerdict.Failed(TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
            }
            apex = state.position.y
        } else {
            apex = maxOf(apex, state.position.y)
        }

        if (pendingBlocker == null && state.verticalCollision && before.velocity.y > 0.0 && !state.onGround) {
            pendingBlocker = TrajectoryDiagnostic.HeadBonk(index, state.position)
        }

        // A sneaking body's horizontal collision is usually its own ledge clip rather than
        // a wall: vanilla zeroes the movement to keep it from stepping off an edge, and the
        // zeroed movement reads as a collision. That is the technique working, not the body
        // running into something, and failing it rejected every controlled descent on the
        // frame its brake first bit. A sneak into an actual wall is harmless anyway -- there
        // is no speed behind it to be a hazard.
        if (state.horizontalCollision && state.onGround && !allowHorizontalContact &&
            !state.isSneaking
        ) {
            return RolloutVerdict.Failed(TrajectoryDiagnostic.HorizontalCollision(index, state.position))
        }
        if (state.position.y < floor) {
            return RolloutVerdict.Failed(TrajectoryDiagnostic.FellBelowRoute(index, floor - state.position.y))
        }

        val atGoal = hypot(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
            abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
        val stopped = state.velocity.horizontalLength() <= config.stoppedSpeed
        stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
        if (stable >= config.stableStopFrames) return RolloutVerdict.Stopped(index)

        return RolloutVerdict.Continue
    }
}

internal fun evaluate(
    rollout: TrajectoryRollout,
    nodes: List<HorizontalPoint>,
    goal: HorizontalPoint,
    config: MotionConstraints,
): Evaluation {
    val evaluator = RolloutEvaluator(rollout.initialState, nodes, goal, config)

    rollout.frames.forEach { frame ->
        val before = if (frame.index == 0) rollout.initialState else rollout.frames[frame.index - 1].state
        when (val verdict = evaluator.observe(frame.index, frame.state, before)) {
            is RolloutVerdict.Continue -> Unit
            is RolloutVerdict.Stopped -> return Evaluation(verdict.frame, null)
            is RolloutVerdict.Failed -> return Evaluation(null, verdict.diagnostic)
        }
    }

    (rollout.termination as? TrajectoryRolloutTermination.Rejected)?.let { rejected ->
        return Evaluation(
            null,
            when (val failure = rejected.failure) {
                is SimulationSnapshotOutOfBoundsException ->
                    TrajectoryDiagnostic.OutsideSnapshot(rejected.frame, failure.pos)

                else ->
                    TrajectoryDiagnostic.UnsupportedPhysics(rejected.frame, failure.message ?: "unsupported")
            },
        )
    }

    evaluator.pendingBlocker?.let { return Evaluation(null, it) }

    val final = rollout.finalState
    return Evaluation(
        null,
        TrajectoryDiagnostic.NoStop(
            frame = rollout.frames.size,
            goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
            speed = final.velocity.horizontalLength(),
        ),
    )
}

private const val VERTICAL_GOAL_TOLERANCE = 0.05

private const val FALL_TOLERANCE = 0.6
