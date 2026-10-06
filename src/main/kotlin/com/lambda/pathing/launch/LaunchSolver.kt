package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.Stance
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class LaunchSolution(
	val mode: LaunchMode,
	val launchOffset: Double,
	val speed: Double,
	val speedSlack: Double,
	val landingSlack: Double,
	val headroom: Double,
	val aimDistance: Double,
	val lateralSlack: Double,
	val holdForward: Boolean,
	val arc: ArcSample,
	val lateralOffset: Double = 0.0,

	val holdTicks: Int = Int.MAX_VALUE,
) {
	val sprint: Boolean get() = mode.sprint
	val jumps: Boolean get() = mode.jumps
	val airTicks: Int get() = arc.airTicks
	val apex: Double get() = arc.apex
	val heldForwardTicks: Int get() = if (holdForward) holdTicks else 0

	fun fallDistance(rise: Double): Double = (apex - rise).coerceAtLeast(0.0)

	val margin: Double
		get() =
			landingSlack + LATERAL_MARGIN_WEIGHT * lateralSlack +
					HEADROOM_WEIGHT * (if (jumps) headroom else -headroom)

	override fun equals(other: Any?): Boolean = this === other ||
			(other is LaunchSolution && mode == other.mode &&
					launchOffset == other.launchOffset && speed == other.speed &&
					aimDistance == other.aimDistance && airTicks == other.airTicks &&
					holdTicks == other.holdTicks && lateralOffset == other.lateralOffset)

	override fun hashCode(): Int {
		var result = mode.hashCode()
		result = 31 * result + launchOffset.hashCode()
		result = 31 * result + speed.hashCode()
		result = 31 * result + aimDistance.hashCode()
		result = 31 * result + airTicks
		result = 31 * result + holdTicks
		result = 31 * result + lateralOffset.hashCode()
		return result
	}

	internal fun outranks(
		other: LaunchSolution,
		exitSpeedWindow: ClosedFloatingPointRange<Double>?,
	): Boolean {
		if (exitSpeedWindow != null) {
			val mine = arc.exitSpeed in exitSpeedWindow
			val theirs = other.arc.exitSpeed in exitSpeedWindow
			if (mine != theirs) return mine
		}
		return margin > other.margin
	}

	companion object {

		val BEST_FIRST: Comparator<LaunchSolution> =
			compareByDescending<LaunchSolution> { it.margin }.thenBy { it.sprint }

		private const val LATERAL_MARGIN_WEIGHT = 0.25
		private const val HEADROOM_WEIGHT = 0.1
	}
}

object LaunchSolver {
	private val ballistics = BallisticProfile.VANILLA

	fun solve(
		from: Stance,
		to: Stance,
		modes: List<LaunchMode> = LaunchMode.entries,
		maxEntrySpeed: (LaunchMode) -> Double = { ballistics.momentumSpeed(it.sprint) },
		preferredEntrySpeed: (LaunchMode) -> Double = { ballistics.cruiseSpeed(it.sprint) },

		rise: Double = (to.y - from.y).toDouble(),

		exitSpeedWindow: ClosedFloatingPointRange<Double>? = null,

		catch: Boolean = false,
	): List<LaunchSolution> {
		val dx = (to.x - from.x).toDouble()
		val dz = (to.z - from.z).toDouble()
		val length = hypot(dx, dz)
		if (length <= DEGENERATE_LENGTH) return emptyList()
		val unitX = dx / length
		val unitZ = dz / length

		val lateral = lateralSlack(unitX, unitZ)
		if (lateral < 0.0) return emptyList()
		val landing = landingWindow(from, to, unitX, unitZ) ?: return emptyList()
		val window = if (catch) landing.start..Double.POSITIVE_INFINITY else landing
		val nearEdge = window.start + if (rise > 0.0) RISING_NEAR_EDGE_INSET else 0.0

		return modes.filter { it.supports(rise) }.mapNotNull { mode ->
			solveMode(
				mode, rise, lateral, window, nearEdge,
				maxEntrySpeed(mode), preferredEntrySpeed(mode), exitSpeedWindow, catch,
			)
		}.sortedWith(LaunchSolution.BEST_FIRST)
	}

