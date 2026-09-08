package com.lambda.pathing.launch

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
	val clearance: Double = 0.0,

	/**
	 * Blocks of sideways shift, perpendicular to the gap axis (perp = (-unitZ, unitX)),
	 * applied to the whole flight line. Zero for every solver-produced solution; set by
	 * the arc probe when the centre line is blocked by a PARTIAL shape (a pane, a fence
	 * post) and a parallel line inside [lateralSlack] sweeps clear. The air steering
	 * flies the shifted aim closed-loop, and the rollout certifies whether the body can
	 * actually curve onto that line.
	 */
	val lateralOffset: Double = 0.0,

	/**
	 * Air ticks to keep forward pressed before releasing it.
	 *
	 * The control that separates where the arc lands from how fast it is going when it
	 * gets there. [Int.MAX_VALUE] holds throughout, which is what a jump does when nothing
	 * downstream cares.
	 */
	val holdTicks: Int = Int.MAX_VALUE,
) {
	val sprint: Boolean get() = mode.sprint
	val jumps: Boolean get() = mode.jumps
	val airTicks: Int get() = arc.airTicks
	val apex: Double get() = arc.apex

	/**
	 * Blocks the body drops between the arc's apex and its landing.
	 *
	 * The rollout evaluator measures exactly this and refuses the trajectory when it
	 * exceeds the safe fall distance, so the same number decides -- before any physics
	 * is simulated -- whether the launch is worth offering at all.
	 */
	fun fallDistance(rise: Double): Double = (apex - rise).coerceAtLeast(0.0)

	fun withClearance(clearance: Double) = copy(clearance = clearance)

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

	/**
	 * Whether this solution beats [other] for a caller that knows what comes next.
	 *
	 * Landing where the next gap can be launched from is worth more than landing
	 * comfortably: a wide margin on an arc that arrives too fast to jump again is a
	 * margin on a dead end. Within a verdict, the usual comfort ranking decides.
	 */
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

	fun solve(
		from: Stance,
		to: Stance,
		profile: BallisticProfile = BallisticProfile.VANILLA,
		modes: List<LaunchMode> = LaunchMode.entries,
		maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
		preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },

		rise: Double = (to.y - from.y).toDouble(),

		/**
		 * Entry speeds the *following* gap can accept, if the caller knows of one.
		 *
		 * On a chain of pads the exit speed *is* the next gap's entry speed, so an informed
		 * solve trims the arc to land inside this window. Null keeps the historical
		 * behaviour exactly, including which throttle policies are considered.
		 * See docs/decisions/movement-tuning.md (exit-speed window).
		 */
		exitSpeedWindow: ClosedFloatingPointRange<Double>? = null,

		/**
		 * A catch, not a landing: the body must reach the target's face while its feet
		 * are still at or above [rise], and the face stops any overshoot. The landing
		 * window loses its far edge and the fastest feasible entry is taken; a slow arc
		 * arrives already descending and slides below the rung.
		 */
		catch: Boolean = false,
	): List<LaunchSolution> {
		val dx = (to.x - from.x).toDouble()
		val dz = (to.z - from.z).toDouble()
		val length = hypot(dx, dz)
		if (length <= 1e-9) return emptyList()
		val unitX = dx / length
		val unitZ = dz / length

		val lateral = lateralSlack(unitX, unitZ)
		if (lateral < 0.0) return emptyList()

		return modes.filter { it.supports(rise) }.mapNotNull { mode ->
			solveMode(
				from, to, unitX, unitZ, rise, lateral, mode, profile,
				maxEntrySpeed(mode), preferredEntrySpeed(mode), exitSpeedWindow, catch,
			)
		}.sortedWith(LaunchSolution.BEST_FIRST)
	}

	fun best(
		from: Stance,
		to: Stance,
		profile: BallisticProfile = BallisticProfile.VANILLA,
		modes: List<LaunchMode> = LaunchMode.entries,
		maxEntrySpeed: (LaunchMode) -> Double = { profile.momentumSpeed(it.sprint) },
		preferredEntrySpeed: (LaunchMode) -> Double = { profile.cruiseSpeed(it.sprint) },
		rise: Double = (to.y - from.y).toDouble(),
		exitSpeedWindow: ClosedFloatingPointRange<Double>? = null,
		catch: Boolean = false,
	): LaunchSolution? =
		solve(from, to, profile, modes, maxEntrySpeed, preferredEntrySpeed, rise, exitSpeedWindow, catch)
			.firstOrNull()

	private fun solveMode(
		from: Stance,
		to: Stance,
		unitX: Double,
		unitZ: Double,
		rise: Double,
		lateral: Double,
		mode: LaunchMode,
		profile: BallisticProfile,
		maxEntrySpeed: Double,
		preferredEntrySpeed: Double,
		exitSpeedWindow: ClosedFloatingPointRange<Double>?,
		catch: Boolean = false,
	): LaunchSolution? {
		if (maxEntrySpeed < 0.0) return null

		val landing = landingWindow(from, to, unitX, unitZ) ?: return null
		val window = if (catch) landing.start..Double.POSITIVE_INFINITY else landing

		var best: LaunchSolution? = null

		// Releasing forward in flight is the only way a jump lands slower than it took
		// off, so the released variant is offered only when a landing speed must be
		// satisfied; uninformed jumps hold forward. See docs/decisions/movement-tuning.md.
		val policies =
			if (mode.drops || exitSpeedWindow != null) listOf(true, false) else listOf(true)

		for (holdForward in policies) {

			val base = profile.fly(mode, 0.0, rise, holdForward) ?: continue
			val unit = profile.fly(mode, 1.0, rise, holdForward) ?: continue
			val slope = unit.distance - base.distance
			if (slope <= 1e-9) continue

			for (offset in launchOffsets(mode)) {

				fun speedFor(distance: Double) = (distance - offset - base.distance) / slope

				// A rising jump that lands at the window's near edge arrives at the
				// target height EXACTLY at the lip -- zero clearance, and any
				// model-vs-simulation epsilon becomes a face hit. Inset the near edge
				// so marginal entries are priced as infeasible instead of ground out.
				val nearEdge = window.start + if (rise > 0.0) RISING_NEAR_EDGE_INSET else 0.0
				val feasibleLow = max(speedFor(nearEdge), 0.0)
				val feasibleHigh = min(speedFor(window.endInclusive), maxEntrySpeed)
				if (feasibleHigh < feasibleLow) continue

				val speed = if (catch) feasibleHigh else preferredSpeed(preferredEntrySpeed, feasibleLow, feasibleHigh, slope)

				val arc = profile.fly(mode, speed, rise, holdForward) ?: continue
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
			released(it, rise, profile, window, exitSpeedWindow, maxEntrySpeed, preferredEntrySpeed)
		}
	}

	/**
	 * Trim the throttle so the arc still lands, but arrives slowly enough to continue.
	 *
	 * Only reached when a downstream window says how fast the body may arrive and the
	 * full-throttle arc is too fast. Exit speed rises monotonically with hold length, so
	 * the longest fitting hold is found by bisection, and the entry speed is re-solved at
	 * each trial. See docs/decisions/movement-tuning.md (released-hold bisection).
	 */
	private fun released(
		solution: LaunchSolution,
		rise: Double,
		profile: BallisticProfile,
		window: ClosedFloatingPointRange<Double>,
		exitSpeedWindow: ClosedFloatingPointRange<Double>?,
		maxEntrySpeed: Double,
		preferredEntrySpeed: Double,
	): LaunchSolution {
		if (exitSpeedWindow == null) return solution
		if (solution.arc.exitSpeed <= exitSpeedWindow.endInclusive) return solution

		fun solveAt(hold: Int): LaunchSolution? {
			val mode = solution.mode
			val held = solution.holdForward
			val base = profile.fly(mode, 0.0, rise, held, holdTicks = hold) ?: return null
			val unit = profile.fly(mode, 1.0, rise, held, holdTicks = hold) ?: return null
			val slope = unit.distance - base.distance
			if (slope <= 1e-9) return null
			val offset = solution.launchOffset
			val nearEdge = window.start + if (rise > 0.0) RISING_NEAR_EDGE_INSET else 0.0
			val low = max((nearEdge - offset - base.distance) / slope, 0.0)
			val high = min((window.endInclusive - offset - base.distance) / slope, maxEntrySpeed)
			if (high < low) return null
			val speed = preferredSpeed(preferredEntrySpeed, low, high, slope)
			val arc = profile.fly(mode, speed, rise, held, holdTicks = hold) ?: return null
			val slack = min(speed - low, high - speed)
			return solution.copy(
				speed = speed,
				speedSlack = slack,
				landingSlack = slack * slope,
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

	private const val BODY_HALF_WIDTH = Kinematics.BODY_HALF_WIDTH

	private const val LANDING_SAFETY_BLOCKS = 0.2

	private const val RISING_NEAR_EDGE_INSET = 0.15

	private const val LANDING_AIM_INSET = LANDING_SAFETY_BLOCKS + BODY_HALF_WIDTH

	private const val AXIS_EPSILON = 1e-9
}

fun BallisticProfile.momentumSpeed(sprint: Boolean): Double {
	val cruise = cruiseSpeed(sprint)
	val mode = if (sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
	val landing = fly(mode, cruise, 0.0) ?: return cruise
	return max(cruise, landing.exitSpeed)
}
