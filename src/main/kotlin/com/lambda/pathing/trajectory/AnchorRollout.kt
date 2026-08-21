/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import kotlin.math.hypot

internal sealed interface Outcome {
    data class Anchored(val anchor: ValueAnchor) : Outcome
    data class Arrived(val frames: List<SimulatedTrajectoryFrame>, val stopFrame: Int) : Outcome
    data class Rejected(val diagnostic: TrajectoryDiagnostic) : Outcome
}

internal class AnchorRollout(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val environment: SnapshotSimulationEnvironment,
    private val profile: PlayerPhysicsProfile,
    private val initialState: MovementSimulationState,
    private val goalPoint: HorizontalPoint,
    private val attempts: AttemptAccumulator,
    private val progressOf: (com.lambda.pathing.coarse.Stance) -> Int,
) {
    fun transition(anchor: ValueAnchor, action: TrajectoryDecision, hazardFrame: Int?): Outcome {
        val chain = field.chain(
            anchor.stance, action.step, searchConfig.chainLength, anchor.heading(),
        )
        val points = chain.map { it.center() }
        val launch = when (action) {
            is TrajectoryDecision.Launch -> LaunchTrigger(action.delayFrames)
            is TrajectoryDecision.Heading ->
                action.delayFrames?.let { LaunchTrigger(it) }
            is TrajectoryDecision.Walk -> null
        }
        val program = if (action is TrajectoryDecision.Heading) {
            HeadingFollowerProgram(
                targetYaw = action.yaw,
                sprint = action.sprint && action.keys.sustainsSprint,
                maxYawChange = config.maxYawDegreesPerFrame,
                launch = launch,
                keys = action.keys,
                airborneKeys = action.airborneKeys,
            )
        } else {
            SegmentFollowerProgram(
                nodes = points,
                sprint = action.sprint,
                lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: ValueFieldAnchorSearch.LOOK_AHEAD_NODES,
                launch = launch,
                maxYawChange = config.maxYawDegreesPerFrame,
                easeTurns = (action as? TrajectoryDecision.Walk)?.easeTurns == true,
            )
        }
        val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, config)
        var previous = anchor.state
        var airborne = false
        var failure: TrajectoryDiagnostic? = null
        var stopFrame: Int? = null
        var eventFrame: Int? = null
        var eventStance = anchor.stance

        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = anchor.state,
            profile = profile,
            environment = environment,
            program = program,
            frameCount = searchConfig.maxTransitionFrames,
        ) { frame ->
            val verdict = evaluator.observe(frame.index, frame.state, previous)
            previous = frame.state
            when (verdict) {
                is RolloutVerdict.Failed -> {
                    failure = verdict.diagnostic
                    true
                }

                is RolloutVerdict.Stopped -> {
                    stopFrame = verdict.frame
                    true
                }

                is RolloutVerdict.Continue -> {
                    if (!frame.state.onGround) {
                        airborne = true
                        false
                    } else {
                        val stance = ValueFieldAnchorSearch.stanceOf(frame.state)
                        val done = when {
                            launch != null -> launch.hasFired && airborne

                            action is TrajectoryDecision.Heading ->
                                frame.index + 1 >= searchConfig.headingCommitFrames
                            else -> stance != anchor.stance
                        }
                        val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed
                        if (done && moving && stance != anchor.stance) {
                            eventFrame = frame.index
                            eventStance = stance
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }

        record(anchor, action, rollout, failure, stopFrame != null)

        stopFrame?.let { return Outcome.Arrived(rollout.frames, it) }
        failure?.let { return Outcome.Rejected(it) }
        val frame = eventFrame ?: return Outcome.Rejected(
            evaluate(rollout, points, goalPoint, config).diagnostic
                ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
        )

        val frames = rollout.frames.take(frame + 1)

        if (!field.isStance(eventStance) || !field.isMapped(eventStance)) {
            return Outcome.Rejected(
                TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
            )
        }
        if (anchor.hasVisited(eventStance)) {
            return Outcome.Rejected(
                TrajectoryDiagnostic.RepeatedCoarseStance(frame)
            )
        }

        return Outcome.Anchored(
            ValueAnchor(
                state = frames.last().state,
                stance = eventStance,
                elapsed = anchor.elapsed + frames.size,
                collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                launchMargin = anchor.launchMargin + launchMargin(frames, hazardFrame),
                inputSwitches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
                parent = anchor,
                inputs = frames.map { it.input },
                boundary = anchor.elapsed + frames.size,
            ),
        )
    }

    private fun record(
        anchor: ValueAnchor,
        action: TrajectoryDecision,
        rollout: TrajectoryRollout,
        failure: TrajectoryDiagnostic?,
        stopped: Boolean,
    ) {
        PlanningDebugChannel.publishAttempt(rollout, stopped, failure)
        attempts.record(PlanAttempt(
            parameters = TerminalApproach(
                sprint = action.sprint,
                lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: ValueFieldAnchorSearch.LOOK_AHEAD_NODES,
                brakeDistance = config.brakeDistances.first(),
                stepUpJumpLeadDistance = null,
            ),
            simulatedFrames = rollout.frames.size,
            finalGoalError = hypot(
                rollout.finalState.position.x - goalPoint.x,
                rollout.finalState.position.z - goalPoint.z,
            ),
            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
            diagnostic = failure,
            blockedProgress = progressOf(anchor.stance),
        ))
    }
}
