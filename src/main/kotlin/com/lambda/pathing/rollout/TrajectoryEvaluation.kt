package com.lambda.pathing.rollout

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import kotlin.math.abs
import kotlin.math.hypot

internal sealed interface RolloutVerdict {
	data object Continue : RolloutVerdict

	data class Stopped(val frame: Int) : RolloutVerdict

	data class Failed(val diagnostic: TrajectoryDiagnostic) : RolloutVerdict
}

internal class RolloutEvaluator(
	initialState: MovementSimulationState,
	nodes: List<HorizontalPoint>,
	private val goal: HorizontalPoint,
	private val config: MotionConstraints,

	private val allowHorizontalContact: Boolean = false,

	descentAllowance: Double = 0.0,

	private val climbing: (net.minecraft.util.math.Vec3d) -> Boolean = { false },
) {
	private val floor = nodes.minOf { it.y } - FALL_TOLERANCE - descentAllowance

	private var apex = initialState.position.y
	private var stable = 0

	var pendingBlocker: TrajectoryDiagnostic? = null
		private set

	fun observe(index: Int, state: MovementSimulationState, before: MovementSimulationState): RolloutVerdict {
		if (climbing(state.position)) apex = state.position.y
		if (state.onGround) {

			val rebounded = state.velocity.y > 0.0
			val fallDistance = apex - state.position.y
			if (!rebounded && fallDistance > config.maxSafeFallDistance) {
				return RolloutVerdict.Failed(TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
			}

			apex = state.position.y
		} else {
			apex = maxOf(apex, state.position.y)
		}

		if (pendingBlocker == null && state.verticalCollision && before.velocity.y > 0.0 && !state.onGround) {
			pendingBlocker = TrajectoryDiagnostic.HeadBonk(index, state.position)
		}

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

internal fun evaluateDiagnostic(
	rollout: TrajectoryRollout,
	nodes: List<HorizontalPoint>,
	goal: HorizontalPoint,
	config: MotionConstraints,
	descentAllowance: Double = 0.0,
	climbing: (net.minecraft.util.math.Vec3d) -> Boolean = { false },
): TrajectoryDiagnostic? {
	val evaluator = RolloutEvaluator(
		rollout.initialState, nodes, goal, config, descentAllowance = descentAllowance,
		climbing = climbing,
	)

	rollout.frames.forEach { frame ->
		val before = if (frame.index == 0) rollout.initialState else rollout.frames[frame.index - 1].state
		when (val verdict = evaluator.observe(frame.index, frame.state, before)) {
			is RolloutVerdict.Continue -> Unit
			is RolloutVerdict.Stopped -> return null
			is RolloutVerdict.Failed -> return verdict.diagnostic
		}
	}

	when (val termination = rollout.termination) {
		is TrajectoryRolloutTermination.Blocked -> return TrajectoryDiagnostic.UnknownTerrain(
			termination.frame, termination.sectionX, termination.sectionY, termination.sectionZ,
		)
		is TrajectoryRolloutTermination.Rejected -> return when (val failure = termination.failure) {
			is SimulationSnapshotOutOfBoundsException ->
				TrajectoryDiagnostic.OutsideSnapshot(termination.frame, failure.pos)
			else -> TrajectoryDiagnostic.UnsupportedPhysics(termination.frame, failure.message ?: "unsupported")
		}
		TrajectoryRolloutTermination.Completed -> Unit
	}

	evaluator.pendingBlocker?.let { return it }

	val final = rollout.finalState
	return TrajectoryDiagnostic.NoStop(
		frame = rollout.frames.size,
		goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
		speed = final.velocity.horizontalLength(),
	)
}

private const val VERTICAL_GOAL_TOLERANCE = 0.05

private const val FALL_TOLERANCE = 0.6
