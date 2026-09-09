package com.lambda.pathing.launch

data class BounceSolution(
	val drop: Int,
	val rise: Int,
	val sprint: Boolean,
	val holdForward: Boolean,

	val jump: Boolean = false,

	val holdTicks: Int = Int.MAX_VALUE,

	val speed: Double,

	val speedSlack: Double,

	val launchOffset: Double,

	val contactDistance: Double,
	val arc: ArcSample,

	val contactDepth: Double = drop.toDouble(),
) {
	val airTicks: Int get() = arc.airTicks

	val distance: Double get() = arc.distance

	val rebound: Double get() = arc.heights.last() - arc.heights.min()
}

object BounceSolver {
	fun solve(
		horizontalDistance: Double,
		drop: Int,
		rise: Int,
		profile: BallisticProfile = BallisticProfile.VANILLA,
		sprint: Boolean = true,
		holdForward: Boolean = true,
		jump: Boolean = false,
		holdTicks: Int = Int.MAX_VALUE,
		maxEntrySpeed: Double = profile.momentumSpeed(sprint),

		contactDepth: Double = drop.toDouble(),

		riseHeight: Double = rise.toDouble(),

		headroom: Double = Double.POSITIVE_INFINITY,

		launchReach: Double = LAUNCH_OFFSET,
	): BounceSolution? {
		if (maxEntrySpeed < 0.0) return null

		val flight = horizontalDistance - launchReach
		if (flight <= 0.0) return null

		val base = profile.bounce(0.0, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null
		val unit = profile.bounce(1.0, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null
		val slope = unit.distance - base.distance
		if (slope <= 0.0) return null

		val speed = (flight - base.distance) / slope
		if (speed < 0.0 || speed > maxEntrySpeed) return null

		val arc = profile.bounce(speed, contactDepth, riseHeight, holdForward, sprint, jump, holdTicks, headroom = headroom) ?: return null

		val slack = (LANDING_WINDOW / 2.0) / slope

		return BounceSolution(
			drop = drop,
			rise = rise,
			sprint = sprint,
			holdForward = holdForward,
			jump = jump,
			holdTicks = holdTicks,
			speed = speed,
			speedSlack = minOf(slack, speed),
			launchOffset = launchReach,
			contactDistance = arc.distances[troughIndex(arc)],
			arc = arc,
			contactDepth = contactDepth,
		)
	}

	fun solveStanding(
		horizontalDistance: Double,
		drop: Int,
		rise: Int,
		jump: Boolean,
		sprint: Boolean,
		profile: BallisticProfile = BallisticProfile.VANILLA,
		contactDepth: Double = drop.toDouble(),
		riseHeight: Double = rise.toDouble(),

		contactPenalty: (Double) -> Double = { 0.0 },

		headroom: Double = Double.POSITIVE_INFINITY,

		launchReach: Double = LAUNCH_OFFSET,
	): BounceSolution? {
		val target = horizontalDistance - launchReach
		if (target <= 0.0) return null

		val minHold = if (sprint) 2 else if (jump) 0 else 1
		fun arcAt(holdTicks: Int): ArcSample? = profile.bounce(
			0.0, contactDepth, riseHeight,
			holdForward = holdTicks > 0, sprint = sprint, jump = jump, holdTicks = holdTicks,
			headroom = headroom,
		)

		var lo = minHold
		var hi = BallisticProfile.MAX_BOUNCE_TICKS
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
			if (hold < minHold || hold > BallisticProfile.MAX_BOUNCE_TICKS) continue
			val arc = arcAt(hold) ?: continue
			val error = kotlin.math.abs(arc.distance - target)
			if (error > LANDING_SUPPORT_REACH) continue
			val penalty = contactPenalty(launchReach + arc.distances[troughIndex(arc)])
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
			drop = drop,
			rise = rise,
			sprint = sprint,
			holdForward = chosenHold > 0,
			jump = jump,
			holdTicks = chosenHold,
			speed = 0.0,
			speedSlack = STANDING_REST_SPEED,
			launchOffset = launchReach,
			contactDistance = arc.distances[troughIndex(arc)],
			arc = arc,
			contactDepth = contactDepth,
		)
	}

	private const val HOLD_SCAN = 3

	private const val LANDING_SUPPORT_REACH = 0.8

	val STANDING_STYLES = listOf(
		true to false,
		false to false,
		true to true,
		false to true,
	)

	const val STANDING_REST_SPEED = 0.02

	private fun troughIndex(arc: ArcSample): Int {
		var index = 0
		for (i in arc.heights.indices) if (arc.heights[i] < arc.heights[index]) index = i
		return index
	}

	private const val LANDING_WINDOW = 1.0

	const val LAUNCH_OFFSET = 0.8
}
