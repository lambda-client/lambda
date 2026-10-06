package com.lambda.pathing.search

import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.rollout.RolloutVerdict
import com.lambda.pathing.rollout.RolloutEvaluator

import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.climbableAt
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class GatedRollout(
	val rollout: TrajectoryRollout,
	val stopFrame: Int?,
	val failed: Boolean,
	val diagnostic: TrajectoryDiagnostic?,
)

internal class RolloutGate(
	private val profile: PlayerPhysicsProfile,
	private val environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints,
	private val route: RouteState,
) {
	fun run(
		from: MovementSimulationState,
		points: List<HorizontalPoint>,
		program: ControlProgram,
		frameCount: Int,
	): GatedRollout {
		val evaluator = RolloutEvaluator(from, points, route.goalPoint, config, climbing = { environment.climbableAt(it) })
		var stopFrame: Int? = null
		var failure: TrajectoryDiagnostic? = null
		val rollout = TrajectoryRolloutEngine.rollout(
			initialState = from,
			profile = profile,
			environment = environment,
			program = program,
			frameCount = frameCount,
		) { frame ->
			when (val verdict = evaluator.observe(frame.index, frame.state)) {
				is RolloutVerdict.Stopped -> {
					stopFrame = verdict.frame; true
				}
				is RolloutVerdict.Failed -> {
					failure = verdict.diagnostic; true
				}
				is RolloutVerdict.Continue -> false
			}
		}
		val diagnostic = if (stopFrame != null) null else failure ?: evaluator.terminalDiagnostic(rollout)
		return GatedRollout(rollout, stopFrame, failure != null, diagnostic)
	}
}
