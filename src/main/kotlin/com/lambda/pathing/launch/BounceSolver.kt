package com.lambda.pathing.launch

import com.lambda.pathing.core.Via
import kotlin.math.abs

data class BounceSolution(
	val sprint: Boolean,
	val holdForward: Boolean,
	val jump: Boolean,
	val holdTicks: Int,
	val speed: Double,
	val speedSlack: Double,
	val launchOffset: Double,
	val arc: ArcSample,
	val contactDepth: Double,
	val pressesWall: Boolean = false,
	val via: Via? = null,
) {
	val airTicks: Int get() = arc.airTicks

	val contactDistance: Double get() = arc.distances[arc.troughIndex()]

	val contactDistances: List<Double> get() = arc.contactIndices().map { arc.distances[it] }
}

object BounceSolver {
	private val ballistics = BallisticProfile.VANILLA

	fun solve(
		horizontalDistance: Double,
		contactDepth: Double,
		riseHeight: Double,
		sprint: Boolean = true,
		holdForward: Boolean = true,
		jump: Boolean = false,
		holdTicks: Int = Int.MAX_VALUE,
		headroom: Double = Double.POSITIVE_INFINITY,
		launchReach: Double = LAUNCH_OFFSET,
		bounces: Int = 1,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
		contactDepths: DoubleArray? = null,
	): BounceSolution? {
		val flight = horizontalDistance - launchReach
		if (flight <= 0.0) return null

		fun arcAt(entrySpeed: Double) = bounceArc(entrySpeed, contactDepth, contactDepths, riseHeight, holdForward, sprint, jump, holdTicks, bounces, headroom, reboundHeadroom)
		val base = arcAt(0.0) ?: return null
		val unit = arcAt(1.0) ?: return null
		val slope = unit.distance - base.distance
		if (slope <= 0.0) return null

		val speed = (flight - base.distance) / slope
		if (speed < 0.0 || speed > ballistics.momentumSpeed(sprint)) return null

		val arc = arcAt(speed) ?: return null

		val slack = (LANDING_WINDOW / 2.0) / slope

		return BounceSolution(
			sprint = sprint,
			holdForward = holdForward,
			jump = jump,
			holdTicks = holdTicks,
			speed = speed,
			speedSlack = minOf(slack, speed),
			launchOffset = launchReach,
			arc = arc,
			contactDepth = contactDepths?.max() ?: contactDepth,
		)
	}

	fun solvePressing(
		horizontalDistance: Double,
		contactDepth: Double,
		riseHeight: Double,
		sprint: Boolean,
		jump: Boolean,
		headroom: Double,
		launchReach: Double,
		bounces: Int = 1,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
	): BounceSolution? {
		val flight = horizontalDistance - launchReach
		if (flight <= 0.0) return null
		val standing = ballistics.bounce(0.0, contactDepth, riseHeight, holdForward = true, sprint = sprint, jump = jump, bounces = bounces, headroom = headroom, reboundHeadroom = reboundHeadroom)
			?: return null
		if (standing.distance < flight) {
			return solve(horizontalDistance, contactDepth, riseHeight, sprint = sprint, jump = jump, headroom = headroom, launchReach = launchReach, bounces = bounces, reboundHeadroom = reboundHeadroom)
		}
		return BounceSolution(
			sprint = sprint,
			holdForward = true,
			jump = jump,
			holdTicks = Int.MAX_VALUE,
			speed = 0.0,
			speedSlack = STANDING_REST_SPEED,
			launchOffset = launchReach,
			arc = standing,
			contactDepth = contactDepth,
		)
	}

	fun solveStanding(
		horizontalDistance: Double,
		jump: Boolean,
		sprint: Boolean,
		contactDepth: Double,
		riseHeight: Double,
		contactPenalty: (contact: Int, reach: Double) -> Double,
		headroom: Double,
		launchReach: Double,
		bounces: Int = 1,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
		contactDepths: DoubleArray? = null,
	): BounceSolution? {
		val target = horizontalDistance - launchReach
		if (target <= 0.0) return null

		val minHold = if (sprint) 2 else if (jump) 0 else 1
		val maxHold = BallisticProfile.MAX_BOUNCE_TICKS * (contactDepths?.size ?: bounces)
		fun arcAt(holdTicks: Int): ArcSample? =
			bounceArc(0.0, contactDepth, contactDepths, riseHeight, holdTicks > 0, sprint, jump, holdTicks, bounces, headroom, reboundHeadroom)

		var lo = minHold
		var hi = maxHold
		val shortest = arcAt(lo)?.distance ?: return null
		val longest = arcAt(hi)?.distance ?: return null
		if (target < shortest - LANDING_SUPPORT_REACH || target > longest + LANDING_SUPPORT_REACH) return null
		while (hi - lo > 1) {
			val mid = (lo + hi) / 2
			val at = arcAt(mid)?.distance
			if (at == null || at < target) lo = mid else hi = mid
		}

		var chosenHold = -1
		var chosenArc: ArcSample? = null
		var chosenPenalty = Double.POSITIVE_INFINITY
		var chosenError = Double.POSITIVE_INFINITY
		for (hold in (lo - HOLD_SCAN)..(hi + HOLD_SCAN)) {
			if (hold < minHold || hold > maxHold) continue
			val arc = arcAt(hold) ?: continue
			val error = abs(arc.distance - target)
			if (error > LANDING_SUPPORT_REACH) continue
			val penalty = arc.contactIndices().withIndex().maxOf { (contact, index) -> contactPenalty(contact, launchReach + arc.distances[index]) }
			if (!penalty.isFinite()) continue
			if (penalty < chosenPenalty || (penalty == chosenPenalty && error < chosenError)) {
				chosenHold = hold
				chosenArc = arc
				chosenPenalty = penalty
				chosenError = error
			}
		}
		val arc = chosenArc ?: return null

		return BounceSolution(
			sprint = sprint,
			holdForward = chosenHold > 0,
			jump = jump,
			holdTicks = chosenHold,
			speed = 0.0,
			speedSlack = STANDING_REST_SPEED,
			launchOffset = launchReach,
			arc = arc,
			contactDepth = contactDepths?.max() ?: contactDepth,
		)
	}

