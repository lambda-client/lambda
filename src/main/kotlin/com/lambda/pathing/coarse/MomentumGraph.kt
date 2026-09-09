package com.lambda.pathing.coarse

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.graph.EdgeSink

enum class SpeedClass {
	STOPPED, MOVING;

	companion object {

		const val MOVING_THRESHOLD_BLOCKS_PER_TICK = 0.15

		fun of(speed: Double): SpeedClass =
			if (speed >= MOVING_THRESHOLD_BLOCKS_PER_TICK) MOVING else STOPPED
	}
}

internal object MomentumRules {

	const val STARTUP_TICKS = 2.0

	const val BRAKE_TICKS = 4.0

	const val CHAIN_TAX_TICKS = 1.0

	fun departsStoppedOnly(movement: MovementId): Boolean = movement == MovementId.CLIMB

	fun arrivesStopped(movement: MovementId): Boolean =
		movement == MovementId.CLIMB || movement == MovementId.STEP_UP ||

				movement == MovementId.LADDER_CATCH

	private fun movingCost(ticks: Double): Double =
		(ticks - CHAIN_TAX_TICKS).coerceAtLeast(ticks * 0.5)

	private fun stoppedCost(movement: MovementId, ticks: Double): Double =
		if (departsStoppedOnly(movement)) ticks else ticks + STARTUP_TICKS

	fun successors(edges: CoarseEdgeCache, node: Long, out: EdgeSink) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.MOVING) {
			out.add(PackedStance.withSpeed(node, SpeedClass.STOPPED), BRAKE_TICKS)
		}
		for ((_, _, to, movement, lowerBoundTicks, _, _, _, standingStart) in edges.edgesFrom(node)) {
			if (speed == SpeedClass.MOVING && departsStoppedOnly(movement)) continue
			if (speed == SpeedClass.STOPPED && !standingStart) continue
			val arrive = if (arrivesStopped(movement)) SpeedClass.STOPPED else SpeedClass.MOVING
			val cost = when (speed) {
				SpeedClass.MOVING -> movingCost(lowerBoundTicks)
				SpeedClass.STOPPED -> stoppedCost(movement, lowerBoundTicks)
			}
			out.addMin(PackedStance.pack(to, arrive), cost)
		}
	}

	fun predecessors(edges: CoarseEdgeCache, node: Long, out: EdgeSink) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.STOPPED) {
			out.add(PackedStance.withSpeed(node, SpeedClass.MOVING), BRAKE_TICKS)
		}
		for ((_, from, _, movement, lowerBoundTicks, _, _, _, standingStart) in edges.edgesTo(node)) {
			val arrive = if (arrivesStopped(movement)) SpeedClass.STOPPED else SpeedClass.MOVING
			if (arrive != speed) continue
			if (!departsStoppedOnly(movement)) {
				out.addMin(PackedStance.pack(from, SpeedClass.MOVING), movingCost(lowerBoundTicks))
			}
			if (standingStart) {
				out.addMin(PackedStance.pack(from, SpeedClass.STOPPED), stoppedCost(movement, lowerBoundTicks))
			}
		}
	}
}
