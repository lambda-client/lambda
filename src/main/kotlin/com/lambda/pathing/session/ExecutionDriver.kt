package com.lambda.pathing.session

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.pathing.debug.executionRejectionReport
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.MAX_SESSION_RESTARTS
import com.lambda.pathing.session.PathingSession.Companion.MAX_STATE_RECOVERIES
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info

/**
 * Replays the running tape: one input per tick, one observation after it, holds at a
 * drained partial, recovery when the certified environment or the body's state diverges,
 * and the arrival. Installing and adopting tapes is [TapeAdmission]'s.
 */
internal class ExecutionDriver(private val walk: PathingSession) {
    private val admission get() = walk.admission

    fun SafeContext.tickExecution() {
        val active = walk.cursor ?: return
        val path = walk.telemetry.published ?: return

        walk.planningSession?.updateExecutionFrame(active.nextFrame)

        val frame = active.nextFrame

        if (walk.awaitingObservation && !finishObservedTick(active, path, frame)) return

        walk.pendingImprovement?.let { improvement ->
            walk.pendingImprovement = null
            with(admission) { adopt(improvement) }
        }

        // The remaining tape is a stationary terminal tail: the body is already at the
        // terminal position and every remaining input is passive. A full tape may
        // complete early; a partial one executes into its closed-cycle terminal so a
        // held body repeats the certified frames exactly.
        walk.cursor?.let { cursor ->
            val running = walk.telemetry.published
            if (running != null && !running.partial && !walk.awaitingObservation &&
                cursor.nextFrame >= running.plan.stationaryFrom &&
                cursor.nextFrame < running.plan.tape.frameCount
            ) {
                finishTrajectory(running)
                return
            }
        }

        // Holding at a drained partial: keep the hold discipline until an extension is
        // adopted (which clears the flag) or the session dies (restart from rest).
        if (walk.holding) {
            walk.telemetry.published?.let { enterHold(it) }
            return
        }

        val current = walk.telemetry.published ?: return
        val running = walk.cursor ?: return

        if (running.nextFrame == 0) {
            if (with(walk.alignment) { realignBeforeLaunch(current) }) return
        }

        val observed = observe(current.plan, running.nextFrame)
        with(admission) { executionEnvironmentDeviation(current, nextFrame = running.nextFrame) }?.let { deviation ->
            return reject(running.nextFrame, deviation, observed, afterInput = false)
        }
        when (val next = running.nextInput(observed)) {
            is ExecutionInputResult.Apply -> {
                walk.tickInput = next.input
                walk.awaitingObservation = true
                walk.state = State.Executing(next.frame, current.plan.tape.frameCount, walk.leg)

                next.input.rotation?.let { rotation ->
                    walk.request.runSafeAutomated { rotationRequest { yaw(rotation.yaw) }.submit() }
                }
            }

            ExecutionInputResult.Complete -> finishTrajectory(current)

            is ExecutionInputResult.Rejected -> reject(
                next.frame, next.deviation, observed, afterInput = false,
            )
        }
    }

    private fun SafeContext.finishObservedTick(
        active: TrajectoryExecutionCursor,
        path: PublishedPath,
        frame: Int,
    ): Boolean {
        val observed = observe(path.plan, frame + 1)
        with(admission) { executionEnvironmentDeviation(path, nextFrame = frame) }?.let { deviation ->
            reject(frame, deviation, observed, afterInput = true)
            return false
        }
        val result = active.observeAfterTick(observed)
        if (result is ExecutionObservationResult.Rejected) {
            reject(result.frame, result.deviation, observed, afterInput = true)
            return false
        }
        walk.awaitingObservation = false

        if (frame < path.plan.frames.size) {
            val predicted = path.plan.frames[frame].state.position
            walk.telemetry.recordTrail(player.pos, player.pos.distanceTo(predicted))
        }

        if (result === ExecutionObservationResult.Complete) {
            finishTrajectory(path)
            return false
        }
        return true
    }

