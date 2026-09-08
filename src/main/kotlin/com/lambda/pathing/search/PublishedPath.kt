package com.lambda.pathing.search

import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.PlayerPhysicsProfile

data class PublishedPath(
	val route: CoarseRoutePlan,
	val plan: TrajectoryPlan,
	val profile: PlayerPhysicsProfile,
	val parameters: TerminalApproach,
	val safeAnchorStance: Stance,
	val safeAnchorFrame: Int,
	val remainingGuideTicks: Double,
	val attempts: Int,
	val planMillis: Long,
	val finalGoal: Stance,
	val controlSegments: Int = 1,
	val spliceFrames: List<Int> = emptyList(),
	val launchMarginFrames: Int = 0,
	val partial: Boolean = false,
	val planningGeneration: Long = 0L,
	val publicationSequence: Int = 0,

	/** Same-instant arrival comparison against [comparedRunningSequence]; see MotionPlanResult.Success. */
	val arrivalTicksEstimate: Double = Double.NaN,
	val comparedRunningArrivalTicks: Double = Double.NaN,
	val comparedRunningSequence: Long = -1,

	/** How the tape's frames divide between the movements that produced them. */
	val segments: List<TapeSegment> = emptyList(),

	/** Frames at which this tape passes the route's walk-through waypoints, in order. */
	val legTouches: List<LegTouch> = emptyList(),
) {
	/**
	 * Frames spent per frame the coarse route could not have avoided: tape length over the
	 * admissible [com.lambda.pathing.coarse.CoarseRoutePlan.lowerBoundTicks] plus the
	 * fixed arrival cost [TERMINAL_STOP_TICKS]. 1.0 is optimal for the route given.
	 * See docs/decisions/session-loop.md.
	 */
	val excessRatio: Double
		get() = (route.lowerBoundTicks + TERMINAL_STOP_TICKS).let {
			if (it > 0.0) plan.tape.frameCount / it else Double.NaN
		}

	private companion object {
		/** Frames a walk spends arriving (decelerate, centre, hold) rather than travelling. */
		const val TERMINAL_STOP_TICKS = 16.0

		/** Below this the body is standing, not merely slow. Matches the planner's stop test. */
		const val STANDING_SPEED = 0.012

		/** Shortest still run counted as a stop rather than a slow turn. */
		const val MIN_STANDING_RUN = 4
	}

	/** Frames per movement kind, heaviest first, as `movement=frames/segments`. */
	/**
	 * How the tape ends at [finalGoal]: the frame it first comes within a block, the frames
	 * spent from there to rest, the furthest it strays afterwards, and how often it leaves
	 * the goal's neighbourhood and returns. A loop count above zero is a walk around the goal.
	 */
	fun approachProfile(): String {
		val gx = finalGoal.x + 0.5
		val gz = finalGoal.z + 0.5
		val distances = plan.frames.map { kotlin.math.hypot(it.state.position.x - gx, it.state.position.z - gz) }
		val near = distances.indexOfFirst { it <= 1.0 }
		if (near < 0) return "approach: never within a block of the goal (final %.2f)".format(distances.lastOrNull() ?: Double.NaN)
		val tail = distances.subList(near, distances.size)
		var left = false
		var loops = 0
		tail.forEach { d ->
			if (d > 1.5) left = true
			if (left && d <= 1.0) {
				loops++; left = false
			}
		}
		return "approach: near@%d tail=%d stray=%.2f loops=%d final=%.2f".format(near, tail.size, tail.max(), loops, distances.last())
	}

	fun movementProfile(): String = segments
		.groupBy { it.movement }
		.mapValues { (_, parts) -> parts.sumOf { it.frames } to parts.size }
		.entries.sortedByDescending { it.value.first }
		.joinToString(" ") { (movement, cost) -> "$movement=${cost.first}/${cost.second}" }

	/**
	 * Run lengths of frames the finished tape spends standing still away from the goal:
	 * the permanent record of brakes the search failed to extend past.
	 */
	fun standingRunLengths(): List<Int> {
		val frames = plan.frames
		if (frames.isEmpty()) return emptyList()
		val still = frames.map { it.state.velocity.horizontalLength() <= STANDING_SPEED }
		val runs = ArrayList<Int>()
		var index = 0
		while (index < still.size) {
			if (!still[index]) {
				index++; continue
			}
			var end = index
			while (end < still.size && still[end]) end++
			// The run that reaches the last frame is the arrival stop, not a stall.
			if (end - index >= MIN_STANDING_RUN && end < still.size) runs += end - index
			index = end
		}
		return runs
	}

	fun standingFrames(): Int = standingRunLengths().sum()

	fun standingRuns(): Int = standingRunLengths().size

	fun standingPercent(): Int {
		val total = plan.tape.frameCount
		return if (total <= 0) 0 else standingFrames() * 100 / total
	}
}
