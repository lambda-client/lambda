package com.lambda.pathing.actions.families.jump

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.pathing.launch.LaunchSolution

/** When to press jump: the launch-delay roll-forward and its distance-estimate fallback. */
internal object JumpLaunchDelays {

	/**
	 * Ticks of run-up after which the body is both AT the launch offset and AT the arc's
	 * entry speed. The run-up is rolled analytically (`v' = friction * (v + acceleration * u)`,
	 * displacement by the pre-friction velocity); ticks whose speed lies in the entry band
	 * are candidates. Falls back to the distance estimate when none fits: this roll drives
	 * straight down the gap while the follower steers toward nodes, and losing the launch
	 * entirely is the worse error.
	 */
	fun launchDelays(context: DecisionContext, solution: LaunchSolution): List<Int> {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
		if (length <= 1e-9) return emptyList()
		val unitX = (to.x - from.x) / length
		val unitZ = (to.z - from.z) / length

		val dynamics = HorizontalDynamics.ground(context.ballistics, solution.sprint)
		val body = context.body.state
		var velocityX = body.velocity.x
		var velocityZ = body.velocity.z
		var along = alongEdge(from, to, body.position.x, body.position.z)

		// Acceleration follows the YAW, which turns toward the gap at a bounded rate;
		// rolling gap-aligned from tick zero overestimates entry speed on a zig-zag.
		val bearing = Math.toDegrees(kotlin.math.atan2(-(to.x - from.x), to.z - from.z))
		var yaw = body.rotation.yaw

		class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

		// The body must still be standing when the trigger fires (it only presses on
		// ground): the cell's half-extent along the gap plus the body's half width.
		val standableAlong = 0.5 * (kotlin.math.abs(unitX) + kotlin.math.abs(unitZ)) + 0.3

		val fitting = ArrayList<Fit>()
		for (delay in 0..MAX_LAUNCH_FRAME) {
			if (along > standableAlong) break
			val speed = velocityX * unitX + velocityZ * unitZ
			if (kotlin.math.abs(speed - solution.speed) <= solution.speedSlack) {
				// Cost in ticks so the terms are commensurable: the run-up plus the offset
				// error walked off at cruise.
				val misplacement = kotlin.math.abs(along - solution.launchOffset)
				fitting += Fit(
					delay,
					delay + misplacement / dynamics.cruise,
					misplacement,
					kotlin.math.abs(speed - solution.speed),
				)
			}
			// One tick of running: the yaw turns toward the gap bearing at the bounded
			// rate and acceleration follows the yaw. The body is displaced by the
			// pre-friction velocity, which is what the stored velocity divides back out to.
			val yawError = Rotation.wrap(bearing - yaw)
			yaw += yawError.coerceIn(-context.constraints.maxYawDegreesPerFrame, context.constraints.maxYawDegreesPerFrame)
			val radians = Math.toRadians(yaw)
			val forwardX = -kotlin.math.sin(radians)
			val forwardZ = kotlin.math.cos(radians)
			velocityX = (velocityX + dynamics.acceleration * forwardX) * dynamics.friction
			velocityZ = (velocityZ + dynamics.acceleration * forwardZ) * dynamics.friction
			along += (velocityX * unitX + velocityZ * unitZ) / dynamics.friction
		}
		// No delay-0 for bodies faster than the band: see docs/decisions/movement-tuning.md
		// (launch-delay ranking).
		if (fitting.isEmpty()) {
			val nominal = launchFrame(context, solution)
			return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
				.distinct()
				.filter { !unreachableEntry(context, it, solution) }
		}
		// Three objectives that genuinely disagree (open ground wants cheap, a one-block
		// pad wants placement, a lone diagonal pad wants entry speed): offer the best of
		// each and let the priced frontier decide. See docs/decisions/movement-tuning.md.
		val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
		val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
		val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
		return (cheapest.take(2) + truest.take(2) + fastest.take(2))
			.distinct()
			.take(MAX_LAUNCH_DELAY_CANDIDATES)
	}

	private fun launchFrame(context: DecisionContext, solution: LaunchSolution): Int {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val body = context.body.state
		val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
		if (remaining <= 0.0) return 0

		val closing = alongEdge(
			from, to,
			from.x + body.velocity.x,
			from.z + body.velocity.z,
		).coerceAtLeast(0.0)

		return context.ballistics.groundRunUpTicks(
			entrySpeed = closing,
			distance = remaining,
			sprint = solution.sprint,
			maxTicks = MAX_LAUNCH_FRAME,
		)
	}

	/**
	 * Whether the body cannot be at the arc's entry speed after [delay] ticks: the
	 * reachable velocity disc, projected onto the gap, must overlap the entry band.
	 * Deliberately the projection, not the whole velocity -- the arc carries its own
	 * lateral tolerance. See docs/decisions/movement-tuning.md (entry reachability).
	 */
	private fun unreachableEntry(
		context: DecisionContext,
		delay: Int,
		solution: LaunchSolution,
	): Boolean {
		val edge = context.edge
		val from = edge.from.center()
		val to = edge.to.center()
		val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
		if (length <= 1e-9) return false
		val unitX = (to.x - from.x) / length
		val unitZ = (to.z - from.z) / length
		val velocity = context.body.state.velocity
		val along = HorizontalDynamics.ground(context.ballistics, solution.sprint)
			.reachable(velocity.x, velocity.z, delay)
			.projectOnto(unitX, unitZ)
		val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
		return along.endInclusive < needed.start || along.start > needed.endInclusive
	}

	private val LAUNCH_BRACKET = listOf(0, -1, 1)

	/** Two picks from each of the three ranking objectives, before dedup; a tighter cap drops an objective. */
	private const val MAX_LAUNCH_DELAY_CANDIDATES = 6

	/** Longest ground run-up any launch decision may schedule before the jump key. */
	const val MAX_LAUNCH_FRAME = 8
}
