package com.lambda.pathing.coarse

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.graph.EdgeList

enum class SpeedClass {
	STOPPED, MOVING;

	companion object {

		private const val MOVING_THRESHOLD_BLOCKS_PER_TICK = 0.15

		fun of(speed: Double): SpeedClass =
			if (speed >= MOVING_THRESHOLD_BLOCKS_PER_TICK) MOVING else STOPPED
	}
}

internal object MomentumRules {

	private const val STARTUP_TICKS = 2.0

	private const val BRAKE_TICKS = 4.0

	private const val CHAIN_TAX_TICKS = 1.0

	private const val MIN_CHAINED_COST_FRACTION = 0.5

	private fun departsStoppedOnly(movement: MovementId): Boolean = movement == MovementId.CLIMB

	private fun arrival(movement: MovementId): SpeedClass =
		if (movement == MovementId.CLIMB || movement == MovementId.STEP_UP || movement == MovementId.LADDER_CATCH) SpeedClass.STOPPED
		else SpeedClass.MOVING

	private fun movingCost(ticks: Double): Double =
		(ticks - CHAIN_TAX_TICKS).coerceAtLeast(ticks * MIN_CHAINED_COST_FRACTION)

	private fun stoppedCost(movement: MovementId, ticks: Double): Double =
		if (departsStoppedOnly(movement)) ticks else ticks + STARTUP_TICKS

	fun successors(edges: CoarseEdgeCache, node: Long, out: EdgeList) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.MOVING) {
			out.add(PackedStance.withSpeed(node, SpeedClass.STOPPED), BRAKE_TICKS)
		}
		for (edge in edges.edgesFrom(node)) {
			if (speed == SpeedClass.MOVING && departsStoppedOnly(edge.movement)) continue
			if (speed == SpeedClass.STOPPED && !edge.standingStart) continue
			val cost = when (speed) {
				SpeedClass.MOVING -> movingCost(edge.lowerBoundTicks)
				SpeedClass.STOPPED -> stoppedCost(edge.movement, edge.lowerBoundTicks)
			}
			out.addMin(PackedStance.pack(edge.to, arrival(edge.movement)), cost)
		}
	}

	fun predecessors(edges: CoarseEdgeCache, node: Long, out: EdgeList) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.STOPPED) {
			out.add(PackedStance.withSpeed(node, SpeedClass.MOVING), BRAKE_TICKS)
		}
		for (edge in edges.edgesTo(node)) {
			if (arrival(edge.movement) != speed) continue
			if (!departsStoppedOnly(edge.movement)) {
				out.addMin(PackedStance.pack(edge.from, SpeedClass.MOVING), movingCost(edge.lowerBoundTicks))
			}
			if (edge.standingStart) {
				out.addMin(PackedStance.pack(edge.from, SpeedClass.STOPPED), stoppedCost(edge.movement, edge.lowerBoundTicks))
			}
		}
	}
}
