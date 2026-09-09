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

	val arrivalTicksEstimate: Double = Double.NaN,
	val comparedRunningArrivalTicks: Double = Double.NaN,
	val comparedRunningSequence: Long = -1,

	val segments: List<TapeSegment> = emptyList(),

	val legTouches: List<LegTouch> = emptyList(),
) {

	val excessRatio: Double
		get() = (route.lowerBoundTicks + TERMINAL_STOP_TICKS).let {
			if (it > 0.0) plan.tape.frameCount / it else Double.NaN
		}

	private companion object {

		const val TERMINAL_STOP_TICKS = 16.0

		const val STANDING_SPEED = 0.012

		const val MIN_STANDING_RUN = 4
	}

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
