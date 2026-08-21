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

/**
 * Runs one decision against the simulator and says what became of the body.
 *
 * The only thing in the search that touches physics. Every anchor in the tree is the
 * certified end of one of these; nothing else may create one.
 */
internal class AnchorRollout(
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val environment: SnapshotSimulationEnvironment,
    private val profile: PlayerPhysicsProfile,
    private val initialState: MovementSimulationState,
    private val goalPoint: HorizontalPoint,
    private val gateConfig: MotionConstraints,
    private val attempts: MutableList<PlanAttempt>,
    private val progressOf: (com.lambda.pathing.coarse.Stance) -> Int,
) {
    /**
     * One local transition, simulated once and stopped at its next event.
     *
     * The event is "the body is grounded and moving on a *different* stance than it
     * started on" — a physical fact about where the body is, not a projection onto a
     * planned node. A launch additionally has to actually leave the ground first.
     */
    fun transition(anchor: ValueAnchor, action: TrajectoryDecision, hazardFrame: Int?): Outcome {
        val chain = field.chain(
            anchor.stance, action.step, searchConfig.chainLength, anchor.heading(),
        )
        val points = chain.map { it.center() }
        val launch = when (action) {
            is TrajectoryDecision.Launch -> LaunchTrigger(action.delayFrames, action.brakeTicks)
            is TrajectoryDecision.Heading ->
                action.delayFrames?.let { LaunchTrigger(it, action.brakeTicks) }
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
                startProgress = 0,
                sprint = action.sprint,
                lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: ValueFieldAnchorSearch.LOOK_AHEAD_NODES,
                launch = launch,
                maxYawChange = config.maxYawDegreesPerFrame,
                easeTurns = (action as? TrajectoryDecision.Walk)?.easeTurns == true,
            )
        }
        val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, gateConfig)
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
                            // Ending on the value field instead of this counter was
                            // tried and measured worse: marginally better across the
                            // short corpus, six frames worse on a 444-frame route, and
                            // it cut the improver's attempts from 404 to 156 because
                            // each transition simulates further. A commitment is worth
                            // more than a well-timed exit.
                            action is TrajectoryDecision.Heading -> action.commitFrames
                                ?.let { frame.index + 1 >= it }
                                ?: (frame.index + 1 >= searchConfig.headingCommitFrames)
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
            evaluate(rollout, points, goalPoint, gateConfig).diagnostic
                ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
        )

        val frames = rollout.frames.take(frame + 1)
        // A landing on ground the coarse layer does not model as standable — or that
        // the value field cannot price — has no value and no usable successors.
        // Anchoring there strands the search on a state it can neither rank nor
        // continue, and ranking it by a straight-line guess is what sends the search
        // off in the wrong direction.
        if (!field.isStance(eventStance) || !field.isMapped(eventStance)) {
            return Outcome.Rejected(
                TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
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
                // A heading that ended on the field rather than on a counter records
                // the length it settled on, so the decision describes itself and a
                // re-run reproduces this transition exactly instead of re-deriving a
                // stall it cannot see from a different entry state.
                via = if (action is TrajectoryDecision.Heading && action.commitFrames == null &&
                    action.delayFrames == null
                ) {
                    action.copy(commitFrames = frames.size)
                } else {
                    action
                },
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
        attempts += PlanAttempt(
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
        )
    }
}
