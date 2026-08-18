/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.SimulationSnapshotOutOfBoundsException
import kotlin.math.abs
import kotlin.math.hypot

internal class Evaluation(val stopFrame: Int?, val diagnostic: TrajectoryDiagnostic?)

/** What the safety/arrival gates say about one simulated frame. */
internal sealed interface RolloutVerdict {
    /** Nothing decided yet; keep simulating. */
    data object Continue : RolloutVerdict

    /** A stable grounded stop at the goal was observed on [frame]. */
    data class Stopped(val frame: Int) : RolloutVerdict

    /** A hard gate rejected this frame; every later frame is moot. */
    data class Failed(val diagnostic: TrajectoryDiagnostic) : RolloutVerdict
}

/**
 * The safety and arrival gates, applied one frame at a time.
 *
 * Incremental on purpose. The whole-route sweep consumes a rollout that already ran to
 * its frame budget, but the anchor search must be able to *stop simulating* the instant
 * a transition ends — that is the difference between work that scales with hazards and
 * work that scales with route length. Both feed the same object, so a gate can never
 * hold in one search and not the other.
 */
internal class RolloutEvaluator(
    initialState: MovementSimulationState,
    private val nodes: List<HorizontalPoint>,
    private val goal: HorizontalPoint,
    private val config: WalkingSeedSearchConfig,
) {
    private val floor = nodes.minOf { it.y } - FALL_TOLERANCE

    // Vanilla's fall distance is the drop from the apex of the current airborne
    // arc, and it resets on every landing -- so a chain of short walk-offs is
    // safe while one long one is not.
    private var apex = initialState.position.y
    private var stable = 0

    /**
     * A head bonk is remembered but not fatal: an arc can graze a ceiling and still
     * land where it meant to. It is only *reported* if the walk never reaches a
     * stable stop.
     */
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

        // A head bonk is a rising body meeting a ceiling; it kills the arc family,
        // so it must be told apart from an ordinary landing.
        if (pendingBlocker == null && state.verticalCollision && before.velocity.y > 0.0 && !state.onGround) {
            pendingBlocker = TrajectoryDiagnostic.HeadBonk(index, state.position)
        }

        // Whether a wall matters depends on whether the body was *walking* into it.
        //
        // Grounded: the ground path is blocked. Fatal -- and it is exactly the
        // signal that a launch might be needed, so it seeds jump discovery.
        //
        // Airborne: a rising jump scrapes the very lip it is clearing. Vanilla
        // slides along it and the arc completes. Vetoing that would make every
        // rising jump uncertifiable, which is how the step-up route broke the
        // moment the coarse layer started preferring a rising jump over a step.
        if (state.horizontalCollision && state.onGround) {
            return RolloutVerdict.Failed(TrajectoryDiagnostic.HorizontalCollision(index, state.position))
        }
        if (state.position.y < floor) {
            return RolloutVerdict.Failed(TrajectoryDiagnostic.FellBelowRoute(index, floor - state.position.y))
        }
        val deviation = horizontalDistanceToPolyline(state.position.x, state.position.z, nodes)
        if (deviation > config.maxCorridorDeviation) {
            return RolloutVerdict.Failed(TrajectoryDiagnostic.LeftCorridor(index, deviation))
        }

        val atGoal = hypot(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
            abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
        val stopped = state.velocity.horizontalLength() <= config.stoppedSpeed
        stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
        if (stable >= config.stableStopFrames) return RolloutVerdict.Stopped(index)

        return RolloutVerdict.Continue
    }
}

/**
 * Walks the rollout once and reports the **first** thing that went wrong, so the
 * diagnostic names the earliest cause rather than a downstream symptom.
 */
internal fun evaluate(
    rollout: TrajectoryRollout,
    nodes: List<HorizontalPoint>,
    goal: HorizontalPoint,
    config: WalkingSeedSearchConfig,
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
                // Terrain we never captured is our bug, not the world's; it must not
                // be counted alongside genuine lava/ladder refusals.
                is SimulationSnapshotOutOfBoundsException ->
                    TrajectoryDiagnostic.OutsideSnapshot(rejected.frame, failure.pos)

                else ->
                    TrajectoryDiagnostic.UnsupportedPhysics(rejected.frame, failure.message ?: "unsupported")
            },
        )
    }

    // Never got there. If something blocked it on the way, that is the actionable
    // cause; a bare "did not stop" is only the truth when nothing was in the way.
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

/** Below the lowest route node by this much means the body left the route downward. */
private const val FALL_TOLERANCE = 0.6
