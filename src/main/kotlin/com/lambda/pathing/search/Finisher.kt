package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.control.CorridorFollowerProgram
import com.lambda.pathing.actions.control.TerminalApproach
import com.lambda.pathing.actions.control.brakeToStop
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.VERTICAL_GOAL_TOLERANCE
import com.lambda.pathing.rollout.evaluateDiagnostic
import com.lambda.pathing.world.center
import com.lambda.pathing.world.climbableAt
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.abs
import kotlin.math.hypot

internal const val FINISH_VALUE_TICKS = 11.0

internal class Finisher(
	private val field: ValueField,
	private val environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints,
	private val gate: RolloutGate,
	private val attempts: AttemptAccumulator,
	private val goalPoint: () -> HorizontalPoint,
) {
	private var provenApproach: TerminalApproach? = null

	fun finishFrom(anchor: ValueAnchor): Solution? {
		val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
		if (!field.reachesGoal(chain)) return null
		val points = chain.map { it.center(environment) }
		val leads: List<Double?> = if (chain.asSequence().zipWithNext().any { (from, to) -> to.y > from.y }) {
			config.stepUpJumpLeadDistances
		} else {
			listOf(null)
		}
		val grid = sequence {
			provenApproach?.let { yield(it) }
			for (sprint in config.sprintModes) {
				for (brake in config.brakeDistances) {
					for (lead in leads) yield(TerminalApproach(sprint, brake, lead))
				}
			}
		}

		var best: Candidate? = null
		for (approach in grid) {
			val frames = terminalRun(anchor, chain, points, approach) ?: continue
			val candidate = Candidate(
				approach, frames,
				collisions = anchor.collisionEvents + collisionEvents(anchor.state, frames),
				switches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
			)
			if (approach == provenApproach) return Solution(anchor, frames, candidate.collisions)
			if (best == null || candidate.beats(best)) best = candidate
		}
		val chosen = best ?: return null
		provenApproach = chosen.approach
		return Solution(anchor, chosen.frames, chosen.collisions)
	}

	fun brakeNearGoal(anchor: ValueAnchor, radius: Double): Solution? {
		val solution = brakeToStop(anchor) ?: return null
		val resting = solution.tailFrames.last().state
		val goal = goalPoint()
		if (hypot(resting.position.x - goal.x, resting.position.z - goal.z) > radius) return null
		if (abs(resting.position.y - goal.y) > VERTICAL_GOAL_TOLERANCE) return null
		return solution
	}

	private class Candidate(
		val approach: TerminalApproach,
		val frames: List<SimulatedTrajectoryFrame>,
		val collisions: Int,
		val switches: Int,
	) {
		fun beats(other: Candidate): Boolean = when {
			frames.size != other.frames.size -> frames.size < other.frames.size
			collisions != other.collisions -> collisions < other.collisions
			else -> switches < other.switches
		}
	}

	fun brakeToStop(anchor: ValueAnchor): Solution? {
		val gated = gate.run(
			anchor.state, listOf(anchor.stance.center(environment)),
			brakeToStop(anchor.state.rotation.yaw),
			BRAKE_TAIL_FRAMES,
		)
		if (gated.failed) return null
		var stable = 0
		var stableEnd = -1
		var previous = anchor.state
		var twoBack: MovementSimulationState? = null
		for ((index, _, state) in gated.rollout.frames) {
			val cycleClosed = twoBack.let {
				it != null && state.position == it.position &&
						state.velocity == it.velocity && state.onGround == it.onGround
			}
			stable = if (cycleClosed && state.velocity.horizontalLength() <= config.stoppedSpeed) stable + 1 else 0
			twoBack = previous
			previous = state
			if (stable >= config.stableStopFrames && state.onGround) {
				stableEnd = index
				break
			}
		}
		if (stableEnd < 0) return null
		val frames = gated.rollout.frames.take(stableEnd + 1)
		val resting = stanceOf(frames.last().state)
		if (!field.isStance(resting) || !field.isMapped(resting)) return null
		return Solution(anchor, frames, anchor.collisionEvents + collisionEvents(anchor.state, frames))
	}

	private fun terminalRun(
		anchor: ValueAnchor,
		chain: List<Stance>,
		points: List<HorizontalPoint>,
		approach: TerminalApproach,
	): List<SimulatedTrajectoryFrame>? {
		val gated = gate.run(anchor.state, points, CorridorFollowerProgram(chain, approach, config), config.maxFrames)
		val goal = goalPoint()
		val evaluation = evaluateDiagnostic(gated.rollout, points, goal, config, climbing = { field.view.climbableAt(it) })
		attempts.record(gated.rollout, gated.stopFrame != null, goal, evaluation)
		return gated.rollout.frames.takeIf { gated.stopFrame != null && !gated.failed }
	}

	private companion object {
		const val FINISH_CHAIN_LENGTH = 24

		const val BRAKE_TAIL_FRAMES = 64
	}
}
