package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.Proposals
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolver
import kotlin.math.abs
import kotlin.math.hypot

internal object MomentumProposals {

	fun proposals(context: ProposalContext): Proposals {
		if (!context.momentum) return Proposals.EMPTY
		val gait = gaitHop(context)
		val skips = skipProposals(context)
		if (gait == null) return skips
		return Proposals(launches = listOf(gait) + skips.launches)
	}

	private fun gaitHop(context: ProposalContext): TrajectoryDecision? {
		val body = context.body
		if (!body.state.onGround) return null
		if (body.speed < GAIT_MIN_SPEED) return null
		val heading = body.heading() ?: return null
		val first = context.steps.firstOrNull()?.to ?: return null
		val chain = context.steering.chain(body.stance, first, GAIT_LOOKAHEAD, heading)
		val headingLength = hypot(heading.first, heading.second)
		var target: Stance? = null
		for (index in 1 until chain.size) {
			val cell = chain[index]

			if (abs(cell.y - body.stance.y) > 1) break
			val towardX = (cell.x - body.stance.x).toDouble()
			val towardZ = (cell.z - body.stance.z).toDouble()
			val distance = hypot(towardX, towardZ)
			if (distance > GAIT_MAX_HOP_BLOCKS) break
			if (distance < GAIT_MIN_HOP_BLOCKS) continue

			if (cell.y > body.stance.y) continue
			val alignment = (heading.first * towardX + heading.second * towardZ) /
					(headingLength * distance)
			if (alignment >= GAIT_MIN_ALIGNMENT) target = cell
		}
		val hop = target ?: return null
		return TrajectoryDecision.Launch(sprint = true, step = hop, delayFrames = 0, solution = null)
	}

	private fun skipProposals(context: ProposalContext): Proposals {
		val body = context.body
		if (!body.state.onGround) return Proposals.EMPTY
		if (body.speed < SKIP_MIN_SPEED) return Proposals.EMPTY
		val first = context.steps.firstOrNull()?.to ?: return Proposals.EMPTY
		val chain = context.steering.chain(body.stance, first, SKIP_LOOKAHEAD, body.heading())
		if (chain.size <= SKIP_MIN_CELLS) return Proposals.EMPTY
		val reachable = maxOf(body.speed, BallisticProfile.VANILLA.cruiseSpeed(sprint = true))

		val here = context.movingGuideTicks(body.stance)
		val heading = body.heading() ?: return Proposals.EMPTY
		for (index in chain.lastIndex downTo SKIP_MIN_CELLS) {
			val target = chain[index]
			if (target.y > body.stance.y) continue
			if (body.stance.y - target.y > SKIP_MAX_DROP) continue
			val towardX = (target.x - body.stance.x).toDouble()
			val towardZ = (target.z - body.stance.z).toDouble()
			val distance = hypot(towardX, towardZ)
			if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) continue

			val alignment = (heading.first * towardX + heading.second * towardZ) /
					(hypot(heading.first, heading.second) * distance)
			if (alignment < SKIP_MIN_ALIGNMENT) continue
			val solution = LaunchSolver.best(
				body.stance, target,
				modes = JumpTemplates.MODES,
				maxEntrySpeed = { reachable },
			) ?: continue

			var walked = 0.0
			var skipsGap = false
			for (step in 1..index) {
				val stride = hypot(
					(chain[step].x - chain[step - 1].x).toDouble(),
					(chain[step].z - chain[step - 1].z).toDouble(),
				)
				walked += stride
				if (stride > SKIP_GAP_STRIDE_BLOCKS) skipsGap = true
			}
			val falls = body.stance.y - target.y >= SKIP_MIN_FALL_BLOCKS
			val cuts = walked - distance >= SKIP_MIN_CUT_BLOCKS
			if (!skipsGap && !falls && !cuts) continue

			if (here.isFinite()) {
				val there = context.movingGuideTicks(target)
				if (there.isFinite()) {
					val flightTicks = solution.airTicks + SKIP_LAUNCH_PREP_TICKS
					if (here - there < flightTicks + SKIP_MIN_SAVING_TICKS) continue
				}
			}
			return Proposals(
				launches = listOf(
					TrajectoryDecision.Launch(solution.sprint, target, delayFrames = 0, solution),
				),
			)
		}
		return Proposals.EMPTY
	}

	private const val SKIP_MIN_SPEED = 0.15

	private const val SKIP_LOOKAHEAD = 5

	private const val SKIP_MIN_CELLS = 2

	private const val SKIP_MAX_DROP = 3

	private const val SKIP_MIN_CUT_BLOCKS = 1.0

	private const val SKIP_MIN_FALL_BLOCKS = 2

	private const val SKIP_GAP_STRIDE_BLOCKS = 1.5

	private const val SKIP_MIN_ALIGNMENT = 0.866

	private const val SKIP_LAUNCH_PREP_TICKS = 2

	private const val SKIP_MIN_SAVING_TICKS = 2.0

	private const val GAIT_MIN_SPEED = 0.2

	private const val GAIT_LOOKAHEAD = 5

	private const val GAIT_MIN_HOP_BLOCKS = 2.5

	private const val GAIT_MAX_HOP_BLOCKS = 3.8

	private const val GAIT_MIN_ALIGNMENT = 0.9
}
