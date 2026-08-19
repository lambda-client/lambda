/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.execution

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

data class ExecutionStateTolerance(
    /** Base simulator-vs-vanilla position allowance at frame zero. */
    val position: Double = 2e-6,
    /** Measured float-rounding accumulation per continuously replayed frame. */
    val positionPerFrame: Double = 8e-8,
    val velocity: Double = 1e-5,
    val rotationDegrees: Double = 1e-3,
) {
    init {
        require(position >= 0.0 && position.isFinite())
        require(positionPerFrame >= 0.0 && positionPerFrame.isFinite())
        require(velocity >= 0.0 && velocity.isFinite())
        require(rotationDegrees >= 0.0 && rotationDegrees.isFinite())
    }
}

sealed interface ExecutionDeviation {
    data class WorldRevision(val expected: Long, val actual: Long) : ExecutionDeviation
    data class Position(val axis: String, val expected: Double, val actual: Double) : ExecutionDeviation
    data class Velocity(val axis: String, val expected: Double, val actual: Double) : ExecutionDeviation
    data class Rotation(val component: String, val expected: Double, val actual: Double) : ExecutionDeviation
    data class Flag(val name: String, val expected: Boolean, val actual: Boolean) : ExecutionDeviation
    data class IntegerValue(val name: String, val expected: Int, val actual: Int) : ExecutionDeviation
    data class BlockPosition(val name: String, val expected: BlockPos, val actual: BlockPos) : ExecutionDeviation
    data class SupportingBlock(val expected: BlockPos?, val actual: BlockPos?) : ExecutionDeviation
    data object PhysicsProfile : ExecutionDeviation
    data class Protocol(val message: String) : ExecutionDeviation
}

sealed interface ExecutionInputResult {
    data class Apply(val frame: Int, val input: MovementSimulationInput) : ExecutionInputResult
    data object Complete : ExecutionInputResult
    data class Rejected(val frame: Int, val deviation: ExecutionDeviation) : ExecutionInputResult
}

sealed interface ExecutionObservationResult {
    data class Accepted(val frame: Int) : ExecutionObservationResult
    data object Complete : ExecutionObservationResult
    data class Rejected(val frame: Int, val deviation: ExecutionDeviation) : ExecutionObservationResult
}

/**
 * Pure replay/monitor state machine. It cannot steer, classify edges, replan,
 * or alter an input. Any mismatch rejects the plan and becomes sticky.
 */
