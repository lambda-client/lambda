package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.control.CorridorFollowerProgram
import com.lambda.pathing.actions.control.TerminalApproach
import com.lambda.pathing.actions.control.brakeToStop
import com.lambda.pathing.actions.control.hover
import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.actions.control.treadWater
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.VERTICAL_GOAL_TOLERANCE
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.center
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
	private val route: RouteState,
	private val routePoints: RoutePoints,
) {
	private var provenApproach: TerminalApproach? = null
	private var sweepEpoch = 0
	private var sweeps = 0

	fun resetSweeps() {
		sweepEpoch++
		sweeps = 0
	}

	fun sweep(anchor: ValueAnchor): Solution? {
		if (anchor.sweptEpoch == sweepEpoch || sweeps >= MAX_FINISH_SWEEPS) return null
		anchor.sweptEpoch = sweepEpoch
		sweeps++
		return finishFrom(anchor)
	}

	fun finishFrom(anchor: ValueAnchor): Solution? {
		val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
		if (!field.reachesGoal(chain)) return null
		val points = routePoints.of(chain)
		val leads: List<Double?> = if (chain.asSequence().zipWithNext().any { (from, to) -> to.y > from.y }) {
			STEP_UP_JUMP_LEADS
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
			if (approach == provenApproach) return Solution(anchor, frames)
			if (best == null || candidate.beats(best)) best = candidate
		}
		val chosen = best ?: return null
		provenApproach = chosen.approach
		return Solution(anchor, chosen.frames)
	}

	fun brakeNearGoal(anchor: ValueAnchor, radius: Double): Solution? {
		val solution = brakeToStop(anchor) ?: return null
		val resting = solution.tailFrames.last().state
		val goal = route.goalPoint
		if (hypot(resting.position.x - goal.x, resting.position.z - goal.z) > radius) return null
		if (abs(resting.position.y - goal.y) > VERTICAL_GOAL_TOLERANCE && !(resting.touchingWater && stanceOf(resting) == route.goal)) return null
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

	fun brakeToStop(anchor: ValueAnchor): Solution? = hoverBrake(anchor) ?: groundBrake(anchor) ?: floatBrake(anchor)

	fun treadTape(anchor: ValueAnchor): Solution? {
		val gated = gate.run(anchor.state, listOf(anchor.stance.center(environment)), treadWater(anchor.state.rotation.yaw), FLOAT_TAIL_FRAMES)
		if (gated.failed) return null
		val frames = gated.rollout.frames
		val end = frames.indices.lastOrNull { settledAfloat(frames, it) } ?: return null
		if (end < FLOAT_TAIL_FRAMES - FLOAT_END_SLACK_FRAMES) return null
		return Solution(anchor, frames.take(end + 1))
	}

	private fun settledAfloat(frames: List<SimulatedTrajectoryFrame>, end: Int): Boolean =
		end >= SETTLED_FRAMES - 1 && frames[end].state.touchingWater &&
			(end - SETTLED_FRAMES + 1..end).all { frames[it].state.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED }

	private fun hoverBrake(anchor: ValueAnchor): Solution? {
		val stance = anchor.stance
		val view = field.view
		if (!DiveMovement.submerged(view, stance.x, stance.y, stance.z) || view.medium(stance.x, stance.y - 1, stance.z) != Medium.WATER) return null
		val center = stance.center(environment)
		val gated = gate.run(anchor.state, listOf(center), hover(center, stance.y + HOVER_DEPTH, config.maxYawDegreesPerFrame), HOVER_TAIL_FRAMES)
		if (gated.failed) return null
		var held = 0
		for ((index, frame) in gated.rollout.frames.withIndex()) {
			val state = frame.state
			held = if (stanceOf(state) == stance && state.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED) held + 1 else 0
			if (held >= config.stableStopFrames) return Solution(anchor, gated.rollout.frames.take(index + 1))
		}
		return null
	}

	private fun floatBrake(anchor: ValueAnchor): Solution? {
		if (field.view.medium(anchor.stance.x, anchor.stance.y, anchor.stance.z) != Medium.WATER) return null
		val gated = gate.run(anchor.state, listOf(anchor.stance.center(environment)), treadWater(anchor.state.rotation.yaw), FLOAT_TAIL_FRAMES, FLOAT_DIP)
		if (gated.failed) return null
		val frames = gated.rollout.frames
		val end = frames.indices.lastOrNull { field.floatsAt(frames[it].state, anchor.stance.y) && settledAfloat(frames, it) } ?: return null
		if (end < FLOAT_TAIL_FRAMES - FLOAT_END_SLACK_FRAMES) return null
		return Solution(anchor, frames.take(end + 1))
	}

	private fun groundBrake(anchor: ValueAnchor): Solution? {
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
			stable = if (cycleClosed && state.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED) stable + 1 else 0
			twoBack = previous
			previous = state
			if (stable >= config.stableStopFrames && state.onGround) {
				stableEnd = index
				break
			}
		}
		if (stableEnd < 0) return null
		val frames = gated.rollout.frames.take(stableEnd + 1)
		val resting = heldStanceOf(field.view, frames.last().state)
		if (!field.isStance(resting) || !field.isMapped(resting)) return null
		return Solution(anchor, frames)
	}

	private fun terminalRun(
		anchor: ValueAnchor,
		chain: List<Stance>,
		points: List<HorizontalPoint>,
		approach: TerminalApproach,
	): List<SimulatedTrajectoryFrame>? {
		val gated = gate.run(anchor.state, points, CorridorFollowerProgram(chain, approach, config), config.maxFrames)
		attempts.record(gated.rollout, gated.stopFrame != null, route.goalPoint, gated.diagnostic)
		return gated.rollout.frames.takeIf { gated.stopFrame != null && !gated.failed }
	}

	private companion object {
		const val MAX_FINISH_SWEEPS = 64
		val STEP_UP_JUMP_LEADS = listOf(0.30, 0.55, 0.80, 1.05)
		const val FINISH_CHAIN_LENGTH = 24

		const val BRAKE_TAIL_FRAMES = 64

		const val FLOAT_TAIL_FRAMES = 600

		const val FLOAT_END_SLACK_FRAMES = 20

		const val FLOAT_DIP = 2.0

		const val SETTLED_FRAMES = 3

		const val HOVER_TAIL_FRAMES = 200

		const val HOVER_DEPTH = 0.3
	}
}