    private fun SafeContext.finishTrajectory(path: PublishedPath) {
        if (path.partial) {
            enterHold(path)
            return
        }
        walk.cursor = null
        walk.planningSession?.updateExecutionFrame(null)
        walk.tickInput = null
        walk.awaitingObservation = false
        walk.cancelPlanning()
        walk.cancelSuccessor()

        walk.state = State.Complete(path.plan.tape.frameCount, walk.leg)
        walk.finish()
        val telemetry = walk.telemetry
        info(
            "Reached ${path.finalGoal}: ${path.plan.tape.frameCount} frames, " +
                "${path.publicationSequence.coerceAtLeast(1)} publication(s), " +
                "${telemetry.adopted} adoption(s), ${walk.holds} hold(s)" +
                (if (telemetry.recoveries > 0) ", recovered ${telemetry.recoveries} time(s)" else "") +
                (if (telemetry.rejectedImprovements > 0) ", rejected ${telemetry.rejectedImprovements}" else "") +
                "; max replay deviation %.2e".format(telemetry.maxDeviation),
            PATHING_SOURCE,
        )
        LOG.info(
            "Pathing tape profile: {} frames vs {} bound = {} optimal; {} standing still " +
                "({}%) in {} stop(s); {}",
            path.plan.tape.frameCount, "%.0f".format(path.route.lowerBoundTicks),
            "%.2fx".format(path.excessRatio), path.standingFrames(), path.standingPercent(),
            path.standingRuns(), walk.publicationCadence(),
        )
        LOG.info("Pathing frames by movement: {}", path.movementProfile())
        walk.continueRoute()
    }

    /**
     * The published tape drained before an extension certified. The body is settled at
     * the tape's closed-cycle terminal; the same session keeps searching from the
     * trajectory frontier, and the next adopted extension resumes from this frame. If
     * the session died, restart one from rest -- without tearing the walk down.
     */
    private fun SafeContext.enterHold(path: PublishedPath) {
        val cursor = walk.cursor ?: return
        if (!walk.holding) {
            walk.holding = true
            walk.holds++
            info(
                "Holding at the tape terminal (${path.plan.tape.frameCount} frames) " +
                    "while the search continues toward ${path.finalGoal}.",
                PATHING_SOURCE,
            )
        }
        walk.tickInput = ALIGNMENT_INPUT
        walk.awaitingObservation = false
        walk.planningSession?.updateExecutionFrame(cursor.nextFrame)
        walk.request.runSafeAutomated {
            rotationRequest { yaw(path.plan.frames.last().state.rotation.yaw) }.submit()
        }
        walk.state = State.Executing(cursor.nextFrame, path.plan.tape.frameCount, walk.leg)

        if (walk.planningSession == null) {
            walk.holding = false
            val reason = walk.sessionFailure
            walk.sessionFailure = null
            if (reason != null && walk.sessionRestarts >= MAX_SESSION_RESTARTS) {
                return with(walk) { fail("planning kept dead-ending: $reason") }
            }
            if (reason != null) walk.sessionRestarts++
            walk.cursor = null
            walk.tickInput = null
            // A successor planned while the body was still replaying is ready to install
            // right now; falling through to planTrajectory would re-plan from scratch and
            // hold for as long as that takes.
            val successor = walk.successorPath
            walk.successorPath = null
            walk.successorSession?.cancel()
            walk.successorSession = null
            if (successor != null) {
                LOG.info("Installing the successor tape planned during replay")
                return with(admission) { begin(successor) }
            }
            with(walk) { planTrajectory() }
        }
    }

    fun SafeContext.reject(
        frame: Int,
        deviation: ExecutionDeviation,
        observed: MovementSimulationState,
        afterInput: Boolean,
    ) {
        val report = executionRejectionReport(walk.telemetry.published, frame, deviation, observed, afterInput)
        with(walk) {
            when (deviation) {
                is ExecutionDeviation.WorldChanged -> recover(report, reuseCoarseState = true)
                is ExecutionDeviation.PhysicsProfile -> recover(report, reuseCoarseState = false)

                else ->
                    if (telemetry.recoveries < MAX_STATE_RECOVERIES) recover(report, reuseCoarseState = true)
                    else fail(report)
            }
        }
    }

    fun SafeContext.observe(plan: TrajectoryPlan, frame: Int) =
        MovementSimulationState.from(player, isJumping = frame > 0 && frame <= plan.tape.frameCount && plan.tape[frame - 1].jump)
}