	fun best(
		from: Stance,
		to: Stance,
		modes: List<LaunchMode> = LaunchMode.entries,
		maxEntrySpeed: (LaunchMode) -> Double = { ballistics.momentumSpeed(it.sprint) },
		preferredEntrySpeed: (LaunchMode) -> Double = { ballistics.cruiseSpeed(it.sprint) },
		rise: Double = (to.y - from.y).toDouble(),
		exitSpeedWindow: ClosedFloatingPointRange<Double>? = null,
		catch: Boolean = false,
	): LaunchSolution? =
		solve(from, to, modes, maxEntrySpeed, preferredEntrySpeed, rise, exitSpeedWindow, catch)
			.firstOrNull()

	private fun solveMode(
		mode: LaunchMode,
		rise: Double,
		lateral: Double,
		window: ClosedFloatingPointRange<Double>,
		nearEdge: Double,
		maxEntrySpeed: Double,
		preferredEntrySpeed: Double,
		exitSpeedWindow: ClosedFloatingPointRange<Double>?,
		catch: Boolean,
	): LaunchSolution? {
		if (maxEntrySpeed < 0.0) return null

		var best: LaunchSolution? = null

		val policies =
			if (mode.drops || exitSpeedWindow != null) listOf(true, false) else listOf(true)

		for (holdForward in policies) {

			val line = speedLine(mode, rise, holdForward, Int.MAX_VALUE) ?: continue
			val slope = line.slope

			for (offset in launchOffsets(mode)) {
				val feasibleLow = max(line.speedFor(nearEdge, offset), 0.0)
				val feasibleHigh = min(line.speedFor(window.endInclusive, offset), maxEntrySpeed)
				if (feasibleHigh < feasibleLow) continue

				val speed = if (catch) feasibleHigh else preferredSpeed(preferredEntrySpeed, feasibleLow, feasibleHigh, slope)

				val arc = ballistics.fly(mode, speed, rise, holdForward) ?: continue
				val candidate = LaunchSolution(
					mode = mode,
					launchOffset = offset,
					speed = speed,
					speedSlack = min(speed - feasibleLow, feasibleHigh - speed),
					landingSlack = min(speed - feasibleLow, feasibleHigh - speed) * slope,
					headroom = if (maxEntrySpeed > 0.0) (maxEntrySpeed - speed) / maxEntrySpeed else 0.0,
					aimDistance = offset + arc.distance,
					lateralSlack = lateral,
					holdForward = holdForward,
					arc = arc,
				)
				if (best == null || candidate.outranks(best, exitSpeedWindow)) best = candidate
			}
		}
		return best?.let {
			released(it, rise, window, nearEdge, exitSpeedWindow, maxEntrySpeed, preferredEntrySpeed)
		}
	}

	private fun released(
		solution: LaunchSolution,
		rise: Double,
		window: ClosedFloatingPointRange<Double>,
		nearEdge: Double,
		exitSpeedWindow: ClosedFloatingPointRange<Double>?,
		maxEntrySpeed: Double,
		preferredEntrySpeed: Double,
	): LaunchSolution {
		if (exitSpeedWindow == null) return solution
		if (solution.arc.exitSpeed <= exitSpeedWindow.endInclusive) return solution

		fun solveAt(hold: Int): LaunchSolution? {
			val line = speedLine(solution.mode, rise, solution.holdForward, hold) ?: return null
			val offset = solution.launchOffset
			val low = max(line.speedFor(nearEdge, offset), 0.0)
			val high = min(line.speedFor(window.endInclusive, offset), maxEntrySpeed)
			if (high < low) return null
			val speed = preferredSpeed(preferredEntrySpeed, low, high, line.slope)
			val arc = ballistics.fly(solution.mode, speed, rise, solution.holdForward, holdTicks = hold) ?: return null
			val slack = min(speed - low, high - speed)
			return solution.copy(
				speed = speed,
				speedSlack = slack,
				landingSlack = slack * line.slope,
				aimDistance = offset + arc.distance,
				arc = arc,
				holdTicks = hold,
			)
		}

		var low = 0
		var high = solution.airTicks + 1
		var best: LaunchSolution? = null
		while (low <= high) {
			val mid = (low + high) / 2
			val candidate = solveAt(mid)
			if (candidate == null || candidate.arc.exitSpeed > exitSpeedWindow.endInclusive) {
				high = mid - 1
			} else {
				best = candidate
				low = mid + 1
			}
		}
		return best ?: solution
	}