class TrajectoryExecutionCursor(
    val plan: TrajectoryPlan,
    activePhysicsProfile: PlayerPhysicsProfile,
    private val tolerance: ExecutionStateTolerance = ExecutionStateTolerance(),
) {
    var nextFrame: Int = 0
        private set

    private var awaitingObservation = false
    private var rejection: ExecutionDeviation? =
        ExecutionDeviation.PhysicsProfile.takeIf { activePhysicsProfile != plan.physicsProfile }

    /**
     * Resumes a replacement plan that shares this one's executed prefix.
     *
     * Anytime refinement publishes a safe tape early and improves the rest while the body
     * is already walking, so the improved plan has to be adopted *mid-replay*. That is only
     * sound when the new tape's frames up to here are the ones already executed — the
     * caller proves that — and then the body's real state is exactly the state this frame
     * expects, because it is the same simulated history. The next comparison the cursor
     * makes will confirm it against the live player, so a mistaken splice fails closed on
     * the very next tick rather than walking a tape from the wrong place.
     */
    fun resumeAt(frame: Int) {
        require(frame in 0..plan.tape.frameCount) { "Cannot resume outside the tape" }
        check(!awaitingObservation) { "Cannot resume while an input is awaiting observation" }
        nextFrame = frame
    }

    fun nextInput(observed: MovementSimulationState, worldRevision: Long): ExecutionInputResult {
        rejection?.let { return ExecutionInputResult.Rejected(nextFrame, it) }
        if (awaitingObservation) return rejectInput(ExecutionDeviation.Protocol("Previous input has not been observed"))
        revisionDeviation(worldRevision)?.let { return rejectInput(it) }

        val expectedBefore = if (nextFrame == 0) plan.initialState else plan.frames[nextFrame - 1].state
        stateDeviation(expectedBefore, observed, compareSprinting = false)?.let { return rejectInput(it) }
        if (nextFrame >= plan.tape.frameCount) return ExecutionInputResult.Complete

        val input = plan.tape[nextFrame]
        if (!sprintTransitionConverges(input, expectedBefore)) {
            flagDeviation("sprinting", expectedBefore.isSprinting, observed.isSprinting)
                ?.let { return rejectInput(it) }
        }

        awaitingObservation = true
        return ExecutionInputResult.Apply(nextFrame, input)
    }

    fun observeAfterTick(observed: MovementSimulationState, worldRevision: Long): ExecutionObservationResult {
        rejection?.let { return ExecutionObservationResult.Rejected(nextFrame, it) }
        if (!awaitingObservation) return rejectObservation(ExecutionDeviation.Protocol("No input is awaiting observation"))
        revisionDeviation(worldRevision)?.let { return rejectObservation(it) }

        val observedFrame = nextFrame
        // The raw sprint data-tracker bit can briefly lag the locally applied key
        // while all movement-bearing state still agrees. Judge it immediately
        // before the next input instead: a held sprint key, a lost forward input,
        // or a hard collision deterministically makes both states converge before
        // the next physics step. Reject only when the next input cannot do that.
        stateDeviation(plan.frames[observedFrame].state, observed, compareSprinting = false)
            ?.let { return rejectObservation(it) }
        nextFrame++
        awaitingObservation = false
        return if (nextFrame == plan.tape.frameCount) ExecutionObservationResult.Complete
        else ExecutionObservationResult.Accepted(observedFrame)
    }

    private fun revisionDeviation(actual: Long): ExecutionDeviation? =
        if (actual == plan.snapshotRevision) null else ExecutionDeviation.WorldRevision(plan.snapshotRevision, actual)

    private fun stateDeviation(
        expected: MovementSimulationState,
        actual: MovementSimulationState,
        compareSprinting: Boolean = true,
    ): ExecutionDeviation? {
        val positionTolerance = tolerance.position + tolerance.positionPerFrame * nextFrame
        componentDeviation("x", expected.position.x, actual.position.x, positionTolerance)?.let { return it }
        componentDeviation("y", expected.position.y, actual.position.y, positionTolerance)?.let { return it }
        componentDeviation("z", expected.position.z, actual.position.z, positionTolerance)?.let { return it }
        velocityDeviation("x", expected.velocity.x, actual.velocity.x)?.let { return it }
        velocityDeviation("y", expected.velocity.y, actual.velocity.y)?.let { return it }
        velocityDeviation("z", expected.velocity.z, actual.velocity.z)?.let { return it }
        componentDeviation("box.minX", expected.boundingBox.minX, actual.boundingBox.minX, positionTolerance)?.let { return it }
        componentDeviation("box.minY", expected.boundingBox.minY, actual.boundingBox.minY, positionTolerance)?.let { return it }
        componentDeviation("box.minZ", expected.boundingBox.minZ, actual.boundingBox.minZ, positionTolerance)?.let { return it }
        componentDeviation("box.maxX", expected.boundingBox.maxX, actual.boundingBox.maxX, positionTolerance)?.let { return it }
        componentDeviation("box.maxY", expected.boundingBox.maxY, actual.boundingBox.maxY, positionTolerance)?.let { return it }
        componentDeviation("box.maxZ", expected.boundingBox.maxZ, actual.boundingBox.maxZ, positionTolerance)?.let { return it }
        // Yaw is the movement yaw, and it steers every input in the tape.
        if (abs(Rotation.wrap(expected.rotation.yaw - actual.rotation.yaw)) > tolerance.rotationDegrees) {
            return ExecutionDeviation.Rotation("yaw", expected.rotation.yaw, actual.rotation.yaw)
        }
        // Pitch is deliberately not compared: no ground or air movement equation
        // reads it, and the executor does not request it, so the head stays the
        // user's. Rejecting a walk because someone looked up would be theatre.
        flagDeviation("onGround", expected.onGround, actual.onGround)?.let { return it }
        flagDeviation("horizontalCollision", expected.horizontalCollision, actual.horizontalCollision)?.let { return it }
        // `collidedSoftly` is deliberately *not* a rejection. It has exactly one consumer
        // in vanilla — whether a glancing scrape lets a sprint survive — and that outcome
        // is already compared directly, as are the velocity and position it would change.
        // So the flag predicts something this cursor verifies anyway, and on a marginal
        // scrape the prediction can be wrong while every physical quantity still matches
        // to the last digit: a staircase riser struck mid-jump reported identical position,
        // velocity, yaw and `horizontalCollision`, with only this flag disagreeing. Failing
        // there aborts a walk that has not actually diverged. It stays in the rejection
        // report, where it names the cause of a real divergence one tick later.
        //
        // Debt: our port of `hasCollidedSoftly` and vanilla's disagree in that marginal
        // case. It wants a fixed-tape differential fixture; the disagreement is real and
        // this only stops it from being fatal on its own.
        flagDeviation("verticalCollision", expected.verticalCollision, actual.verticalCollision)?.let { return it }
        if (compareSprinting) {
            flagDeviation("sprinting", expected.isSprinting, actual.isSprinting)?.let { return it }
        }
        flagDeviation("jumping", expected.isJumping, actual.isJumping)?.let { return it }
        flagDeviation("sneaking", expected.isSneaking, actual.isSneaking)?.let { return it }
        if (expected.jumpingCooldown != actual.jumpingCooldown) {
            return ExecutionDeviation.IntegerValue("jumpingCooldown", expected.jumpingCooldown, actual.jumpingCooldown)
        }
        if (expected.velocityAffectingPos != actual.velocityAffectingPos) {
            return ExecutionDeviation.BlockPosition("velocityAffectingPos", expected.velocityAffectingPos, actual.velocityAffectingPos)
        }
        if (expected.supportingBlockPos != actual.supportingBlockPos) {
            return ExecutionDeviation.SupportingBlock(expected.supportingBlockPos, actual.supportingBlockPos)
        }
        return null
    }

    /** Whether applying [input] makes either raw sprint bit produce the same state. */
    private fun sprintTransitionConverges(
        input: MovementSimulationInput,
        state: MovementSimulationState,
    ): Boolean {
        val hasForwardMovement = input.forward > FORWARD_MOVEMENT_EPSILON
        val forcedStop = !hasForwardMovement || state.horizontalCollision && !state.collidedSoftly
        val forcedStart = input.sprint && hasForwardMovement && !input.sneak
        return forcedStop || forcedStart
    }

    private fun componentDeviation(axis: String, expected: Double, actual: Double, allowed: Double): ExecutionDeviation? =
        if (abs(expected - actual) <= allowed) null else ExecutionDeviation.Position(axis, expected, actual)

    private fun velocityDeviation(axis: String, expected: Double, actual: Double): ExecutionDeviation? =
        if (abs(expected - actual) <= tolerance.velocity) null else ExecutionDeviation.Velocity(axis, expected, actual)

    private fun flagDeviation(name: String, expected: Boolean, actual: Boolean): ExecutionDeviation? =
        if (expected == actual) null else ExecutionDeviation.Flag(name, expected, actual)

    private fun rejectInput(deviation: ExecutionDeviation): ExecutionInputResult.Rejected {
        rejection = deviation
        return ExecutionInputResult.Rejected(nextFrame, deviation)
    }

    private fun rejectObservation(deviation: ExecutionDeviation): ExecutionObservationResult.Rejected {
        rejection = deviation
        return ExecutionObservationResult.Rejected(nextFrame, deviation)
    }

    private companion object {
        const val FORWARD_MOVEMENT_EPSILON = 1.0E-5
    }
}
