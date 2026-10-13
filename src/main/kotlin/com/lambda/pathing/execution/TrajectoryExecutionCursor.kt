package com.lambda.pathing.execution

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.world.streaming.WorldMutation
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

sealed interface ExecutionDeviation {
	data class WorldChanged(val snapshotRevision: Long, val mutation: WorldMutation) : ExecutionDeviation
	data class Position(val axis: String, val expected: Double, val actual: Double) : ExecutionDeviation
	data class Velocity(val axis: String, val expected: Double, val actual: Double) : ExecutionDeviation
	data class Rotation(val component: String, val expected: Double, val actual: Double) : ExecutionDeviation
	data class Flag(val name: String, val expected: Boolean, val actual: Boolean) : ExecutionDeviation
	data class IntegerValue(val name: String, val expected: Int, val actual: Int) : ExecutionDeviation
	data class BlockPosition(val name: String, val expected: BlockPos, val actual: BlockPos) : ExecutionDeviation
	data class SupportingBlock(val expected: BlockPos?, val actual: BlockPos?) : ExecutionDeviation
	data class PhysicsProfile(
		val expected: PlayerPhysicsProfile,
		val actual: PlayerPhysicsProfile,
	) : ExecutionDeviation

	data class Protocol(val message: String) : ExecutionDeviation
}

sealed interface ExecutionInputResult {
	data class Apply(val frame: Int, val input: MovementSimulationInput) : ExecutionInputResult
	data object Complete : ExecutionInputResult
	data class Rejected(val frame: Int, val deviation: ExecutionDeviation) : ExecutionInputResult
}

sealed interface ExecutionObservationResult {
	data object Accepted : ExecutionObservationResult
	data object Complete : ExecutionObservationResult
	data class Rejected(val frame: Int, val deviation: ExecutionDeviation) : ExecutionObservationResult
}

class TrajectoryExecutionCursor(
	val plan: TrajectoryPlan,
	startFrame: Int = 0,
) {
	var nextFrame: Int = startFrame
		private set

	private val poseHeightDeltas = with(plan.physicsProfile) {
		doubleArrayOf(height - crouchHeight, height - swimmingHeight, crouchHeight - swimmingHeight)
	}

	var awaitingObservation = false
		private set
	private var poseLagFrames = 0
	private var rejection: ExecutionDeviation? = null

	init {
		require(startFrame in 0..plan.tape.frameCount) { "Cannot resume outside the tape" }
	}

	fun nextInput(observed: MovementSimulationState): ExecutionInputResult {
		rejection?.let { return ExecutionInputResult.Rejected(nextFrame, it) }
		if (awaitingObservation) return rejectInput(ExecutionDeviation.Protocol("Previous input has not been observed"))

		val expectedBefore = if (nextFrame == 0) plan.initialState else plan.frames[nextFrame - 1].state
		stateDeviation(expectedBefore, observed)?.let { return rejectInput(it) }
		if (nextFrame >= plan.tape.frameCount) return ExecutionInputResult.Complete

		val input = plan.tape[nextFrame]
		if (!sprintTransitionConverges(input, expectedBefore)) {
			flagDeviation("sprinting", expectedBefore.isSprinting, observed.isSprinting)
				?.let { return rejectInput(it) }
		}

		awaitingObservation = true
		return ExecutionInputResult.Apply(nextFrame, input)
	}

	fun observeAfterTick(observed: MovementSimulationState): ExecutionObservationResult {
		rejection?.let { return ExecutionObservationResult.Rejected(nextFrame, it) }
		if (!awaitingObservation) return rejectObservation(ExecutionDeviation.Protocol("No input is awaiting observation"))

		stateDeviation(plan.frames[nextFrame].state, observed)
			?.let { return rejectObservation(it) }
		nextFrame++
		awaitingObservation = false
		return if (nextFrame == plan.tape.frameCount) ExecutionObservationResult.Complete
		else ExecutionObservationResult.Accepted
	}

	private fun stateDeviation(
		expected: MovementSimulationState,
		actual: MovementSimulationState,
	): ExecutionDeviation? {
		val positionTolerance = POSITION_TOLERANCE + POSITION_TOLERANCE_PER_FRAME * nextFrame
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

		val boxHeight = componentDeviation("box.maxY", expected.boundingBox.maxY, actual.boundingBox.maxY, positionTolerance)
		if (boxHeight != null) {
			if (!isPoseHeightLag(expected, actual) || poseLagFrames >= MAX_POSE_LAG_FRAMES) return boxHeight
			poseLagFrames++
		} else {
			poseLagFrames = 0
		}
		componentDeviation("box.maxZ", expected.boundingBox.maxZ, actual.boundingBox.maxZ, positionTolerance)?.let { return it }

		if (abs(Rotation.wrap(expected.rotation.yaw - actual.rotation.yaw)) > ROTATION_TOLERANCE_DEGREES) {
			return ExecutionDeviation.Rotation("yaw", expected.rotation.yaw, actual.rotation.yaw)
		}

		flagDeviation("onGround", expected.onGround, actual.onGround)?.let { return it }
		flagDeviation("horizontalCollision", expected.horizontalCollision, actual.horizontalCollision)?.let { return it }

		flagDeviation("verticalCollision", expected.verticalCollision, actual.verticalCollision)?.let { return it }
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

	private fun sprintTransitionConverges(
		input: MovementSimulationInput,
		state: MovementSimulationState,
	): Boolean {
		val hasForwardMovement = input.forward > Vanilla.FORWARD_INPUT_EPSILON
		val forcedStop = !hasForwardMovement || state.horizontalCollision && !state.collidedSoftly
		val forcedStart = input.sprint && hasForwardMovement && !input.sneak
		return forcedStop || forcedStart
	}

	private fun componentDeviation(axis: String, expected: Double, actual: Double, allowed: Double): ExecutionDeviation? =
		if (abs(expected - actual) <= allowed) null else ExecutionDeviation.Position(axis, expected, actual)

	private fun velocityDeviation(axis: String, expected: Double, actual: Double): ExecutionDeviation? =
		if (abs(expected - actual) <= VELOCITY_TOLERANCE) null else ExecutionDeviation.Velocity(axis, expected, actual)

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

	private fun isPoseHeightLag(
		expected: MovementSimulationState,
		actual: MovementSimulationState,
	): Boolean {
		if (expected.isSneaking != actual.isSneaking) return false
		val expectedHeight = expected.boundingBox.maxY - expected.boundingBox.minY
		val actualHeight = actual.boundingBox.maxY - actual.boundingBox.minY
		val delta = abs(expectedHeight - actualHeight)
		return poseHeightDeltas.any { abs(delta - it) < POSE_HEIGHT_TOLERANCE }
	}

	private companion object {
		const val POSITION_TOLERANCE = 2e-6
		const val POSITION_TOLERANCE_PER_FRAME = 8e-8
		const val VELOCITY_TOLERANCE = 1e-5
		const val ROTATION_TOLERANCE_DEGREES = 1e-3
		const val POSE_HEIGHT_TOLERANCE = 1e-6
		const val MAX_POSE_LAG_FRAMES = 3
	}
}
