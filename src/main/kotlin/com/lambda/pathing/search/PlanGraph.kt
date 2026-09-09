package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.physics.MovementSimulationState

class PlanJunction(
	val index: Int,
	val frame: Int,
	val state: MovementSimulationState,
	val settled: Boolean,
	val rejoinable: Boolean,
)

class PlanGraph private constructor(
	val junctions: List<PlanJunction>,
	val spine: List<PlanSegment>,
) {
	val frames: Int get() = junctions.last().frame

	fun repairJunction(firstAffectedFrame: Int, cursorFrame: Int, minLeadFrames: Int): PlanJunction? =
		junctions.lastOrNull { junction ->
			junction.index > 0 && junction.rejoinable &&
					junction.frame < firstAffectedFrame &&
					junction.frame >= cursorFrame + minLeadFrames
		}

	fun improvementDepartures(beforeJunction: Int = junctions.lastIndex, maxSpan: Int = DEFAULT_MAX_SPAN): List<Int> {
		require(beforeJunction in 0..junctions.size)
		val scores = DoubleArray(beforeJunction) { Double.NEGATIVE_INFINITY }
		for (start in 0 until beforeJunction) {
			if (!junctions[start].rejoinable) continue
			for (end in start + 1..minOf(start + maxSpan, junctions.lastIndex)) {
				if (!junctions[end].rejoinable) continue
				val frames = junctions[end].frame - junctions[start].frame
				if (frames <= 0) continue
				val displacement = junctions[start].state.position.distanceTo(junctions[end].state.position)
				if (displacement < MIN_SPAN_BLOCKS) continue
				val score = frames / displacement
				if (score > scores[start]) scores[start] = score
			}
		}
		return scores.indices.filter { scores[it] != Double.NEGATIVE_INFINITY }.sortedByDescending { scores[it] }
	}

	companion object {

		const val DEFAULT_MAX_SPAN = 8

		const val MIN_SPAN_BLOCKS = 1.0

		fun rejoinable(state: MovementSimulationState): Boolean = state.onGround

		fun of(
			plan: TrajectoryPlan,
			constraints: MotionConstraints = MotionConstraints(),
			rejoinable: (MovementSimulationState) -> Boolean = ::rejoinable,
		): PlanGraph? = of(plan.segments, plan.initialState, constraints, rejoinable)

		fun of(
			spine: List<PlanSegment>,
			initialState: MovementSimulationState,
			constraints: MotionConstraints = MotionConstraints(),
			rejoinable: (MovementSimulationState) -> Boolean = ::rejoinable,
		): PlanGraph? {
			if (spine.isEmpty()) return null
			val junctions = ArrayList<PlanJunction>(spine.size + 1)

			junctions += PlanJunction(index = 0, frame = 0, state = initialState, settled = true, rejoinable = true)
			spine.forEachIndexed { index, segment ->
				junctions += PlanJunction(
					index = index + 1,
					frame = segment.endFrame,
					state = segment.exit,
					settled = segment.settledExit(constraints.stoppedSpeed),
					rejoinable = rejoinable(segment.exit),
				)
			}
			return PlanGraph(junctions, spine)
		}
	}
}
