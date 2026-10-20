package com.lambda.pathing.rollout

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.planarLength
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
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
	private val climbing: (Vec3d) -> Boolean,
	private val sliding: (MovementSimulationState) -> Boolean = { false },
	private val landingRule: (MovementSimulationState) -> FallRule = { FallRule.NORMAL },
	private val lava: (Box) -> Boolean = { false },
) {
	private val floor = nodes.minOf { it.y } - FALL_TOLERANCE - descentAllowance

	private val airFloor = if (initialState.air >= config.airReserve) config.airReserve else 0

	private var previous = initialState
	private var apex = initialState.position.y
	private var stable = 0

	var pendingBlocker: TrajectoryDiagnostic? = null
		private set

	fun observe(index: Int, state: MovementSimulationState): RolloutVerdict {
		val before = previous
		previous = state
		if (state.touchingWater || climbing(state.position) || sliding(state)) apex = state.position.y
		if (state.onGround) {
			val rebounded = state.velocity.y > 0.0
			val fallDistance = apex - state.position.y
			if (config.avoidLavaEdges && !before.onGround && landsOverLava(state, fallDistance)) {
				return RolloutVerdict.Failed(TrajectoryDiagnostic.UnsupportedPhysics(index, "landing over lava"))
			}
			if (!rebounded && fallDistance > config.maxSafeFallDistance && fallDistance > config.maxFallOnto(landingRule(state))) {
				return RolloutVerdict.Failed(TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
			}
			if (!rebounded && fallDistance > FallRule.TRAMPLE_FALL_DISTANCE && landingRule(state).tramples) {
				return RolloutVerdict.Failed(TrajectoryDiagnostic.HarmfulFall(index, fallDistance))
			}

			apex = state.position.y
		} else {
			apex = maxOf(apex, state.position.y)
		}

		if (state.eyeInWater && state.air < airFloor) return RolloutVerdict.Failed(TrajectoryDiagnostic.Drowning(index, state.air))

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

		val atGoal = planarLength(state.position.x - goal.x, state.position.z - goal.z) <= config.goalRadius &&
				abs(state.position.y - goal.y) <= VERTICAL_GOAL_TOLERANCE
		val stopped = state.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED
		stable = if (atGoal && stopped && state.onGround) stable + 1 else 0
		if (stable >= config.stableStopFrames) return RolloutVerdict.Stopped(index)

		return RolloutVerdict.Continue
	}

	private fun overLava(box: Box): Boolean = lava(Box(box.minX, box.minY - SUPPORT_PROBE_DEPTH, box.minZ, box.maxX, box.minY, box.maxZ))

	private fun landsOverLava(state: MovementSimulationState, fallDistance: Double): Boolean {
		val box = state.boundingBox
		if (fallDistance >= LAVA_LANDING_FALL_BLOCKS) return overLava(box)
		val x = state.position.x
		val z = state.position.z
		return lava(Box(x - CENTRE_PROBE_HALF_WIDTH, box.minY - SUPPORT_PROBE_DEPTH, z - CENTRE_PROBE_HALF_WIDTH, x + CENTRE_PROBE_HALF_WIDTH, box.minY, z + CENTRE_PROBE_HALF_WIDTH))
	}

	fun terminalDiagnostic(rollout: TrajectoryRollout): TrajectoryDiagnostic = when (val termination = rollout.termination) {
		is TrajectoryRolloutTermination.Blocked -> TrajectoryDiagnostic.UnknownTerrain(termination.frame, termination.section)
		is TrajectoryRolloutTermination.Rejected -> when (val failure = termination.failure) {
			is SimulationSnapshotOutOfBoundsException -> TrajectoryDiagnostic.OutsideSnapshot(termination.frame, failure.pos)
			else -> TrajectoryDiagnostic.UnsupportedPhysics(termination.frame, failure.message ?: "unsupported")
		}
		TrajectoryRolloutTermination.Completed -> pendingBlocker ?: rollout.finalState.let { final ->
			TrajectoryDiagnostic.NoStop(
				frame = rollout.frames.size,
				goalError = hypot(final.position.x - goal.x, final.position.z - goal.z),
				speed = final.velocity.horizontalLength(),
			)
		}
	}
}

internal const val VERTICAL_GOAL_TOLERANCE = 0.05

private const val FALL_TOLERANCE = 0.6

private const val SUPPORT_PROBE_DEPTH = 0.05

private const val LAVA_LANDING_FALL_BLOCKS = 0.5

private const val CENTRE_PROBE_HALF_WIDTH = 1.0E-4