	private fun bounceArc(
		entrySpeed: Double,
		contactDepth: Double,
		contactDepths: DoubleArray?,
		riseHeight: Double,
		holdForward: Boolean,
		sprint: Boolean,
		jump: Boolean,
		holdTicks: Int,
		bounces: Int,
		headroom: Double,
		reboundHeadroom: Double,
	): ArcSample? = if (contactDepths == null) {
		ballistics.bounce(entrySpeed, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, bounces = bounces, headroom = headroom, reboundHeadroom = reboundHeadroom)
	} else {
		ballistics.bounce(entrySpeed, contactDepths, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom, reboundHeadroom = reboundHeadroom)
	}

	fun reach(drop: Int, rise: Int, bounces: Int = 1, ledgeDepth: Double = 0.0): ClosedFloatingPointRange<Double>? {
		val depth = drop.toDouble()
		val height = rise - ledgeDepth
		var floor = Double.POSITIVE_INFINITY
		var ceiling = Double.NEGATIVE_INFINITY
		for (jump in listOf(true, false)) {
			val standing = ballistics.bounce(0.0, depth, height, holdForward = true, sprint = true, jump = jump, bounces = bounces) ?: continue
			val unit = ballistics.bounce(1.0, depth, height, holdForward = true, sprint = true, jump = jump, bounces = bounces) ?: continue
			val slope = unit.distance - standing.distance
			if (slope <= 0.0) continue
			val gentle = ballistics.bounce(0.0, depth, height, holdForward = false, sprint = false, jump = jump, bounces = bounces)
			floor = minOf(floor, (gentle ?: standing).distance + LAUNCH_OFFSET)
			ceiling = maxOf(ceiling, standing.distance + LAUNCH_OFFSET + slope * ballistics.momentumSpeed(sprint = true))
		}
		return if (floor <= ceiling) floor..ceiling else null
	}

	/** Ticks of the quickest bounce, walked off or jumped, whose arc carries [distance] from the take-off stance centre. */
	fun ticks(drop: Int, rise: Int, distance: Double, bounces: Int = 1, ledgeDepth: Double = 0.0): Int? {
		val depth = drop.toDouble()
		val height = rise - ledgeDepth
		val flight = distance - LAUNCH_OFFSET
		var best: Int? = null
		for (jump in listOf(false, true)) {
			val standing = ballistics.bounce(0.0, depth, height, holdForward = true, sprint = true, jump = jump, bounces = bounces) ?: continue
			val unit = ballistics.bounce(1.0, depth, height, holdForward = true, sprint = true, jump = jump, bounces = bounces) ?: continue
			val slope = unit.distance - standing.distance
			if (slope <= 0.0) continue
			val gentle = ballistics.bounce(0.0, depth, height, holdForward = false, sprint = false, jump = jump, bounces = bounces)
			if (flight < (gentle ?: standing).distance || flight > standing.distance + slope * ballistics.momentumSpeed(sprint = true)) continue
			val ticks = standing.airTicks + if (jump) 1 else 0
			best = minOf(best ?: ticks, ticks)
		}
		return best
	}

	private const val HOLD_SCAN = 3

	private const val LANDING_SUPPORT_REACH = 0.5 + BODY_HALF_WIDTH

	const val STANDING_REST_SPEED = 0.02

	private const val LANDING_WINDOW = 1.0

	const val LAUNCH_OFFSET = 0.5 + BODY_HALF_WIDTH
}

private fun ArcSample.troughIndex(): Int {
	var index = 0
	for (i in heights.indices) if (heights[i] < heights[index]) index = i
	return index
}

private fun ArcSample.contactIndices(): List<Int> {
	if (contacts.isNotEmpty()) return contacts.toList()
	val floor = heights[troughIndex()]
	return heights.indices.filter { heights[it] == floor }
}