	private class SpeedLine(private val base: Double, val slope: Double) {
		fun speedFor(distance: Double, offset: Double): Double = (distance - offset - base) / slope
	}

	private fun speedLine(mode: LaunchMode, rise: Double, holdForward: Boolean, holdTicks: Int): SpeedLine? {
		val base = ballistics.fly(mode, 0.0, rise, holdForward, holdTicks = holdTicks) ?: return null
		val unit = ballistics.fly(mode, 1.0, rise, holdForward, holdTicks = holdTicks) ?: return null
		val slope = unit.distance - base.distance
		return if (slope <= MIN_SPEED_SLOPE) null else SpeedLine(base.distance, slope)
	}

	private fun preferredSpeed(preferred: Double, low: Double, high: Double, slope: Double): Double {
		val halfWidth = (high - low) * 0.5
		val safety = LANDING_AIM_INSET / slope

		if (safety >= halfWidth) return low + halfWidth
		return preferred.coerceIn(low + safety, high - safety)
	}

	private fun landingWindow(
		from: Stance,
		to: Stance,
		unitX: Double,
		unitZ: Double,
	): ClosedFloatingPointRange<Double>? {
		val originX = from.x + 0.5
		val originZ = from.z + 0.5
		var near = Double.NEGATIVE_INFINITY
		var far = Double.POSITIVE_INFINITY

		for ((origin, unit, minimum) in listOf(
			Triple(originX, unitX, to.x.toDouble()),
			Triple(originZ, unitZ, to.z.toDouble()),
		)) {
			if (abs(unit) < AXIS_EPSILON) {

				if (origin < minimum - BODY_HALF_WIDTH || origin > minimum + 1.0 + BODY_HALF_WIDTH) return null
				continue
			}
			val first = (minimum - BODY_HALF_WIDTH - origin) / unit
			val second = (minimum + 1.0 + BODY_HALF_WIDTH - origin) / unit
			near = max(near, min(first, second))
			far = min(far, max(first, second))
		}

		return if (far - near > 0.0) near..far else null
	}

	private fun lateralSlack(unitX: Double, unitZ: Double): Double {

		val halfExtent = (abs(unitZ) + abs(unitX)) * 0.5
		return halfExtent - BODY_HALF_WIDTH
	}

	private fun launchOffsets(mode: LaunchMode): List<Double> =
		if (mode.drops) DROP_OFFSETS else JUMP_OFFSETS

	private val JUMP_OFFSETS = listOf(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.5 + BODY_HALF_WIDTH)

	private val DROP_OFFSETS = listOf(0.5 + BODY_HALF_WIDTH)


	private const val LANDING_SAFETY_BLOCKS = 0.2

	private const val RISING_NEAR_EDGE_INSET = 0.15

	private const val LANDING_AIM_INSET = LANDING_SAFETY_BLOCKS + BODY_HALF_WIDTH

	private const val AXIS_EPSILON = 1e-9

	private const val MIN_SPEED_SLOPE = 1e-9
}

fun BallisticProfile.momentumSpeed(sprint: Boolean): Double {
	val cruise = cruiseSpeed(sprint)
	val mode = if (sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
	val landing = fly(mode, cruise, 0.0) ?: return cruise
	return max(cruise, landing.exitSpeed)
}
