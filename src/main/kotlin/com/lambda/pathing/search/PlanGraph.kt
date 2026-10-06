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

	companion object {

		fun rejoinable(state: MovementSimulationState): Boolean = state.onGround

		fun of(
			plan: TrajectoryPlan,
			rejoinable: (MovementSimulationState) -> Boolean = ::rejoinable,
		): PlanGraph? = of(plan.segments, plan.initialState, rejoinable)

		fun of(
			spine: List<PlanSegment>,
			initialState: MovementSimulationState,
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
					settled = segment.settledExit(MotionConstraints.DEFAULT_STOPPED_SPEED),
					rejoinable = rejoinable(segment.exit),
				)
			}
			return PlanGraph(junctions, spine)
		}
	}
}
